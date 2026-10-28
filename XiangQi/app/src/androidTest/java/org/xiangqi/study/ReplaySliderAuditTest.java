package org.xiangqi.study;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.xiangqi.study.core.Position;

/** Real accessibility, keyboard and touch navigation through the replay slider. */
public final class ReplaySliderAuditTest extends InstrumentationTestCase {
    private Activity activity;
    private SharedPreferences study,engine;
    private Map<String,?> oldStudy,oldEngine;

    @Override protected void setUp()throws Exception {
        super.setUp();Context context=getInstrumentation().getTargetContext();
        study=context.getSharedPreferences("study",0);engine=context.getSharedPreferences("engine-settings",0);
        oldStudy=study.getAll();oldEngine=engine.getAll();study.edit().clear().commit();
        engine.edit().putInt("depth",2).putLong("millis",1000).putInt("threads",1).putInt("hashMb",32).putInt("lines",1).commit();
        activity=getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->Boolean.TRUE.equals(get("loaded")),15000,"Library did not load");
        main(()->{GameRepository.Game game=games().get(0);call("openGame",new Class<?>[]{GameRepository.Game.class,boolean.class},game,false);});getInstrumentation().waitForIdleSync();
    }
    @Override protected void tearDown()throws Exception {
        if(activity!=null&&!activity.isDestroyed())main(activity::finish);getInstrumentation().waitForIdleSync();
        Field io=MainActivity.class.getDeclaredField("FILE_IO");io.setAccessible(true);((ExecutorService)io.get(null)).submit(()->{}).get(15,TimeUnit.SECONDS);
        long until=SystemClock.uptimeMillis()+5000;while(activity!=null&&!activity.isDestroyed()&&SystemClock.uptimeMillis()<until)Thread.sleep(25);
        restore(study,oldStudy);restore(engine,oldEngine);super.tearDown();
    }

    public void testSliderCoversEndpointsAndFollowsButtonsKeyboardAutoplayAndGoToMove()throws Exception {
        final int[] last={0};main(()->{
            SeekBar slider=slider();assertNotNull(slider);last[0]=moves().size();assertTrue(last[0]>4);assertEquals(last[0],slider.getMax());assertEquals(0,slider.getProgress());
            ViewGroup content=(ViewGroup)get("content");View boardRow=(View)((View)get("board")).getParent();
            assertEquals("The compact slider sits immediately below the board",content.indexOfChild(boardRow)+1,content.indexOfChild(slider));
            setAccessibleProgress(last[0]);assertEquals(last[0],ply());assertEquals(last[0],slider.getProgress());
            setAccessibleProgress(0);assertEquals(0,ply());slider.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));
            assertEquals("Keyboard advances one half-move",1,ply());assertEquals(1,slider.getProgress());
            findDescription(root(),"Next move").performClick();assertEquals(2,slider.getProgress());findDescription(root(),"Previous move").performClick();assertEquals(1,slider.getProgress());
        });
        UiTestActions.goToMove(getInstrumentation(),activity,last[0]/2);
        main(()->{assertEquals(last[0]/2,slider().getProgress());study.edit().putLong("replayDelayMillis",500).commit();call("toggleAutoplay");});
        await(()->ply()>last[0]/2,3000,"Autoplay did not advance the slider");
        main(()->{call("stopAutoplay");assertEquals(ply(),slider().getProgress());});
    }

    public void testCustomFenVariationBoundsAndOldSliderCannotNavigateNewGame()throws Exception {
        final SeekBar[] old={null};GameRepository.Game custom=new GameRepository.Game();custom.id="slider-custom-black";custom.red="Custom Red";custom.black="Custom Black";
        custom.event="Slider audit";custom.round="";custom.date="2026-10-28";custom.result="*";custom.sourceUrl="";custom.startFen=Position.START_FEN.replace(" w "," b ");
        custom.moves.addAll(Arrays.asList("b7e7","b0c2","a9a8"));
        main(()->{
            old[0]=slider();List<GameRepository.Game> entries=new ArrayList<>(games());entries.add(custom);set("games",entries);call("openGame",new Class<?>[]{GameRepository.Game.class,boolean.class},custom,false);
            assertEquals(3,slider().getMax());setAccessibleProgress(3);assertEquals(3,ply());setAccessibleProgress(1);
            call("toggleExplore");call("makeMove",new Class<?>[]{String.class},"b0a2");assertEquals(2,slider().getMax());assertEquals(2,slider().getProgress());
            call("toggleExplore");assertEquals("Returning to source restores its complete range",3,slider().getMax());assertEquals(2,slider().getProgress());
            call("startEngineGame",new Class<?>[]{boolean.class},false);assertTrue((Boolean)get("vsEngine"));setAccessibleProgress(0);
            assertFalse("Manual progression ends engine play",(Boolean)get("vsEngine"));assertNull(get("playFuture"));assertEquals(0,ply());
            Bundle args=new Bundle();args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,old[0].getMax());old[0].performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args);
            assertEquals("A detached slider cannot navigate the new game",0,ply());assertEquals(0,slider().getProgress());
        });
    }

    public void testTouchScrubPausesSearchUntilReleaseAndDoesNotSwipeToLibrary()throws Exception {
        main(()->{study.edit().putLong("replayDelayMillis",60000).commit();call("analyze");call("toggleAutoplay");});
        final Rect[] bounds={null};final int[] padding={0};
        main(()->{SeekBar slider=slider();slider.requestRectangleOnScreen(new Rect(0,0,slider.getWidth(),slider.getHeight()),true);});getInstrumentation().waitForIdleSync();
        main(()->{bounds[0]=new Rect();assertTrue(slider().getGlobalVisibleRect(bounds[0]));padding[0]=slider().getPaddingLeft();});
        float start=bounds[0].left+padding[0]+1,y=bounds[0].exactCenterY(),end=bounds[0].right-sliderPaddingRight()-1;
        long down=SystemClock.uptimeMillis();send(down,MotionEvent.ACTION_DOWN,start,y);send(down,MotionEvent.ACTION_MOVE,start+(end-start)*.35f,y);
        await(()->Boolean.TRUE.equals(get("seeking")),3000,"Dragging did not enter scrubbing mode");
        main(()->{assertTrue((Boolean)get("analysisEnabled"));assertNull("Scrubbing pauses the old search",get("analysisFuture"));assertFalse("Scrubbing pauses autoplay",(Boolean)get("autoplay"));assertFalse("Horizontal slider drag must not open the library",(Boolean)get("library"));});
        send(down,MotionEvent.ACTION_MOVE,start+(end-start)*.7f,y);
        final int[] generation={0};main(()->{assertTrue(ply()>0);assertEquals(ply(),slider().getProgress());generation[0]=(Integer)get("analysisGeneration");call("resumeAnalysis");assertNull(get("analysisFuture"));assertEquals(generation[0],((Integer)get("analysisGeneration")).intValue());});
        send(down,MotionEvent.ACTION_UP,start+(end-start)*.7f,y);
        await(()->Boolean.FALSE.equals(get("seeking"))&&((Integer)get("analysisGeneration"))>generation[0],3000,"Release did not resume enabled analysis");
        main(()->{assertEquals("Release starts exactly one new position search",generation[0]+1,((Integer)get("analysisGeneration")).intValue());assertTrue((Boolean)get("analysisEnabled"));assertFalse((Boolean)get("library"));assertEquals(ply(),slider().getProgress());call("stopSearch");});
    }

    private void setAccessibleProgress(int progress){Bundle args=new Bundle();args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,progress);assertTrue("Accessibility progression must be accepted",slider().performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args));}
    private int sliderPaddingRight(){int[] padding={0};main(()->padding[0]=slider().getPaddingRight());return padding[0];}
    private void send(long down,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{assertTrue("Touch event must be injected",getInstrumentation().getUiAutomation().injectInputEvent(event,true));}finally{event.recycle();}getInstrumentation().waitForIdleSync();}
    private SeekBar slider(){return (SeekBar)get("replayProgress");}
    private int ply(){return (Integer)get("ply");}
    @SuppressWarnings("unchecked") private List<GameRepository.Game> games(){return (List<GameRepository.Game>)get("games");}
    @SuppressWarnings("unchecked") private List<String> moves(){return (List<String>)get("workingMoves");}
    private View root(){return activity.getWindow().getDecorView();}
    private void main(Runnable action){UiTestActions.runOnMain(getInstrumentation(),action);}
    private void await(BooleanSupplier condition,long timeout,String message)throws Exception {long until=SystemClock.uptimeMillis()+timeout;boolean[] ready={false};while(SystemClock.uptimeMillis()<until){main(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(40);}fail(message);}
    private Object get(String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}catch(Exception error){throw new RuntimeException(error);}}
    private void set(String name,Object value){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}catch(Exception error){throw new RuntimeException(error);}}
    private Object call(String name){return call(name,new Class<?>[0]);}
    private Object call(String name,Class<?>[] types,Object... values){try{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,values);}catch(Exception error){throw new RuntimeException(error);}}
    private static View findDescription(View view,String description){if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),description);if(found!=null)return found;}}return null;}
    private static void restore(SharedPreferences preferences,Map<String,?> values){SharedPreferences.Editor edit=preferences.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){Object value=item.getValue();String key=item.getKey();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> set=(Set<String>)value;edit.putStringSet(key,set);}}edit.commit();}
}
