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
    private SharedPreferences studyPreferences;private Map<String,?> previousStudy;
    @Override protected void setUp()throws Exception{
        super.setUp();dismissEmulatorBootDialog();Context c=getInstrumentation().getTargetContext();studyPreferences=c.getSharedPreferences("study",0);previousStudy=studyPreferences.getAll();preferences=c.getSharedPreferences("home",0);previous=preferences.getAll();preferences.edit().clear().putString("screen","play").commit();store=ComputerGameStore.get(c);store.getWritableDatabase().delete("games","id LIKE ?",new String[]{"played-audit-%"});
        activity=(HomeActivity)getInstrumentation().startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->Boolean.TRUE.equals(get("ready")),20000,"Home did not load");fixture=new ComputerGameStore.SavedGame();fixture.id="played-audit-"+UUID.randomUUID();store.save(fixture);
    }
    @Override protected void tearDown()throws Exception{
        ComputerReview.pause();if(unblock!=null)unblock.countDown();if(activity!=null&&!activity.isDestroyed())main(activity::finish);getInstrumentation().waitForIdleSync();
        Field f=HomeActivity.class.getDeclaredField("IO");f.setAccessible(true);((ExecutorService)f.get(null)).submit(()->{}).get(15,TimeUnit.SECONDS);
        if(fixture!=null)store.getWritableDatabase().delete("games","id=?",new String[]{fixture.id});
        restorePreferences(preferences,previous);restorePreferences(studyPreferences,previousStudy);super.tearDown();
    }
    public void testPersonalPersistenceReviewGateDeletionAndUndoRevision()throws Exception{
        fixture.moves.addAll(Arrays.asList("b2e2","b9c7"));store.save(fixture);
        assertEquals(fixture.position().toFen(),store.load(fixture.id).position().toFen());
        assertTrue(store.putAnalysis(fixture.id,0,0,100,"b2e2","b2e2 b9c7",12));assertTrue(store.putAnalysis(fixture.id,0,1,80,"b9c7","b9c7",12));assertTrue(store.putAnalysis(fixture.id,0,2,70,"h0g2","h0g2",12));
        try{store.review(fixture.id);fail("An unfinished game must never expose hidden notes");}catch(IllegalArgumentException expected){}
        fixture.result="0-1";fixture.reason="Surrender";fixture.updated++;store.save(fixture);GameRepository.Game review=store.review(fixture.id);assertEquals(2,review.moveComments.size());assertEquals("b2e2",review.moveBestMoves.get(1));assertEquals("b9c7",review.moveBestMoves.get(2));assertNull(review.moveBestMoves.get(0));
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
        screenshot("options");main(()->{call("difficulty");AlertDialog sheet=(AlertDialog)get("dialog");SeekBar slider=findSlider(sheet.getWindow().getDecorView());assertNotNull(slider);slider.setProgress(99);assertEquals(100,preferences.getInt("level",0));assertEquals("Changing the default must not change an existing game",40,current().level);sheet.dismiss();});
        main(()->call("showScreen",new Class<?>[]{String.class},"database"));await(()->{try{return ((android.widget.BaseAdapter)get("adapter")).getCount()>0;}catch(Exception e){return false;}},6000,"Personal database did not show saved game");screenshot("database");
        main(()->{set("playing",true);call("showScreen",new Class<?>[]{String.class},"play");});
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(HomeActivity.class.getName(),null,false);main(activity::recreate);
        android.app.Activity replacement=monitor.waitForActivityWithTimeout(15000);getInstrumentation().removeMonitor(monitor);assertNotNull("Home did not recreate",replacement);activity=(HomeActivity)replacement;
        await(()->Boolean.TRUE.equals(get("ready")),20000,"Recreated app did not load");assertEquals(fixture.id,current().id);assertEquals(0,current().moves.size());assertEquals(Boolean.TRUE,get("playing"));
    }
    public void testCollectionPreferencesDoNotDependOnLastImport()throws Exception{
        main(()->{set("collection","human");set("importId","batch-a");set("query","Lai");set("order","oldest");call("saveScope");
            set("collection","imports");set("importId","batch-b");set("query","Engine");set("order","newest");call("saveScope");
            set("collection","human");call("loadScope");assertEquals("Human search must survive selecting another import","Lai",get("query"));assertEquals("oldest",get("order"));});
    }
    public void testNewGameSidePreviewAndSpacingSurviveRecreation()throws Exception{
        main(()->{set("setup",true);call("showScreen",new Class<?>[]{String.class},"play");});
        main(()->{View black=findDescription(activity.getWindow().getDecorView(),"Play as Black");assertNotNull(black);black.performClick();assertEquals(Boolean.TRUE,get("setupFlip"));assertEquals(Boolean.TRUE,field(get("setupBoard"),"flipped"));
            findDescription(activity.getWindow().getDecorView(),"Play as Random").performClick();assertEquals("Random must keep the current preview orientation",Boolean.TRUE,get("setupFlip"));
            View red=findDescription(activity.getWindow().getDecorView(),"Play as Red");red.performClick();assertEquals(Boolean.FALSE,field(get("setupBoard"),"flipped"));
            findDescription(activity.getWindow().getDecorView(),"Play as Random").performClick();assertEquals(Boolean.FALSE,get("setupFlip"));
            findDescription(activity.getWindow().getDecorView(),"Play as Black").performClick();findDescription(activity.getWindow().getDecorView(),"Play as Random").performClick();
        });getInstrumentation().waitForIdleSync();
        main(()->{View red=findDescription(activity.getWindow().getDecorView(),"Play as Red"),black=findDescription(activity.getWindow().getDecorView(),"Play as Black"),random=findDescription(activity.getWindow().getDecorView(),"Play as Random");assertTrue("Red and Black controls must have a visible gap",bounds(black).left-bounds(red).right>=6);assertTrue(bounds(random).left-bounds(black).right>=6);});
        screenshot("new-game");recreateHome();main(()->{assertEquals("Random",get("side"));assertEquals(Boolean.TRUE,get("setupFlip"));assertEquals(Boolean.TRUE,field(get("setupBoard"),"flipped"));});
    }
    public void testSavedReviewSuggestionsMatchEachPlayedTurnAndExploreCorrectPosition()throws Exception{
        fixture.moves.addAll(Arrays.asList("b2e2","b9c7"));fixture.result="0-1";store.save(fixture);
        store.putAnalysis(fixture.id,0,0,25,"h2e2","h2e2 h9g7",18);store.putAnalysis(fixture.id,0,1,10,"h9g7","h9g7 b0c2",18);store.putAnalysis(fixture.id,0,2,5,"b0c2","b0c2",18);
        GameRepository.Game data=store.review(fixture.id);assertEquals("h2e2",data.moveBestMoves.get(1));assertEquals("h9g7",data.moveBestMoves.get(2));assertNull(data.moveBestMoves.get(0));
        studyPreferences.edit().putBoolean("analysisEnabled",false).commit();MainActivity viewer=(MainActivity)getInstrumentation().startActivitySync(new Intent(activity,MainActivity.class).putExtra(HomeActivity.FROM_HOME,true).putExtra(HomeActivity.REVIEW_ID,fixture.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{await(()->Boolean.TRUE.equals(field(viewer,"loaded")),20000,"Review did not open");main(()->{
            ListView record=(ListView)field(viewer,"movesView");assertEquals(3,record.getCount());View first=record.getAdapter().getView(0,null,record);assertNotNull(findText(first,"b2–e2"));View alternative=findText(first,"h2–e2");assertNotNull("The same row must show the pre-move suggestion",alternative);alternative.performClick();
            assertEquals(Boolean.TRUE,field(viewer,"exploring"));assertEquals(1,field(viewer,"ply"));@SuppressWarnings("unchecked") List<Position> positions=(List<Position>)field(viewer,"positions");assertEquals(Position.initial().play("h2e2").toFen(),positions.get(1).toFen());
            invoke(viewer,"toggleExplore");assertEquals(Boolean.FALSE,field(viewer,"exploring"));@SuppressWarnings("unchecked") List<Position> mainline=(List<Position>)field(viewer,"positions");assertEquals(Position.initial().play("b2e2").toFen(),mainline.get(1).toFen());
            View second=record.getAdapter().getView(1,null,record);assertNotNull(findText(second,"b9–c7"));assertNotNull("Black's advice must stay on Black's row",findText(second,"h9–g7"));
        });capture(viewer,"review");}finally{main(viewer::finish);}
    }
    public void testReviewRefreshDoesNotPullScrollAwayFromResult()throws Exception{
        studyPreferences.edit().putBoolean("analysisEnabled",false).commit();String source=((GameRepository.Game)((List<?>)get("sources")).get(0)).id;
        MainActivity viewer=(MainActivity)getInstrumentation().startActivitySync(new Intent(activity,MainActivity.class).putExtra(HomeActivity.FROM_HOME,true).putExtra(HomeActivity.SOURCE_ID,source).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{await(()->Boolean.TRUE.equals(field(viewer,"loaded")),20000,"Source viewer did not open");main(()->{ListView record=(ListView)field(viewer,"movesView");record.setSelection(record.getCount()-1);});getInstrumentation().waitForIdleSync();
            final int[] before={0};final android.graphics.Rect[] board={null};main(()->{ListView record=(ListView)field(viewer,"movesView");before[0]=record.getFirstVisiblePosition();board[0]=bounds((View)field(viewer,"board"));invoke(viewer,"updateReplay");});getInstrumentation().waitForIdleSync();
            main(()->{assertEquals("A notes refresh must preserve manual record scrolling",before[0],((ListView)field(viewer,"movesView")).getFirstVisiblePosition());assertEquals(board[0],bounds((View)field(viewer,"board")));});
        }finally{main(viewer::finish);}
    }
    public void testViewerOptionsReturnPreservesPositionAndAnalysisToggle()throws Exception{
        studyPreferences.edit().putBoolean("analysisEnabled",false).commit();String source=((GameRepository.Game)((List<?>)get("sources")).get(0)).id;
        MainActivity viewer=(MainActivity)getInstrumentation().startActivitySync(new Intent(activity,MainActivity.class).putExtra(HomeActivity.FROM_HOME,true).putExtra(HomeActivity.SOURCE_ID,source).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));HomeActivity options=null;
        try{await(()->Boolean.TRUE.equals(field(viewer,"loaded")),20000,"Viewer did not load");android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(HomeActivity.class.getName(),null,false);
            main(()->{try{Field enabled=MainActivity.class.getDeclaredField("analysisEnabled");enabled.setAccessible(true);enabled.setBoolean(viewer,true);}catch(Exception e){throw new AssertionError(e);}invoke(viewer,"settings");});
            options=(HomeActivity)monitor.waitForActivityWithTimeout(15000);getInstrumentation().removeMonitor(monitor);assertNotNull(options);final HomeActivity shown=options;await(()->Boolean.TRUE.equals(field(shown,"ready")),20000,"Options did not load");
            main(()->{assertEquals(Boolean.TRUE,field(shown,"standaloneOptions"));assertEquals("play",preferences.getString("screen",""));});
            android.app.Instrumentation.ActivityMonitor rotation=getInstrumentation().addMonitor(HomeActivity.class.getName(),null,false);main(shown::recreate);options=(HomeActivity)rotation.waitForActivityWithTimeout(15000);getInstrumentation().removeMonitor(rotation);assertNotNull(options);final HomeActivity rotated=options;await(()->Boolean.TRUE.equals(field(rotated,"ready")),20000,"Rotated Options did not load");
            main(()->{assertEquals("options",field(rotated,"screen"));assertEquals("play",preferences.getString("screen",""));StudySettings.selectBoardPreset(rotated,"moss");rotated.finish();});await(()->Boolean.TRUE.equals(field(viewer,"foreground")),10000,"Viewer did not resume");
            main(()->{assertEquals(source,((GameRepository.Game)field(viewer,"game")).id);assertEquals(Boolean.TRUE,field(viewer,"analysisEnabled"));assertEquals(org.xiangqi.study.core.AppearancePresets.board("moss").background,field(field(viewer,"board"),"boardColor"));invoke(viewer,"stopSearch");});
        }finally{if(options!=null&&!options.isDestroyed()){final HomeActivity remaining=options;main(remaining::finish);}main(viewer::finish);}
    }
    private static android.graphics.Rect bounds(View view){int[] at=new int[2];view.getLocationOnScreen(at);return new android.graphics.Rect(at[0],at[1],at[0]+view.getWidth(),at[1]+view.getHeight());}
    private static Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(e);}}
    private static void invoke(Object target,String name){try{Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(target);}catch(Exception e){throw new AssertionError(e);}}
    public void testPendingSearchSurvivesLeavingDatabase()throws Exception{
        main(()->{set("collection","human");call("loadScope");call("showScreen",new Class<?>[]{String.class},"database");
            EditText search=(EditText)findDescription(activity.getWindow().getDecorView(),"Database search");assertNotNull(search);search.setText("Lai");
            call("showScreen",new Class<?>[]{String.class},"options");call("loadScope");assertEquals("A pending debounce must not lose the last typed search","Lai",get("query"));});
    }
    public void testFinishedScreenSurvivesRecreation()throws Exception{
        fixture.result="0-1";fixture.reason="Surrender";store.save(fixture);
        main(()->{set("current",fixture);set("resultScreen",true);set("playing",false);call("showScreen",new Class<?>[]{String.class},"play");});
        recreateHome();assertEquals(Boolean.TRUE,get("resultScreen"));assertNotNull(current());assertEquals(fixture.id,current().id);assertTrue(current().finished());
        main(()->assertTrue(visible(activity.getWindow().getDecorView()).contains("Game finished")));
    }
    public void testDeletingReviewedGameInvalidatesReturnButtonAndUndoRestoresIt()throws Exception{
        fixture.result="0-1";store.save(fixture);SharedPreferences study=activity.getSharedPreferences("study",0);String old=study.getString("lastGame",null);study.edit().putString("lastGame",fixture.id).commit();
        try{
            main(()->{set("personal",new ArrayList<>(Collections.singletonList(fixture)));set("collection","personal");call("showScreen",new Class<?>[]{String.class},"database");assertNotNull(findDescription(activity.getWindow().getDecorView(),"Return to current game"));call("delete",new Class<?>[]{String.class},fixture.id);});
            await(()->fixture.id.equals(get("deletedId")),5000,"Delete did not complete");
            main(()->{assertNull("Deleted records must not remain the return target",findDescription(activity.getWindow().getDecorView(),"Return to current game"));View undo=findText(activity.getWindow().getDecorView(),"Undo");assertNotNull(undo);undo.performClick();});
            await(()->"".equals(get("deletedId")),5000,"Undo did not complete");assertNotNull(store.load(fixture.id));
            main(()->assertNotNull("Restored records must again be valid return targets",findDescription(activity.getWindow().getDecorView(),"Return to current game")));
        }finally{SharedPreferences.Editor e=study.edit();if(old==null)e.remove("lastGame");else e.putString("lastGame",old);e.commit();}
    }
    public void testMalformedStartingFenShowsErrorInsteadOfCrashing()throws Exception{
        Context c=getInstrumentation().getTargetContext();activity=(HomeActivity)getInstrumentation().startActivitySync(new Intent(c,HomeActivity.class).putExtra(HomeActivity.START_FEN,"9/9").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->Boolean.TRUE.equals(get("ready")),20000,"Invalid-position launch did not recover");assertEquals(Boolean.FALSE,get("playing"));assertEquals(Boolean.FALSE,get("setup"));
        main(()->{AlertDialog error=(AlertDialog)get("dialog");assertNotNull(error);assertTrue(error.isShowing());assertTrue(visible(error.getWindow().getDecorView()).contains("Could not open position"));error.dismiss();});
    }
    public void testSourcePositionLaunchIsNotReappliedOnRotation()throws Exception{
        String fen=Position.initial().play("b2e2").toFen();fixture.startFen=fen;fixture.humanRed=false;store.save(fixture);Context c=getInstrumentation().getTargetContext();
        activity=(HomeActivity)getInstrumentation().startActivitySync(new Intent(c,HomeActivity.class).putExtra(HomeActivity.START_FEN,fen).putExtra(HomeActivity.SOURCE_TITLE,"Audit source position").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->Boolean.TRUE.equals(get("ready")),20000,"Source setup did not load");assertEquals(Boolean.TRUE,get("setup"));showFixture();recreateHome();
        assertEquals(Boolean.TRUE,get("playing"));assertEquals("Rotation must resume rather than reopen setup",Boolean.FALSE,get("setup"));assertEquals(fixture.id,current().id);assertEquals(fen,current().startFen);
    }
    public void testImportDestinationSurvivesReturnFromViewerAndSourceReload()throws Exception{
        main(()->{set("collection","human");call("loadScope");call("showScreen",new Class<?>[]{String.class},"database");});
        studyPreferences.edit().putBoolean("analysisEnabled",false).commit();String id=((GameRepository.Game)((List<?>)get("sources")).get(0)).id;
        Activity viewer=getInstrumentation().startActivitySync(new Intent(activity,MainActivity.class).putExtra(HomeActivity.FROM_HOME,true).putExtra(HomeActivity.SOURCE_ID,id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            Field loaded=MainActivity.class.getDeclaredField("loaded");loaded.setAccessible(true);await(()->{try{return loaded.getBoolean(viewer);}catch(Exception e){throw new AssertionError(e);}},20000,"Viewer did not load");
            main(()->{set("importVersion",-1L);preferences.edit().putString("collection","imports").putString("importId","audit-new-import").commit();viewer.finish();});
            await(()->Boolean.TRUE.equals(get("foreground")),10000,"Home did not return to foreground");
            main(()->{assertEquals("imports",get("collection"));assertEquals("audit-new-import",get("importId"));assertEquals("Returning must not overwrite the importer's destination","imports",preferences.getString("collection",""));});
        }finally{if(!viewer.isDestroyed())main(viewer::finish);}
    }
    public void testDetachedDatabaseWidgetsCannotChangeAnotherScreen()throws Exception{
        main(()->{set("collection","human");call("loadScope");call("showScreen",new Class<?>[]{String.class},"database");
            EditText staleSearch=(EditText)findDescription(activity.getWindow().getDecorView(),"Database search");Spinner staleSort=(Spinner)findDescription(activity.getWindow().getDecorView(),"Database sort order");assertNotNull(staleSearch);assertNotNull(staleSort);
            android.widget.AdapterView.OnItemSelectedListener selection=staleSort.getOnItemSelectedListener();call("showScreen",new Class<?>[]{String.class},"options");set("query","current query");set("order","newest");
            staleSearch.setText("stale query");selection.onItemSelected(staleSort,null,1,0);assertEquals("current query",get("query"));assertEquals("newest",get("order"));assertEquals("options",get("screen"));
        });
    }
    public void testLegacyPinnedLauncherStillReachesHome()throws Exception{
        Context c=getInstrumentation().getTargetContext();assertTrue("Older pinned shortcuts must remain launchable",c.getPackageManager().getActivityInfo(new ComponentName(c,MainActivity.class),0).exported);
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(HomeActivity.class.getName(),null,false);
        Activity legacy=getInstrumentation().startActivitySync(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClass(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Activity replacement=monitor.waitForActivityWithTimeout(15000);getInstrumentation().removeMonitor(monitor);assertNotNull("Legacy shortcut did not reach Play / Database / Options",replacement);activity=(HomeActivity)replacement;
        await(()->Boolean.TRUE.equals(get("ready")),20000,"Home did not load after legacy shortcut");assertTrue(legacy.isFinishing()||legacy.isDestroyed());
    }
    @SuppressWarnings("unchecked") private static void restorePreferences(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> v:values.entrySet()){Object x=v.getValue();if(x instanceof String)e.putString(v.getKey(),(String)x);else if(x instanceof Integer)e.putInt(v.getKey(),(Integer)x);else if(x instanceof Long)e.putLong(v.getKey(),(Long)x);else if(x instanceof Boolean)e.putBoolean(v.getKey(),(Boolean)x);else if(x instanceof Float)e.putFloat(v.getKey(),(Float)x);else if(x instanceof Set)e.putStringSet(v.getKey(),new HashSet<>((Set<String>)x));}e.commit();}
    private void recreateHome()throws Exception{
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(HomeActivity.class.getName(),null,false);main(activity::recreate);
        android.app.Activity replacement=monitor.waitForActivityWithTimeout(15000);getInstrumentation().removeMonitor(monitor);assertNotNull(replacement);activity=(HomeActivity)replacement;await(()->Boolean.TRUE.equals(get("ready")),20000,"Recreated Home did not load");
    }
    private static View findDescription(View v,String description){if(description.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=findDescription(g.getChildAt(i),description);if(found!=null)return found;}}return null;}
    private static View findText(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=findText(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private void screenshot(String name)throws Exception{
        capture(activity,name);
    }
    private void capture(Activity target,String name)throws Exception{
        getInstrumentation().waitForIdleSync();CountDownLatch drawn=new CountDownLatch(1);main(()->target.getWindow().getDecorView().postOnAnimation(()->target.getWindow().getDecorView().postOnAnimation(drawn::countDown)));assertTrue("Screen did not draw",drawn.await(5,TimeUnit.SECONDS));
        Thread.sleep(300);android.view.accessibility.AccessibilityNodeInfo active=getInstrumentation().getUiAutomation().getRootInActiveWindow();assertNotNull(active);assertEquals("A system overlay invalidates a UI screenshot","org.xiangqi.study",String.valueOf(active.getPackageName()));
        android.graphics.Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        java.io.File file=new java.io.File(target.getExternalFilesDir(null),"audit-"+name+".png");try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
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
    private void main(Runnable r){java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();getInstrumentation().runOnMainSync(()->{try{r.run();}catch(Throwable e){failure.set(e);}});getInstrumentation().waitForIdleSync();Throwable e=failure.get();if(e instanceof AssertionError)throw (AssertionError)e;if(e!=null)throw new AssertionError(e);}
    private void await(BooleanSupplier check,long millis,String message)throws Exception{long until=SystemClock.uptimeMillis()+millis;while(SystemClock.uptimeMillis()<until){if(check.getAsBoolean())return;Thread.sleep(40);}fail(message);}
    private static String visible(View v){if(v.getVisibility()!=View.VISIBLE)return "";StringBuilder s=new StringBuilder();if(v instanceof TextView)s.append(((TextView)v).getText()).append(' ');if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)s.append(visible(g.getChildAt(i)));}return s.toString();}
}
