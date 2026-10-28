package org.xiangqi.study;

import android.app.Activity;
import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
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

/** The requested five replay controls, game menu and computer-opponent flows. */
public final class GameControlsTest extends InstrumentationTestCase {
    private Activity activity;
    private SharedPreferences study,enginePreferences;
    private Map<String,?> oldStudy,oldEngine;
    private GameRepository.Game first,middle,last;

    @Override protected void setUp()throws Exception{
        super.setUp();Context context=getInstrumentation().getTargetContext();
        study=context.getSharedPreferences("study",0);enginePreferences=context.getSharedPreferences("engine-settings",0);
        oldStudy=study.getAll();oldEngine=enginePreferences.getAll();study.edit().clear().commit();
        // The production settings loader also accepts finite, low-cost test limits.
        enginePreferences.edit().putInt("depth",3).putLong("millis",1500).putInt("threads",1).putInt("hashMb",32).putInt("lines",1).commit();
        activity=getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->findText(root(),"Xiangqi Study")!=null,15000,"Initial library did not load");
        first=fixture("a","Controls A","2020-01-01");middle=fixture("c","Controls C","2021-01-01");last=fixture("b","Controls B","2022-01-01");
        GameRepository.Game excluded=fixture("excluded","Unrelated player","2019-01-01");
        main(()->{set("games",new ArrayList<>(Arrays.asList(last,middle,excluded,first)));set("librarySort","oldest");set("search","Controls");open(first,false);});
        getInstrumentation().waitForIdleSync();
    }
    @Override protected void tearDown()throws Exception{
        if(activity!=null&&!activity.isDestroyed())main(activity::finish);
        getInstrumentation().waitForIdleSync();long deadline=SystemClock.uptimeMillis()+5000;
        while(activity!=null&&!activity.isDestroyed()&&SystemClock.uptimeMillis()<deadline)Thread.sleep(25);
        restore(study,oldStudy);restore(enginePreferences,oldEngine);super.tearDown();
    }

    public void testFiveControlOrderAndSortedFilteredGameNavigation()throws Exception{
        main(()->{
            View play=findDescription(root(),"Play replay");assertNotNull(play);ViewGroup controls=(ViewGroup)play.getParent();
            String[] descriptions={"Previous game","Next game","Play replay","Previous move","Next move"};
            assertEquals("Replay row has exactly five controls",5,controls.getChildCount());
            for(int i=0;i<descriptions.length;i++)assertEquals("Replay control order",descriptions[i],String.valueOf(controls.getChildAt(i).getContentDescription()));
        });
        clickDescription("Next move");clickDescription("Next move");assertEquals(2,ply());
        clickDescription("Next game");assertEquals("c",currentId());assertEquals("A fresh game opens at its own saved position",0,ply());
        clickDescription("Next move");assertEquals(1,ply());
        clickDescription("Previous game");assertEquals("a",currentId());assertEquals("Returning to a game restores its position",2,ply());
        clickDescription("Next game");assertEquals("c",currentId());assertEquals(1,ply());
        clickDescription("Next game");assertEquals("b",currentId());
        main(()->assertEquals("Game navigation preserves the library's filter","Controls",get("search")));
    }

    public void testGameMenuFlipsAndStartsAnalysis()throws Exception{
        final boolean[] before={false};main(()->before[0]=(Boolean)get("flipped"));
        clickDescription("Game menu");assertExternalText("Flip board");assertExternalText("Start analysis");assertExternalText("Play against engine");
        clickExternalText("Flip board");main(()->assertEquals(!before[0],get("flipped")));
        clickDescription("Game menu");clickExternalText("Start analysis");
        await(()->get("lastResult")!=null&&((Engine.Result)get("lastResult")).depth>0,15000,"Menu analysis did not complete a search depth");
        main(()->{
            Engine.Result result=(Engine.Result)get("lastResult");assertTrue(first.initial().legalMoves().contains(result.bestMove));
            assertTrue("Completed analysis is displayed",((TextView)get("analysisText")).getText().toString().contains("Depth "));
        });
        clickDescription("Next move");main(()->assertEquals("Analysis remains toggled on after navigation",Boolean.TRUE,get("analysisEnabled")));
        await(()->get("lastResult")!=null&&((Engine.Result)get("lastResult")).depth>0,15000,"Analysis did not follow the next position");
        main(()->assertTrue(first.initial().play(first.moves.get(0)).legalMoves().contains(((Engine.Result)get("lastResult")).bestMove)));
        clickDescription("Game menu");clickExternalText("Stop analysis");clickDescription("Next move");
        main(()->{assertEquals(Boolean.FALSE,get("analysisEnabled"));assertNull(get("lastResult"));assertNull(get("analysisFuture"));});
    }

    public void testReplayHasNoStandalonePlayEntryAndContextActionsAreConditional()throws Exception{
        main(()->{
            assertNull("The duplicate play entry must be removed",findText(root(),"Play from here"));
            assertEquals("Recorded replay has no contextual action",View.GONE,((View)get("exploreButton")).getVisibility());
        });
        clickDescription("Next move");clickDescription("Previous move");
        main(()->assertEquals("Moving through the record must not expose the contextual action",View.GONE,((View)get("exploreButton")).getVisibility()));
        startMatch("Play as Red");
        main(()->{
            assertEquals(View.VISIBLE,((View)get("exploreButton")).getVisibility());
            assertNotNull(findText(root(),"Stop playing"));
        });
        clickText("Stop playing");
        main(()->{
            assertEquals(Boolean.FALSE,get("vsEngine"));assertEquals(Boolean.TRUE,get("exploring"));
            assertEquals(View.VISIBLE,((View)get("exploreButton")).getVisibility());
            assertNotNull(findText(root(),"Return to recorded game"));
        });
        clickText("Return to recorded game");
        main(()->{
            assertEquals(Boolean.FALSE,get("exploring"));assertEquals(first.moves,get("workingMoves"));
            assertEquals("Returning to the record removes the contextual action",View.GONE,((View)get("exploreButton")).getVisibility());
        });
    }

    public void testPlayingRedGetsLegalComputerReplyAndCanReturnToRecord()throws Exception{
        startMatch("Play as Red");main(()->{assertEquals(Boolean.TRUE,get("vsEngine"));assertEquals(Boolean.TRUE,get("humanRed"));assertEquals(Boolean.FALSE,get("engineThinking"));});
        assertEquals(0,ply());
        main(()->invoke("makeMove",new Class<?>[]{String.class},"b2e2"));
        await(()->((Integer)get("ply"))==2&&!((Boolean)get("engineThinking")),20000,"Computer did not play a legal reply");
        main(()->{
            @SuppressWarnings("unchecked") List<String> moves=(List<String>)get("workingMoves");
            assertEquals(2,moves.size());assertEquals("b2e2",moves.get(0));
            assertTrue("Computer reply is legal for the human's actual move",Position.initial().play("b2e2").legalMoves().contains(moves.get(1)));
            assertEquals("Recorded source moves stay intact",6,first.moves.size());
            assertNotNull(findText(root(),"Stop playing"));
        });
        clickText("Stop playing");main(()->assertEquals(Boolean.FALSE,get("vsEngine")));
        clickText("Return to recorded game");main(()->{
            assertEquals(Boolean.FALSE,get("exploring"));assertEquals(first.moves,get("workingMoves"));
            assertEquals("Stopping computer play leaves its original record intact",6,first.moves.size());
        });
    }

    public void testPlayingBlackQueuesComputerMoveAndGameNavigationCancelsIt()throws Exception{
        ExecutorService worker=(ExecutorService)get("engine");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        worker.submit(()->{entered.countDown();try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
        assertTrue("Computer executor fixture must start",entered.await(3,TimeUnit.SECONDS));
        try{
            startMatch("Play as Black");main(()->{assertEquals(Boolean.TRUE,get("vsEngine"));assertEquals(Boolean.FALSE,get("humanRed"));assertEquals(Boolean.TRUE,get("engineThinking"));});
            clickDescription("Next game");assertEquals("c",currentId());assertEquals(0,ply());
            main(()->{assertEquals(Boolean.FALSE,get("vsEngine"));assertEquals(Boolean.FALSE,get("engineThinking"));});
            release.countDown();worker.submit(()->{}).get(8,TimeUnit.SECONDS);Thread.sleep(250);
            assertEquals("A cancelled opponent must not play in the next game","c",currentId());assertEquals(0,ply());
            main(()->assertEquals(middle.moves,get("workingMoves")));
        }finally{release.countDown();}
    }

    public void testBackgroundingComputerGameRestoresInteractiveVariationOnResume()throws Exception{
        ExecutorService worker=(ExecutorService)get("engine");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        worker.submit(()->{entered.countDown();try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(3,TimeUnit.SECONDS));
        try{
            startMatch("Play as Black");main(()->assertEquals(Boolean.TRUE,get("engineThinking")));
            assertTrue("Home action must background this app",getInstrumentation().getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME));
            await(()->!((Boolean)get("vsEngine")),8000,"Backgrounding must cancel opponent play");
            release.countDown();worker.submit(()->{}).get(8,TimeUnit.SECONDS);
            getInstrumentation().getTargetContext().startActivity(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            await(()->activity.hasWindowFocus(),10000,"Replay did not regain focus");
            main(()->{
                assertEquals(Boolean.FALSE,get("engineThinking"));assertEquals(Boolean.FALSE,get("vsEngine"));
                assertNotNull("Resumed study exposes its recorded-game return",findText(root(),"Return to recorded game"));
                assertNull("Resumed study must not retain a stale active-match button",findText(root(),"Stop playing"));
                assertTrue("Board must become interactive after cancelled opponent play",((BoardView)get("board")).getContentDescription().toString().contains("Explore mode."));
            });
        }finally{release.countDown();}
    }

    private void startMatch(String side)throws Exception{clickDescription("Game menu");clickExternalText("Play against engine");clickExternalText(side);getInstrumentation().waitForIdleSync();}
    private void clickDescription(String value)throws Exception{if(value.equals("Game menu")){UiTestActions.openGameMenu(getInstrumentation(),activity);return;}main(()->{View control=findDescription(root(),value);assertNotNull("Missing control "+value,control);assertTrue("Control is enabled: "+value,control.isEnabled());control.performClick();});getInstrumentation().waitForIdleSync();}
    private void clickText(String value)throws Exception{main(()->{View control=findText(root(),value);assertNotNull("Missing control "+value,control);control.performClick();});getInstrumentation().waitForIdleSync();}
    private void assertExternalText(String value)throws Exception{long deadline=SystemClock.uptimeMillis()+5000;while(SystemClock.uptimeMillis()<deadline){if(nodeText(getInstrumentation().getUiAutomation().getRootInActiveWindow(),value)!=null)return;Thread.sleep(50);}fail("Menu did not show "+value);}
    private void clickExternalText(String value)throws Exception{assertTrue("Menu action must activate: "+value,UiTestActions.clickPopup(getInstrumentation(),value));}
    private void await(BooleanSupplier condition,long timeout,String message)throws Exception{long deadline=SystemClock.uptimeMillis()+timeout;boolean[] ready={false};while(SystemClock.uptimeMillis()<deadline){main(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(50);}fail(message+diagnostics());}
    private String diagnostics(){String[] details={""};main(()->{Object rawResult=get("lastResult"),rawText=get("analysisText"),rawFuture=get("analysisFuture");Engine.Result result=rawResult instanceof Engine.Result?(Engine.Result)rawResult:null;Future<?> future=rawFuture instanceof Future?(Future<?>)rawFuture:null;details[0]=" [ply="+get("ply")+", analysis="+(result==null?"none":"depth "+result.depth+", best "+result.bestMove)+", future="+(future==null?"none":"done="+future.isDone()+", cancelled="+future.isCancelled())+", text="+(rawText instanceof TextView?((TextView)rawText).getText():"none")+"]";});return details[0];}
    private int ply(){int[] value={-1};main(()->value[0]=(Integer)get("ply"));return value[0];}
    private String currentId(){String[] value={null};main(()->value[0]=((GameRepository.Game)get("game")).id);return value[0];}
    private void open(GameRepository.Game game,boolean restore){invoke("openGame",new Class<?>[]{GameRepository.Game.class,boolean.class},game,restore);}
    private Object get(String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}catch(Exception error){throw new RuntimeException(error);}}
    private void set(String name,Object value){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}catch(Exception error){throw new RuntimeException(error);}}
    private void invoke(String name,Class<?>[] types,Object... args){try{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(activity,args);}catch(Exception error){throw new RuntimeException(error);}}
    private void main(Runnable work){UiTestActions.runOnMain(getInstrumentation(),work);}
    private View root(){return activity.getWindow().getDecorView();}
    private static GameRepository.Game fixture(String id,String red,String date){GameRepository.Game game=new GameRepository.Game();game.id=id;game.red=red;game.black="Opponent";game.date=date;game.event="Navigation audit";game.round="";game.sourceUrl="";game.startFen="";game.result="*";game.moves.addAll(Arrays.asList("b2e2","b9c7","a0a1","a9a8","h2g2","h9g7"));return game;}
    private static View findText(View view,String text){if(view instanceof TextView&&!(view instanceof EditText)&&((TextView)view).getText().toString().startsWith(text))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View hit=findText(group.getChildAt(i),text);if(hit!=null)return hit;}}return null;}
    private static View findDescription(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View hit=findDescription(group.getChildAt(i),text);if(hit!=null)return hit;}}return null;}
    private static AccessibilityNodeInfo nodeText(AccessibilityNodeInfo node,String text){if(node==null)return null;if(text.equalsIgnoreCase(node.getText()==null?"":node.getText().toString()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo hit=nodeText(node.getChild(i),text);if(hit!=null)return hit;}return null;}
    private static void restore(SharedPreferences preferences,Map<String,?> values){SharedPreferences.Editor edit=preferences.edit().clear();for(Map.Entry<String,?> entry:values.entrySet()){String key=entry.getKey();Object value=entry.getValue();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> set=(Set<String>)value;edit.putStringSet(key,set);}}edit.commit();}
}
