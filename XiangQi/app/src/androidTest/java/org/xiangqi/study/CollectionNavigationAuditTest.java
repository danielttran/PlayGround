package org.xiangqi.study;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Lifecycle and boundary cases beyond the initial collection smoke tests. */
public final class CollectionNavigationAuditTest extends InstrumentationTestCase {
    private Activity activity;
    private SharedPreferences study;
    private Map<String,?> original;

    @Override protected void setUp()throws Exception {
        super.setUp();Context context=getInstrumentation().getTargetContext();
        study=context.getSharedPreferences("study",0);original=study.getAll();study.edit().clear().commit();
        activity=getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->Boolean.TRUE.equals(get("loaded"))&&Boolean.TRUE.equals(get("library")),15000,"Initial library did not load");
        UiTestActions.awaitLibrarySearch(getInstrumentation(),activity);
    }

    @Override protected void tearDown()throws Exception {
        if(activity!=null&&!activity.isDestroyed())main(activity::finish);
        getInstrumentation().waitForIdleSync();
        Field worker=MainActivity.class.getDeclaredField("FILE_IO");worker.setAccessible(true);
        ((ExecutorService)worker.get(null)).submit(()->{}).get(15,TimeUnit.SECONDS);
        long until=SystemClock.uptimeMillis()+5000;
        while(activity!=null&&!activity.isDestroyed()&&SystemClock.uptimeMillis()<until)Thread.sleep(25);
        restore(study,original);super.tearDown();
    }

    public void testCollectionSearchSortAndScrollSurviveRecreationIndependently()throws Exception {
        main(()->{search().setText("Trenh");sort().setSelection(2);});getInstrumentation().waitForIdleSync();
        UiTestActions.awaitLibrarySearch(getInstrumentation(),activity);
        final int[] matches={0};main(()->{matches[0]=matches().size();assertTrue(matches[0]>3);assertEquals("oldest",get("librarySort"));});
        clickDescription("Engine games tab");
        main(()->{assertEquals("",search().getText().toString());search().setText("Unreleased engine collection");sort().setSelection(0);});getInstrumentation().waitForIdleSync();
        recreate();
        main(()->{assertEquals("engine",get("libraryTab"));assertEquals("Unreleased engine collection",search().getText().toString());assertEquals("collection",get("librarySort"));});
        clickDescription("Human games tab");
        main(()->{assertEquals("Trenh",search().getText().toString());assertEquals("oldest",get("librarySort"));assertEquals(matches[0],matches().size());list().setSelectionFromTop(3,-23);});
        await(()->list().getFirstVisiblePosition()>=3,5000,"Human collection did not reach its saved scroll fixture");
        final int[] geometry=new int[2];main(()->{geometry[0]=list().getFirstVisiblePosition();geometry[1]=list().getChildAt(0).getTop();});
        recreate();
        await(()->list().getFirstVisiblePosition()==geometry[0]&&list().getChildAt(0)!=null&&list().getChildAt(0).getTop()==geometry[1],5000,"Recreation lost collection scroll position");
        main(()->{assertEquals("human",get("libraryTab"));assertEquals("Trenh",get("search"));assertEquals("oldest",get("librarySort"));assertEquals(matches[0],matches().size());});
    }

    public void testReplayStopsAtLastMoveAndLibraryEntryCancelsTheNextTick()throws Exception {
        GameRepository.Game fixture=new GameRepository.Game();fixture.id="replay-boundary-audit";fixture.red="Replay audit Red";fixture.black="Replay audit Black";
        fixture.event="Replay boundary audit";fixture.date="2026-10-28";fixture.round="";fixture.sourceUrl="";fixture.startFen="";fixture.result="*";
        fixture.moves.addAll(Arrays.asList("b2e2","b9c7","a0a1"));
        main(()->{set("games",new ArrayList<>(Collections.singletonList(fixture)));call("openGame",new Class<?>[]{GameRepository.Game.class,boolean.class},fixture,false);study.edit().putLong("replayDelayMillis",500).commit();call("navigate",new Class<?>[]{int.class},2);call("toggleAutoplay");});
        await(()->((Integer)get("ply"))==3&&Boolean.FALSE.equals(get("autoplay")),3000,"Playback did not stop exactly at the last move");
        Thread.sleep(650);main(()->{assertEquals(3,((Integer)get("ply")).intValue());assertFalse((Boolean)get("autoplay"));call("toggleAutoplay");assertEquals("Play at the end restarts from the beginning",0,((Integer)get("ply")).intValue());call("showLibrary");});
        Thread.sleep(700);main(()->{assertTrue((Boolean)get("library"));assertFalse((Boolean)get("autoplay"));assertEquals("The library must cancel a pending replay tick",0,((Integer)get("ply")).intValue());call("returnToCurrentGame");assertEquals(0,((Integer)get("ply")).intValue());assertFalse("Returning must wait for the user's next Play command",(Boolean)get("autoplay"));});
    }

    public void testPastedDecimalCommaCannotSilentlyBecomeFiveSeconds()throws Exception {
        main(()->call("replaySpeed",new Class<?>[]{Runnable.class},(Object)null));getInstrumentation().waitForIdleSync();
        main(()->{
            AlertDialog dialog=(AlertDialog)get("replaySpeedDialog");assertNotNull(dialog);
            EditText input=(EditText)findDescription(dialog.getWindow().getDecorView(),"Seconds between replay moves");assertNotNull(input);
            input.getEditableText().replace(0,input.length(),"0,5");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals("A decimal comma must mean half a second rather than being stripped by the numeric field",500,study.getLong("replayDelayMillis",1000));
            assertFalse(dialog.isShowing());
        });
    }

    private void recreate()throws Exception {
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        main(activity::recreate);Activity replacement=monitor.waitForActivityWithTimeout(10000);getInstrumentation().removeMonitor(monitor);
        assertNotNull("Activity must recreate",replacement);activity=replacement;
        await(()->Boolean.TRUE.equals(get("loaded"))&&Boolean.TRUE.equals(get("library")),15000,"Recreated library did not load");UiTestActions.awaitLibrarySearch(getInstrumentation(),activity);
    }
    private EditText search(){return (EditText)findDescription(root(),"Library search");}
    private Spinner sort(){return (Spinner)findDescription(root(),"Game sort order");}
    private ListView list(){return (ListView)get("libraryList");}
    @SuppressWarnings("unchecked") private List<GameRepository.Game> matches(){return (List<GameRepository.Game>)get("libraryMatches");}
    private View root(){return activity.getWindow().getDecorView();}
    private void clickDescription(String description)throws Exception{main(()->{View view=findDescription(root(),description);assertNotNull(description,view);view.performClick();});UiTestActions.awaitLibrarySearch(getInstrumentation(),activity);}
    private void main(Runnable action){UiTestActions.runOnMain(getInstrumentation(),action);}
    private void await(BooleanSupplier condition,long timeout,String failure)throws Exception {long until=SystemClock.uptimeMillis()+timeout;boolean[] ready={false};while(SystemClock.uptimeMillis()<until){main(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(40);}fail(failure);}
    private Object get(String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}catch(Exception error){throw new RuntimeException(error);}}
    private void set(String name,Object value){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}catch(Exception error){throw new RuntimeException(error);}}
    private Object call(String name){return call(name,new Class<?>[0]);}
    private Object call(String name,Class<?>[] types,Object... values){try{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,values);}catch(Exception error){throw new RuntimeException(error);}}
    private static View findDescription(View view,String description){if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),description);if(found!=null)return found;}}return null;}
    private static void restore(SharedPreferences preferences,Map<String,?> values){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){Object value=item.getValue();String key=item.getKey();if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> set=(Set<String>)value;editor.putStringSet(key,set);}}editor.commit();}
}
