package org.xiangqi.study;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.xiangqi.study.core.Engine;
import org.xiangqi.study.core.Position;

/** Direct study-board interaction must fork a record with or without analysis. */
public final class VariationEditingAuditTest extends InstrumentationTestCase {
    private Activity activity;
    private SharedPreferences study,engine;
    private Map<String,?> oldStudy,oldEngine;
    private GameRepository.Game recorded;
    private final List<String> source=Arrays.asList("b2e2","b9c7","a0a1","a9a8","h0g2","h9g7");

    @Override protected void setUp()throws Exception {
        super.setUp();Context context=getInstrumentation().getTargetContext();
        study=context.getSharedPreferences("study",0);engine=context.getSharedPreferences("engine-settings",0);
        oldStudy=study.getAll();oldEngine=engine.getAll();study.edit().clear().commit();
        engine.edit().putInt("depth",2).putLong("millis",1000).putInt("threads",1).putInt("hashMb",32).putInt("lines",1).commit();
        activity=getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->Boolean.TRUE.equals(get("loaded")),15000,"Library did not load");
        recorded=new GameRepository.Game();recorded.id="direct-variation-audit";recorded.red="Recorded Red";recorded.black="Recorded Black";
        recorded.event="Board variation audit";recorded.round="";recorded.date="2023-11-20";recorded.result="*";recorded.sourceUrl="";recorded.startFen="";recorded.moves.addAll(source);
        main(()->{set("games",new ArrayList<>(Collections.singletonList(recorded)));call("openGame",new Class<?>[]{GameRepository.Game.class,boolean.class},recorded,false);});
        settleBoard();
    }

    @Override protected void tearDown()throws Exception {
        if(activity!=null&&!activity.isDestroyed())main(activity::finish);getInstrumentation().waitForIdleSync();
        Field io=MainActivity.class.getDeclaredField("FILE_IO");io.setAccessible(true);((ExecutorService)io.get(null)).submit(()->{}).get(15,TimeUnit.SECONDS);
        long until=SystemClock.uptimeMillis()+5000;while(activity!=null&&!activity.isDestroyed()&&SystemClock.uptimeMillis()<until)Thread.sleep(25);
        restore(study,oldStudy);restore(engine,oldEngine);super.tearDown();
    }

    public void testRecordedBoardAcceptsBlackAndRedVariationsWithAnalysisOff()throws Exception {
        main(()->{call("navigate",new Class<?>[]{int.class},1);assertEquals(Boolean.FALSE,get("exploring"));assertEquals(Boolean.FALSE,get("analysisEnabled"));study.edit().putLong("replayDelayMillis",60000).commit();call("toggleAutoplay");assertEquals(Boolean.TRUE,get("autoplay"));});
        tapSquare("h9");main(()->{assertEquals("Touching a piece pauses replay before its destination is chosen",Boolean.FALSE,get("autoplay"));assertEquals(1,get("ply"));});tapSquare("g7");assertVariation(Arrays.asList("b2e2","h9g7"),false);
        tapMove("b0a2");assertVariation(Arrays.asList("b2e2","h9g7","b0a2"),false);
        returnToRecord(3,false);
        main(()->{assertNull(get("analysisFuture"));assertEquals(View.GONE,((View)get("analysisBox")).getVisibility());});
    }

    public void testRecordedBoardVariationsFollowAnalysisAndRemainEditableAfterStopping()throws Exception {
        main(()->{call("navigate",new Class<?>[]{int.class},2);call("analyze");});awaitAnalysis();
        int before=generation();tapMove("b0a2");assertVariation(Arrays.asList("b2e2","b9c7","b0a2"),true);awaitAnalysis();assertTrue("Analysis restarts for the first variation move",generation()>before);
        before=generation();tapMove("h9g7");assertVariation(Arrays.asList("b2e2","b9c7","b0a2","h9g7"),true);awaitAnalysis();assertTrue("Analysis restarts for the opponent's variation move",generation()>before);
        returnToRecord(4,true);awaitAnalysis();
        main(()->call("stopSearch"));tapMove("b0a2");assertVariation(Arrays.asList("b2e2","b9c7","a0a1","a9a8","b0a2"),false);returnToRecord(5,false);
        main(()->{assertNull(get("analysisFuture"));assertEquals(View.GONE,((View)get("analysisBox")).getVisibility());});
    }

    public void testLegalMoveChooserPausesReplayAndRejectsAStalePosition()throws Exception {
        final AlertDialog[] chooser={null};final String[] chosen={null};
        main(()->{
            call("makeMove",new Class<?>[]{String.class},"b2e2");call("navigate",new Class<?>[]{int.class},0);
            study.edit().putLong("replayDelayMillis",500).commit();call("toggleAutoplay");assertEquals(Boolean.TRUE,get("autoplay"));
            chosen[0]=current().legalMoves().get(0);call("legalMoves");
            assertEquals("Opening a position-specific move chooser pauses playback",Boolean.FALSE,get("autoplay"));
            chooser[0]=(AlertDialog)get("legalMovesDialog");assertNotNull(chooser[0]);assertTrue(chooser[0].isShowing());
        });
        Thread.sleep(700);main(()->{
            assertEquals("The chooser must keep its original position",0,get("ply"));
            chooser[0].getListView().performItemClick(null,0,chooser[0].getListView().getAdapter().getItemId(0));
            assertEquals(Collections.singletonList(chosen[0]),moves());assertEquals(1,get("ply"));assertEquals(source,recorded.moves);
            call("navigate",new Class<?>[]{int.class},0);call("legalMoves");chooser[0]=(AlertDialog)get("legalMovesDialog");
            call("navigate",new Class<?>[]{int.class},1);
            chooser[0].getListView().performItemClick(null,0,chooser[0].getListView().getAdapter().getItemId(0));
            assertEquals("A stale choice cannot change the new position",1,get("ply"));assertEquals(Collections.singletonList(chosen[0]),moves());assertNull(get("errorDialog"));
            call("legalMoves");chooser[0]=(AlertDialog)get("legalMovesDialog");activity.finish();
        });
        await(activity::isDestroyed,5000,"Activity did not finish");assertFalse("The legal-move window closes on destruction",chooser[0].isShowing());
    }

    public void testEngineSideChooserPausesReplayAtTheSelectedPosition()throws Exception {
        main(()->{
            call("navigate",new Class<?>[]{int.class},1);study.edit().putLong("replayDelayMillis",500).commit();call("toggleAutoplay");
            assertEquals(Boolean.TRUE,get("autoplay"));call("chooseEngineSide");
            assertEquals("Choosing the engine side must freeze the advertised current position",Boolean.FALSE,get("autoplay"));
        });
        Thread.sleep(700);main(()->{
            assertEquals(1,get("ply"));AlertDialog chooser=(AlertDialog)get("playSideDialog");assertTrue(chooser.isShowing());
            chooser.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();assertEquals(Boolean.FALSE,get("autoplay"));assertEquals(source,recorded.moves);
            call("chooseEngineSide");chooser=(AlertDialog)get("playSideDialog");call("navigate",new Class<?>[]{int.class},2);
            chooser.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals("A chooser from an old position cannot start a new engine game",Boolean.FALSE,get("vsEngine"));assertEquals(2,get("ply"));
        });
    }

    private void assertVariation(List<String> expected,boolean enabled){main(()->{
        assertEquals("A board move enters a separate working variation",Boolean.TRUE,get("exploring"));assertEquals(expected,moves());assertEquals(expected.size(),get("ply"));
        assertEquals("The original recorded moves remain intact",source,recorded.moves);assertEquals(enabled,get("analysisEnabled"));assertEquals(enabled,study.getBoolean("analysisEnabled",false));
        View action=findText(root(),"Return to recorded game");assertNotNull(action);assertEquals(View.VISIBLE,action.getVisibility());
        Position replay=recorded.initial();for(String move:expected)replay=replay.play(move);assertEquals(replay.toFen(),current().toFen());
    });}

    private void returnToRecord(int expectedPly,boolean enabled)throws Exception {
        main(()->{View action=findText(root(),"Return to recorded game");assertNotNull(action);action.performClick();});getInstrumentation().waitForIdleSync();
        main(()->{
            assertEquals(Boolean.FALSE,get("exploring"));assertEquals(source,moves());assertEquals(source,recorded.moves);assertEquals(expectedPly,get("ply"));assertEquals(enabled,get("analysisEnabled"));
            assertEquals("Returning restores the full source history",source.size()+1,positions().size());assertEquals(View.GONE,((View)get("exploreButton")).getVisibility());
            Position replay=recorded.initial();for(int i=0;i<expectedPly;i++)replay=replay.play(source.get(i));assertEquals(replay.toFen(),current().toFen());
        });settleBoard();
    }

    private void tapMove(String move)throws Exception {tapSquare(move.substring(0,2));tapSquare(move.substring(2));}
    private void tapSquare(String square)throws Exception {
        settleBoard();final float[] xy={0,0};
        main(()->{try{BoardView board=(BoardView)get("board");int[] location=new int[2];board.getLocationOnScreen(location);Method x=BoardView.class.getDeclaredMethod("x",int.class),y=BoardView.class.getDeclaredMethod("y",int.class);x.setAccessible(true);y.setAccessible(true);int index=Position.index(square);xy[0]=location[0]+(Float)x.invoke(board,index);xy[1]=location[1]+(Float)y.invoke(board,index);Rect visible=new Rect();assertTrue(board.getGlobalVisibleRect(visible));assertTrue("The tapped intersection must be visible",visible.contains(Math.round(xy[0]),Math.round(xy[1])));}catch(Exception error){throw new RuntimeException(error);}});
        long down=SystemClock.uptimeMillis();send(down,MotionEvent.ACTION_DOWN,xy[0],xy[1]);send(down,MotionEvent.ACTION_UP,xy[0],xy[1]);getInstrumentation().waitForIdleSync();
    }
    private void send(long down,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{assertTrue("Board tap must be injected",getInstrumentation().getUiAutomation().injectInputEvent(event,true));}finally{event.recycle();}}
    private void settleBoard()throws Exception {CountDownLatch frame=new CountDownLatch(1);main(()->((View)get("board")).postOnAnimation(()->((View)get("board")).postOnAnimation(frame::countDown)));assertTrue("Board drawing must settle before touch input",frame.await(5,TimeUnit.SECONDS));getInstrumentation().waitForIdleSync();}
    private void awaitAnalysis()throws Exception {await(()->get("analysisFuture")==null&&get("lastResult") instanceof Engine.Result&&((Engine.Result)get("lastResult")).depth>0,10000,"Enabled analysis did not complete for the viewed position");main(()->assertTrue(current().legalMoves().contains(((Engine.Result)get("lastResult")).bestMove)));}
    private int generation(){int[] value={0};main(()->value[0]=(Integer)get("analysisGeneration"));return value[0];}
    private Position current(){return positions().get((Integer)get("ply"));}
    @SuppressWarnings("unchecked") private List<Position> positions(){return (List<Position>)get("positions");}
    @SuppressWarnings("unchecked") private List<String> moves(){return (List<String>)get("workingMoves");}
    private View root(){return activity.getWindow().getDecorView();}
    private void main(Runnable action){UiTestActions.runOnMain(getInstrumentation(),action);}
    private void await(BooleanSupplier condition,long timeout,String message)throws Exception {long until=SystemClock.uptimeMillis()+timeout;boolean[] ready={false};while(SystemClock.uptimeMillis()<until){main(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(40);}fail(message);}
    private Object get(String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}catch(Exception error){throw new RuntimeException(error);}}
    private void set(String name,Object value){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}catch(Exception error){throw new RuntimeException(error);}}
    private Object call(String name){return call(name,new Class<?>[0]);}
    private Object call(String name,Class<?>[] types,Object... values){try{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,values);}catch(Exception error){throw new RuntimeException(error);}}
    private static View findText(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findText(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static void restore(SharedPreferences preferences,Map<String,?> values){SharedPreferences.Editor edit=preferences.edit().clear();for(Map.Entry<String,?> entry:values.entrySet()){String key=entry.getKey();Object value=entry.getValue();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> set=(Set<String>)value;edit.putStringSet(key,set);}}edit.commit();}
}
