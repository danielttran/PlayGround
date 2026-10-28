package org.xiangqi.study;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;
import org.xiangqi.study.core.Engine;
import org.xiangqi.study.core.Position;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Analysis is a user toggle; cancelling a position's work must preserve that choice. */
public final class PersistentAnalysisTest extends InstrumentationTestCase {
    private Activity activity;
    private SharedPreferences study,enginePreferences;
    private Map<String,?> oldStudy,oldEngine;
    private GameRepository.Game first,second,realGame;
    private List<GameRepository.Game> realGames;

    @Override protected void setUp()throws Exception{
        super.setUp();Context context=getInstrumentation().getTargetContext();
        study=context.getSharedPreferences("study",0);enginePreferences=context.getSharedPreferences("engine-settings",0);
        oldStudy=study.getAll();oldEngine=enginePreferences.getAll();study.edit().clear().commit();
        enginePreferences.edit().putInt("depth",3).putLong("millis",1500).putInt("threads",1).putInt("hashMb",32).putInt("lines",1).commit();
        activity=getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->findText(root(),"Xiangqi Study")!=null,15000,"Initial library did not load");
        main(()->{
            @SuppressWarnings("unchecked") List<GameRepository.Game> loaded=(List<GameRepository.Game>)get("games");
            realGames=new ArrayList<>(loaded);
            for(GameRepository.Game candidate:loaded)if(candidate.moves.size()>2){realGame=candidate;break;}
            assertNotNull("A real game is needed to audit restoration",realGame);
            first=fixture("persistent-a","Analysis A","2020-01-01");second=fixture("persistent-b","Analysis B","2021-01-01");
            set("games",new ArrayList<>(Arrays.asList(first,second)));set("librarySort","oldest");set("search","");open(first,false);
        });
        getInstrumentation().waitForIdleSync();
    }

    @Override protected void tearDown()throws Exception{
        if(activity!=null&&!activity.isDestroyed())main(activity::finish);
        getInstrumentation().waitForIdleSync();long deadline=SystemClock.uptimeMillis()+5000;
        while(activity!=null&&!activity.isDestroyed()&&SystemClock.uptimeMillis()<deadline)Thread.sleep(25);
        restore(study,oldStudy);restore(enginePreferences,oldEngine);super.tearDown();
    }

    public void testFiniteCompletionKeepsToggleAndNavigationAnalysesBothSides()throws Exception{
        enableAnalysis();awaitCompleted(-1);
        main(()->{assertEquals(Boolean.TRUE,get("analysisEnabled"));assertTrue(study.getBoolean("analysisEnabled",false));assertTrue(current().redToMove);});
        assertAnalysisMenu("Stop analysis");
        int generation=generation();clickDescription("Next move");awaitCompleted(generation);
        main(()->assertFalse("The next position is Black to move",current().redToMove));
        generation=generation();clickDescription("Next move");awaitCompleted(generation);
        main(()->assertTrue("Analysis must also restart when Red is to move",current().redToMove));
        generation=generation();clickDescription("Previous move");awaitCompleted(generation);
        main(()->assertFalse(current().redToMove));
        assertAnalysisMenu("Stop analysis");
    }

    public void testSuggestedMoveAndReturningToRecordRestartAnalysis()throws Exception{
        enableAnalysis();awaitCompleted(-1);
        int generation=generation();clickText("Explore suggested move");awaitCompleted(generation);
        main(()->{assertEquals(Boolean.TRUE,get("exploring"));assertEquals(1,get("ply"));assertEquals(Boolean.TRUE,get("analysisEnabled"));});
        generation=generation();clickText("Return to recorded game");awaitCompleted(generation);
        main(()->{assertEquals(Boolean.FALSE,get("exploring"));assertEquals(first.moves,get("workingMoves"));assertEquals(Boolean.TRUE,get("analysisEnabled"));});
    }

    public void testLibraryAndGameNavigationPauseAndResumeEnabledAnalysis()throws Exception{
        enableAnalysis();awaitCompleted(-1);
        ExecutorService worker=(ExecutorService)get("engine");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        worker.submit(()->{entered.countDown();try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}});
        assertTrue("Search executor fixture must start",entered.await(3,TimeUnit.SECONDS));
        try{
            clickDescription("Next move");Future<?>[] pending={null};main(()->pending[0]=(Future<?>)get("analysisFuture"));assertNotNull(pending[0]);
            clickText("‹  Library");
            main(()->{
                assertEquals(Boolean.TRUE,get("library"));assertEquals(Boolean.TRUE,get("analysisEnabled"));
                assertNull("Library must release the position search",get("analysisFuture"));assertNull(get("lastResult"));
            });
            assertTrue("Opening the library cancels the previous position's task",pending[0].isCancelled());
            release.countDown();worker.submit(()->{}).get(8,TimeUnit.SECONDS);getInstrumentation().waitForIdleSync();
            main(()->{assertEquals(Boolean.TRUE,get("library"));assertNull("Obsolete callbacks must not repopulate the analysis",get("lastResult"));assertNotNull(findText(root(),"Xiangqi Study"));});
            int generation=generation();clickText("Return to current game");awaitCompleted(generation);
            main(()->assertEquals(first.id,((GameRepository.Game)get("game")).id));
            generation=generation();clickDescription("Next game");awaitCompleted(generation);
            main(()->{assertEquals(second.id,((GameRepository.Game)get("game")).id);assertEquals(Boolean.TRUE,get("analysisEnabled"));});
        }finally{release.countDown();}
    }

    public void testRecreationAndBackgroundResumePreserveAnalysisToggle()throws Exception{
        openRestorableGame();enableAnalysis();awaitCompleted(-1);recreate();
        await(()->!((Boolean)get("library"))&&get("game")!=null,15000,"Recorded game did not restore");awaitCompleted(-1);
        main(()->{assertEquals(realGame.id,((GameRepository.Game)get("game")).id);assertEquals(Boolean.TRUE,get("analysisEnabled"));});
        assertTrue("Home action must background this app",getInstrumentation().getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME));
        await(()->!((Boolean)get("foreground")),8000,"Activity did not enter the background");
        main(()->{assertEquals(Boolean.TRUE,get("analysisEnabled"));assertNull("Background must release its search",get("analysisFuture"));});
        int generation=generation();Context context=getInstrumentation().getTargetContext();
        context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        await(()->activity.hasWindowFocus()&&((Boolean)get("foreground")),10000,"Replay did not regain focus");awaitCompleted(generation);
    }

    public void testExplicitStopCancelsQueuedSearchAndPreventsLaterRestarts()throws Exception{
        openRestorableGame();enableAnalysis();awaitCompleted(-1);
        ExecutorService worker=(ExecutorService)get("engine");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        worker.submit(()->{entered.countDown();try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}});
        assertTrue("Search executor fixture must start",entered.await(3,TimeUnit.SECONDS));
        try{
            clickDescription("Next move");main(()->assertNotNull("Enabled analysis must queue work for the new position",get("analysisFuture")));
            clickDescription("Game menu");clickExternalText("Stop analysis");
            main(()->{assertEquals(Boolean.FALSE,get("analysisEnabled"));assertFalse(study.getBoolean("analysisEnabled",true));assertNull(get("analysisFuture"));});
            release.countDown();worker.submit(()->{}).get(8,TimeUnit.SECONDS);
            clickDescription("Next move");clickDescription("Previous move");clickText("‹  Library");clickText("Return to current game");
            main(()->{assertEquals(Boolean.FALSE,get("analysisEnabled"));assertNull(get("analysisFuture"));assertNull("Stopped search callbacks must not repopulate another position",get("lastResult"));});
            assertAnalysisMenu("Start analysis");recreate();
            await(()->!((Boolean)get("library"))&&get("game")!=null,15000,"Stopped game did not restore");
            main(()->{assertEquals(Boolean.FALSE,get("analysisEnabled"));assertNull("Recreation must honor explicit stop",get("analysisFuture"));});
        }finally{release.countDown();}
    }

    public void testEnginePlayPausesAnalysisAndStoppingPlayResumesIt()throws Exception{
        enableAnalysis();awaitCompleted(-1);clickDescription("Game menu");clickExternalText("Play against engine");clickExternalText("Play as Red");
        main(()->{
            assertEquals(Boolean.TRUE,get("vsEngine"));assertEquals(Boolean.TRUE,get("analysisEnabled"));
            assertNull("Study analysis must not compete with opponent play",get("analysisFuture"));
        });
        int generation=generation();clickText("Stop playing");awaitCompleted(generation);
        main(()->{assertEquals(Boolean.FALSE,get("vsEngine"));assertEquals(Boolean.TRUE,get("analysisEnabled"));});
    }

    public void testAutoplayKeepsAdvancingWithAnalysisEnabled()throws Exception{
        enableAnalysis();awaitCompleted(-1);
        main(()->{
            findDescription(root(),"Play replay").performClick();assertBestArrow();
            findDescription(root(),"Pause replay").performClick();assertBestArrow();
        });
        clickDescription("Play replay");
        await(()->((Integer)get("ply"))>=2,8000,"Analysis restarts must not interrupt replay playback");
        main(()->{assertEquals(Boolean.TRUE,get("autoplay"));assertEquals(Boolean.TRUE,get("analysisEnabled"));});
        clickDescription("Pause replay");awaitCompleted(-1);
    }

    private void enableAnalysis()throws Exception{clickDescription("Game menu");clickExternalText("Start analysis");}
    private void awaitCompleted(int afterGeneration)throws Exception{
        await(()->((Integer)get("analysisGeneration"))>afterGeneration&&((Boolean)get("analysisEnabled"))&&get("analysisFuture")==null&&get("lastResult") instanceof Engine.Result&&((Engine.Result)get("lastResult")).depth>0,20000,"Enabled analysis did not complete at the current position");
        main(()->{
            Engine.Result result=(Engine.Result)get("lastResult");assertTrue("Displayed best move must be legal for this position",current().legalMoves().contains(result.bestMove));
            assertTrue("Search result must be displayed",((TextView)get("analysisText")).getText().toString().contains("Depth "));
            assertBestArrow();
        });
    }
    private void assertBestArrow(){try{Field hint=BoardView.class.getDeclaredField("hintMove");hint.setAccessible(true);assertEquals("The best-move arrow must match this position's result",((Engine.Result)get("lastResult")).bestMove,hint.get(get("board")));}catch(ReflectiveOperationException error){throw new RuntimeException(error);}}
    private void assertAnalysisMenu(String action)throws Exception{
        clickDescription("Game menu");long deadline=SystemClock.uptimeMillis()+5000;boolean visible=false;
        while(SystemClock.uptimeMillis()<deadline){if(nodeText(getInstrumentation().getUiAutomation().getRootInActiveWindow(),action)!=null){visible=true;break;}Thread.sleep(50);}
        assertTrue("Analysis menu did not expose "+action,visible);
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);getInstrumentation().waitForIdleSync();
    }
    private void openRestorableGame(){main(()->{set("games",new ArrayList<>(realGames));open(realGame,false);});getInstrumentation().waitForIdleSync();}
    private void recreate()throws Exception{
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        main(activity::recreate);Activity replacement=monitor.waitForActivityWithTimeout(10000);getInstrumentation().removeMonitor(monitor);
        assertNotNull("Activity must recreate",replacement);activity=replacement;getInstrumentation().waitForIdleSync();
    }
    @SuppressWarnings("unchecked") private Position current(){return ((List<Position>)get("positions")).get((Integer)get("ply"));}
    private int generation(){int[] generation={-1};main(()->generation[0]=(Integer)get("analysisGeneration"));return generation[0];}
    private void clickDescription(String value)throws Exception{if(value.equals("Game menu")){UiTestActions.openGameMenu(getInstrumentation(),activity);return;}main(()->{View control=findDescription(root(),value);assertNotNull("Missing control "+value,control);assertTrue("Control is enabled: "+value,control.isEnabled());control.performClick();});getInstrumentation().waitForIdleSync();}
    private void clickText(String value){main(()->{View control=findText(root(),value);assertNotNull("Missing control "+value,control);control.performClick();});getInstrumentation().waitForIdleSync();}
    private void clickExternalText(String value)throws Exception{assertTrue("Menu action must activate: "+value,UiTestActions.clickPopup(getInstrumentation(),value));}
    private void await(BooleanSupplier condition,long timeout,String message)throws Exception{long deadline=SystemClock.uptimeMillis()+timeout;boolean[] ready={false};while(SystemClock.uptimeMillis()<deadline){main(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(50);}fail(message+diagnostics());}
    private String diagnostics(){String[] details={""};main(()->{Object result=get("lastResult");details[0]=" [ply="+get("ply")+", enabled="+get("analysisEnabled")+", foreground="+get("foreground")+", library="+get("library")+", future="+get("analysisFuture")+", result="+(result instanceof Engine.Result?"depth "+((Engine.Result)result).depth:"none")+", text="+(get("analysisText") instanceof TextView?((TextView)get("analysisText")).getText():"none")+"]";});return details[0];}
    private void open(GameRepository.Game selected,boolean restore){invoke("openGame",new Class<?>[]{GameRepository.Game.class,boolean.class},selected,restore);}
    private Object get(String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}catch(Exception error){throw new RuntimeException(error);}}
    private void set(String name,Object value){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}catch(Exception error){throw new RuntimeException(error);}}
    private void invoke(String name,Class<?>[] types,Object... args){try{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(activity,args);}catch(Exception error){throw new RuntimeException(error);}}
    private void main(Runnable work){UiTestActions.runOnMain(getInstrumentation(),work);}
    private View root(){return activity.getWindow().getDecorView();}
    private static GameRepository.Game fixture(String id,String red,String date){GameRepository.Game game=new GameRepository.Game();game.id=id;game.red=red;game.black="Opponent";game.date=date;game.event="Persistent analysis audit";game.round="";game.sourceUrl="";game.startFen="";game.result="*";game.moves.addAll(Arrays.asList("b2e2","b9c7","a0a1","a9a8","h2g2","h9g7"));return game;}
    private static View findText(View view,String text){if(view instanceof TextView&&!(view instanceof EditText)&&((TextView)view).getText().toString().startsWith(text))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View hit=findText(group.getChildAt(i),text);if(hit!=null)return hit;}}return null;}
    private static View findDescription(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View hit=findDescription(group.getChildAt(i),text);if(hit!=null)return hit;}}return null;}
    private static AccessibilityNodeInfo nodeText(AccessibilityNodeInfo node,String text){if(node==null)return null;if(text.equalsIgnoreCase(node.getText()==null?"":node.getText().toString()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo hit=nodeText(node.getChild(i),text);if(hit!=null)return hit;}return null;}
    private static void restore(SharedPreferences preferences,Map<String,?> values){SharedPreferences.Editor edit=preferences.edit().clear();for(Map.Entry<String,?> entry:values.entrySet()){String key=entry.getKey();Object value=entry.getValue();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> set=(Set<String>)value;edit.putStringSet(key,set);}}edit.commit();}
}
