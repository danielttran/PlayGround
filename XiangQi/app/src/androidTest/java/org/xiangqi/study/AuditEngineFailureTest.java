package org.xiangqi.study;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.xiangqi.study.core.Engine;

/** A real failed opponent worker must report its error outside the conditional analysis panel. */
public final class AuditEngineFailureTest extends InstrumentationTestCase {
    private Activity activity;
    private SharedPreferences study,engine;
    private Map<String,?> oldStudy,oldEngine;
    private File directory,binary;
    private String nativeDirectory;

    @Override protected void setUp()throws Exception {
        super.setUp();Context target=getInstrumentation().getTargetContext();
        study=target.getSharedPreferences("study",0);engine=target.getSharedPreferences("engine-settings",0);
        oldStudy=study.getAll();oldEngine=engine.getAll();study.edit().clear().commit();
        engine.edit().putInt("depth",1).putLong("millis",500).putInt("threads",1).putInt("hashMb",32).putInt("lines",1).commit();
        activity=getInstrumentation().startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->Boolean.TRUE.equals(get("loaded")),15000,"Library did not load");
        main(()->{
            nativeDirectory=activity.getApplicationInfo().nativeLibraryDir;
            assertFalse("This failure fixture belongs on the portable emulator build",PikafishEngine.available(activity));
            call("openGame",new Class<?>[]{GameRepository.Game.class,boolean.class},games().get(0),false);
            call("toggleExplore");call("makeMove",new Class<?>[]{String.class},"b2e2");
        });getInstrumentation().waitForIdleSync();
        directory=new File(target.getCacheDir(),"opponent-failure-"+UUID.randomUUID());assertTrue(directory.mkdir());
        binary=new File(directory,"libpikafish.so");assertTrue(binary.createNewFile());
    }

    @Override protected void tearDown()throws Exception {
        if(activity!=null&&!activity.isDestroyed())main(()->{if(nativeDirectory!=null)activity.getApplicationInfo().nativeLibraryDir=nativeDirectory;activity.finish();});
        getInstrumentation().waitForIdleSync();long until=SystemClock.uptimeMillis()+5000;
        while(activity!=null&&!activity.isDestroyed()&&SystemClock.uptimeMillis()<until)Thread.sleep(25);
        Field io=MainActivity.class.getDeclaredField("FILE_IO");io.setAccessible(true);((ExecutorService)io.get(null)).submit(()->{}).get(15,TimeUnit.SECONDS);
        restore(study,oldStudy);restore(engine,oldEngine);
        if(binary!=null&&binary.exists())assertTrue(binary.delete());if(directory!=null&&directory.exists())assertTrue(directory.delete());
        super.tearDown();
    }

    public void testFailedOpponentIsVisibleWithAnalysisOffAndKeepsVariation()throws Exception {failedOpponent(false);}
    public void testFailedOpponentIsVisibleWithAnalysisOnAndResumesStudy()throws Exception {failedOpponent(true);}

    private void failedOpponent(boolean enabled)throws Exception {
        ExecutorService executor=(ExecutorService)get("engine");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        executor.submit(()->{entered.countDown();try{release.await(15,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
        assertTrue("The executor blocker must start",entered.await(3,TimeUnit.SECONDS));
        final List<String> source=new ArrayList<>(),variation=new ArrayList<>();
        try {
            main(()->{
                GameRepository.Game game=(GameRepository.Game)get("game");source.addAll(game.moves);variation.addAll(moves());
                if(enabled)call("analyze");
                try {
                    // Capture the native route, then restore availability before its queued worker runs.
                    // The real adapter guard fails; no fake executable or network is ever opened.
                    activity.getApplicationInfo().nativeLibraryDir=directory.getAbsolutePath();assertTrue(PikafishEngine.available(activity));
                    call("startEngineGame",new Class<?>[]{boolean.class},true);
                    assertEquals(Boolean.TRUE,get("vsEngine"));assertEquals(Boolean.TRUE,get("engineThinking"));assertNotNull(get("playFuture"));
                } finally {activity.getApplicationInfo().nativeLibraryDir=nativeDirectory;}
            });
        } finally {release.countDown();}
        await(()->Boolean.FALSE.equals(get("vsEngine"))&&get("playFuture")==null,10000,"Failed opponent did not release play state");
        main(()->{
            assertEquals(Boolean.FALSE,get("engineThinking"));assertEquals(Boolean.TRUE,get("exploring"));assertEquals(1,get("ply"));
            assertEquals(variation,moves());assertEquals(source,((GameRepository.Game)get("game")).moves);
            assertEquals(enabled,get("analysisEnabled"));assertEquals(enabled,study.getBoolean("analysisEnabled",false));
            if(!enabled){assertNull(get("analysisFuture"));assertEquals(View.GONE,((View)get("analysisBox")).getVisibility());}
        });
        long until=SystemClock.uptimeMillis()+3000;boolean visible=false;
        while(SystemClock.uptimeMillis()<until){if(contains(getInstrumentation().getUiAutomation().getRootInActiveWindow(),"Pikafish requires Android")){visible=true;break;}Thread.sleep(40);}
        assertTrue("A failed opponent must show its error independently of the analysis panel",visible);
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);getInstrumentation().waitForIdleSync();
        if(enabled)await(()->get("analysisFuture")==null&&get("lastResult") instanceof Engine.Result&&((Engine.Result)get("lastResult")).depth>0,10000,"Enabled study analysis did not resume after opponent failure");
    }

    private static boolean contains(AccessibilityNodeInfo node,String value){if(node==null)return false;if(node.isVisibleToUser()&&node.getText()!=null&&node.getText().toString().contains(value))return true;for(int i=0;i<node.getChildCount();i++)if(contains(node.getChild(i),value))return true;return false;}
    @SuppressWarnings("unchecked") private List<GameRepository.Game> games(){return (List<GameRepository.Game>)get("games");}
    @SuppressWarnings("unchecked") private List<String> moves(){return (List<String>)get("workingMoves");}
    private void main(Runnable action){UiTestActions.runOnMain(getInstrumentation(),action);}
    private void await(BooleanSupplier condition,long timeout,String message)throws Exception {long until=SystemClock.uptimeMillis()+timeout;boolean[] ready={false};while(SystemClock.uptimeMillis()<until){main(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(40);}fail(message);}
    private Object get(String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}catch(Exception error){throw new RuntimeException(error);}}
    private Object call(String name){return call(name,new Class<?>[0]);}
    private Object call(String name,Class<?>[] types,Object... values){try{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,values);}catch(Exception error){throw new RuntimeException(error);}}
    private static void restore(SharedPreferences preferences,Map<String,?> values){SharedPreferences.Editor edit=preferences.edit().clear();for(Map.Entry<String,?> entry:values.entrySet()){String key=entry.getKey();Object value=entry.getValue();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> set=(Set<String>)value;edit.putStringSet(key,set);}}edit.commit();}
}
