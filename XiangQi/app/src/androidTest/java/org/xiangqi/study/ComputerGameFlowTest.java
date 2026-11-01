package org.xiangqi.study;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.*;
import android.widget.*;
import org.xiangqi.study.core.Position;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Persistence, hidden-review boundaries and cancellation around real playing controls. */
public final class ComputerGameFlowTest extends InstrumentationTestCase {
    private HomeActivity activity;private ComputerGameStore store;private ComputerGameStore.SavedGame fixture;
    private SharedPreferences preferences;private Map<String,?> previous;private CountDownLatch unblock;
    @Override protected void setUp()throws Exception{
        super.setUp();dismissEmulatorBootDialog();Context c=getInstrumentation().getTargetContext();preferences=c.getSharedPreferences("home",0);previous=preferences.getAll();preferences.edit().clear().putString("screen","play").commit();store=ComputerGameStore.get(c);
        activity=(HomeActivity)getInstrumentation().startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->Boolean.TRUE.equals(get("ready")),20000,"Home did not load");fixture=new ComputerGameStore.SavedGame();fixture.id="played-audit-"+UUID.randomUUID();store.save(fixture);
    }
    @Override protected void tearDown()throws Exception{
        ComputerReview.pause();if(unblock!=null)unblock.countDown();if(activity!=null&&!activity.isDestroyed())main(activity::finish);getInstrumentation().waitForIdleSync();
        Field f=HomeActivity.class.getDeclaredField("IO");f.setAccessible(true);((ExecutorService)f.get(null)).submit(()->{}).get(15,TimeUnit.SECONDS);
        if(fixture!=null)store.getWritableDatabase().delete("games","id=?",new String[]{fixture.id});
        SharedPreferences.Editor e=preferences.edit().clear();for(Map.Entry<String,?> v:previous.entrySet()){Object x=v.getValue();if(x instanceof String)e.putString(v.getKey(),(String)x);else if(x instanceof Integer)e.putInt(v.getKey(),(Integer)x);else if(x instanceof Long)e.putLong(v.getKey(),(Long)x);else if(x instanceof Boolean)e.putBoolean(v.getKey(),(Boolean)x);}e.commit();super.tearDown();
    }
    public void testPersonalPersistenceReviewGateDeletionAndUndoRevision()throws Exception{
        fixture.moves.addAll(Arrays.asList("b2e2","b9c7"));store.save(fixture);
        assertEquals(fixture.position().toFen(),store.load(fixture.id).position().toFen());
        assertTrue(store.putAnalysis(fixture.id,0,0,100,"b2e2","b2e2 b9c7",12));assertTrue(store.putAnalysis(fixture.id,0,1,80,"b9c7","b9c7",12));assertTrue(store.putAnalysis(fixture.id,0,2,70,"h0g2","h0g2",12));
        try{store.review(fixture.id);fail("An unfinished game must never expose hidden notes");}catch(IllegalArgumentException expected){}
        fixture.result="0-1";fixture.reason="Surrender";fixture.updated++;store.save(fixture);GameRepository.Game review=store.review(fixture.id);assertEquals(2,review.moveComments.size());assertEquals("b2e2",review.moveBestMoves.get(0));
        store.setDeleted(fixture.id,true);assertNull(store.load(fixture.id));assertFalse(store.putAnalysis(fixture.id,0,0,0,"b2e2","",1));store.setDeleted(fixture.id,false);assertEquals(3,store.analyzed(fixture.id));
        fixture.result="*";fixture.moves.remove(1);fixture.revision++;fixture.updated++;store.save(fixture);assertNull(store.analysis(fixture.id,2));assertFalse("Old analysis cannot overwrite an undone game",store.putAnalysis(fixture.id,0,2,0,"a0a1","",1));
    }
    public void testUndoCancelsPendingComputerReplyAndRemovesOnlyHumanTurn()throws Exception{
        showFixture();unblock=new CountDownLatch(1);ExecutorService worker=(ExecutorService)get("work");worker.submit(()->{try{unblock.await();}catch(InterruptedException ignored){Thread.currentThread().interrupt();}});
        main(()->call("humanMove",new Class<?>[]{String.class},"b2e2"));assertTrue(Boolean.TRUE.equals(get("computing")));
        main(()->call("undoMove"));assertEquals(0,current().moves.size());assertEquals(1,current().revision);unblock.countDown();Thread.sleep(200);assertEquals("A cancelled reply must not arrive later",0,current().moves.size());
        await(()->{try{return store.load(fixture.id).revision==1;}catch(Exception e){return false;}},5000,"Undo not saved");
        main(()->{String visible=visible(activity.getWindow().getDecorView());assertFalse(visible.contains("Your turn"));assertFalse(visible.contains("Saved automatically"));assertTrue(visible.contains("Undo"));assertTrue(visible.contains("Surrender"));assertTrue(visible.contains("Hint"));});
    }
    public void testHintUsesMaximumTwoSecondBudgetAndDoesNotRevealScores()throws Exception{
        PikafishEngine.Options hint=EngineSettings.timed(activity,2000,1);assertEquals(128,hint.maxDepth);assertEquals(2000L,hint.millis);assertEquals(0L,hint.maxNodes);
        showFixture();long started=SystemClock.elapsedRealtime();main(()->call("hint"));await(()->!Boolean.TRUE.equals(get("hinting")),10000,"Hint did not finish");
        String move=(String)get("hintMove");assertTrue(Position.initial().legalMoves().contains(move));assertTrue(current().hints.contains(0));assertEquals(0,current().moves.size());assertTrue("Hint should return promptly",SystemClock.elapsedRealtime()-started<8000);
        main(()->{String visible=visible(activity.getWindow().getDecorView());assertFalse(visible.contains("Best move"));assertFalse(visible.contains("Evaluation"));});
    }
    public void testSurrenderSavesResultWithoutOpeningReview()throws Exception{
        showFixture();main(()->call("surrender"));main(()->((AlertDialog)get("dialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        await(()->{try{return store.load(fixture.id).finished();}catch(Exception e){return false;}},5000,"Finished result not saved");assertEquals("0-1",store.load(fixture.id).result);assertEquals(Boolean.FALSE,get("playing"));
        main(()->{String visible=visible(activity.getWindow().getDecorView());assertTrue(visible.contains("Game finished"));assertTrue(visible.contains("My Games"));assertFalse(visible.contains("Best move"));});
    }
    public void testHundredLevelsAndFixedAnalysisStrength()throws Exception{
        long prior=0;for(int level=1;level<=100;level++){assertTrue(ComputerDifficulty.nodes(level)>prior);prior=ComputerDifficulty.nodes(level);assertTrue(ComputerDifficulty.depth(level)>=1);}
        Context c=getInstrumentation().getTargetContext();SharedPreferences p=c.getSharedPreferences("engine-settings",0);Map<String,?> before=p.getAll();p.edit().putInt("depth",1).putLong("millis",50).putInt("threads",1).commit();
        try{PikafishEngine.Options maximum=EngineSettings.load(c);assertEquals(128,maximum.maxDepth);assertEquals(0L,maximum.millis);assertEquals(Math.max(1,Runtime.getRuntime().availableProcessors()),maximum.threads);}finally{SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> v:before.entrySet()){if(v.getValue() instanceof Integer)e.putInt(v.getKey(),(Integer)v.getValue());else if(v.getValue() instanceof Long)e.putLong(v.getKey(),(Long)v.getValue());}e.commit();}
    }
    public void testHiddenReviewContinuesAfterPreviouslyCachedGameGrows()throws Exception{
        fixture.moves.addAll(Arrays.asList("b2e2","b9c7"));store.save(fixture);for(int i=0;i<=2;i++)store.putAnalysis(fixture.id,0,i,0,"","",8);
        showFixture();main(()->call("humanMove",new Class<?>[]{String.class},"h0g2"));await(()->current().moves.size()>=4,8000,"Computer reply did not arrive");
        await(()->store.analysis(fixture.id,4)!=null,15000,"Hidden review missed the appended position");
        try{store.review(fixture.id);fail("Background review must remain hidden during play");}catch(IllegalArgumentException expected){}
    }
    public void testScreensAndResumeAfterRecreation()throws Exception{
        showFixture();screenshot("play");main(()->call("showScreen",new Class<?>[]{String.class},"options"));
        main(()->{SeekBar slider=findSlider(activity.getWindow().getDecorView());assertNotNull(slider);slider.setProgress(99);assertEquals(100,preferences.getInt("level",0));assertEquals("Changing the default must not change an existing game",40,current().level);});screenshot("options");
        main(()->call("showScreen",new Class<?>[]{String.class},"database"));await(()->{try{return ((android.widget.BaseAdapter)get("adapter")).getCount()>0;}catch(Exception e){return false;}},6000,"Personal database did not show saved game");screenshot("database");
        main(()->{set("playing",true);call("showScreen",new Class<?>[]{String.class},"play");});
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(HomeActivity.class.getName(),null,false);main(activity::recreate);
        android.app.Activity replacement=monitor.waitForActivityWithTimeout(15000);getInstrumentation().removeMonitor(monitor);assertNotNull("Home did not recreate",replacement);activity=(HomeActivity)replacement;
        await(()->Boolean.TRUE.equals(get("ready")),20000,"Recreated app did not load");assertEquals(fixture.id,current().id);assertEquals(0,current().moves.size());assertEquals(Boolean.TRUE,get("playing"));
    }
    private void screenshot(String name)throws Exception{
        getInstrumentation().waitForIdleSync();CountDownLatch drawn=new CountDownLatch(1);main(()->activity.getWindow().getDecorView().postOnAnimation(()->activity.getWindow().getDecorView().postOnAnimation(drawn::countDown)));assertTrue("Screen did not draw",drawn.await(5,TimeUnit.SECONDS));
        Thread.sleep(300);android.view.accessibility.AccessibilityNodeInfo active=getInstrumentation().getUiAutomation().getRootInActiveWindow();assertNotNull(active);assertEquals("A system overlay invalidates a UI screenshot","org.xiangqi.study",String.valueOf(active.getPackageName()));
        android.graphics.Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        java.io.File file=new java.io.File(activity.getExternalFilesDir(null),"audit-"+name+".png");try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    }
    private void dismissEmulatorBootDialog()throws Exception{
        android.view.accessibility.AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root==null)return;
        if(!root.findAccessibilityNodeInfosByText("System UI isn't responding").isEmpty())for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Wait")){node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);Thread.sleep(300);break;}
    }
    private static SeekBar findSlider(View v){if(v instanceof SeekBar)return (SeekBar)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){SeekBar found=findSlider(g.getChildAt(i));if(found!=null)return found;}}return null;}
    private void showFixture()throws Exception{main(()->{set("current",fixture);set("playing",true);set("setup",false);call("showScreen",new Class<?>[]{String.class},"play");});}
    private ComputerGameStore.SavedGame current(){return (ComputerGameStore.SavedGame)get("current");}
    private Object get(String name){try{Field f=HomeActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new AssertionError(e);}}
    private void set(String name,Object value){try{Field f=HomeActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}catch(Exception e){throw new AssertionError(e);}}
    private void call(String name){call(name,new Class<?>[0]);}
    private void call(String name,Class<?>[] types,Object... args){try{Method m=HomeActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}catch(Exception e){throw new AssertionError(e);}}
    private void main(Runnable r){getInstrumentation().runOnMainSync(r);getInstrumentation().waitForIdleSync();}
    private void await(BooleanSupplier check,long millis,String message)throws Exception{long until=SystemClock.uptimeMillis()+millis;while(SystemClock.uptimeMillis()<until){if(check.getAsBoolean())return;Thread.sleep(40);}fail(message);}
    private static String visible(View v){if(v.getVisibility()!=View.VISIBLE)return "";StringBuilder s=new StringBuilder();if(v instanceof TextView)s.append(((TextView)v).getText()).append(' ');if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)s.append(visible(g.getChildAt(i)));}return s.toString();}
}
