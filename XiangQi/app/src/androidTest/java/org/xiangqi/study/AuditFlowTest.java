package org.xiangqi.study;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Map;
import java.util.Set;
import java.io.File;
import java.nio.file.Files;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** End-user flows beyond the initial smoke gate. No imported library data is erased. */
public final class AuditFlowTest extends InstrumentationTestCase {
    private Activity activity;
    private SharedPreferences preferences;
    private Map<String,?> savedPreferences;
    @Override protected void setUp() throws Exception {
        super.setUp();
        preferences=getInstrumentation().getTargetContext().getSharedPreferences("study",0);
        savedPreferences=preferences.getAll(); preferences.edit().clear().commit();
        activity=launch();
    }
    @Override protected void tearDown() throws Exception {
        if(activity!=null&&!activity.isDestroyed())main(()->activity.finish());
        getInstrumentation().waitForIdleSync();
        long stoppedBy=SystemClock.uptimeMillis()+5000;
        while(activity!=null&&!activity.isDestroyed()&&SystemClock.uptimeMillis()<stoppedBy)Thread.sleep(50);
        SharedPreferences.Editor edit=preferences.edit().clear();
        for(Map.Entry<String,?> entry:savedPreferences.entrySet()) {
            String key=entry.getKey(); Object value=entry.getValue();
            if(value instanceof String)edit.putString(key,(String)value);
            else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
            else if(value instanceof Integer)edit.putInt(key,(Integer)value);
            else if(value instanceof Long)edit.putLong(key,(Long)value);
            else if(value instanceof Float)edit.putFloat(key,(Float)value);
            else if(value instanceof Set) { @SuppressWarnings("unchecked") Set<String> set=(Set<String>)value;edit.putStringSet(key,set); }
        }
        edit.commit(); super.tearDown();
    }

    public void testSearchNavigationAndAutoplay() throws Exception {
        for(String player:new String[]{"Lai Ly Huynh","Nguyen Thanh Bao","Andy Wo","吴宗翰"}) {
            search(player); await("9 of ",8000);
        }
        search("no such player audit 982374"); await("No matching games",8000);
        search("Yin Sheng");open("Yin Sheng");
        clickDescription("First position"); await("Starting position",3000);
        clickDescription("Last position"); await("Recorded game · 108 / 108",3000);
        clickDescription("Previous move"); await("Recorded game · 107 / 108",3000);
        clickDescription("First position"); clickDescription("Play replay");
        long until=SystemClock.uptimeMillis()+4500;
        while(progress()==0&&SystemClock.uptimeMillis()<until)Thread.sleep(100);
        assertTrue("Autoplay must advance",progress()>0);
        clickDescription("Pause replay"); int paused=progress(); Thread.sleep(1300);
        assertEquals("Pause must remove scheduled advancement",paused,progress());
        reveal(SeekBar.class);
        AccessibilityNodeInfo slider=null;long accessibilityBy=SystemClock.uptimeMillis()+5000;
        while(slider==null&&SystemClock.uptimeMillis()<accessibilityBy){
            slider=nodeDescription(getInstrumentation().getUiAutomation().getRootInActiveWindow(),"Game position, in half-moves");
            if(slider==null)Thread.sleep(100);
        }
        assertNotNull(slider);android.os.Bundle arguments=new android.os.Bundle();
        arguments.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,54);
        assertTrue("Accessible seek must navigate",slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),arguments));
        await("Recorded game · 54 / 108",3000);
        clickDescription("First position");await("Recorded game · 0 / 108",3000);
    }

    public void testVariationSurvivesSwitchingGamesAndRecreation() throws Exception {
        search("Yin Sheng");open("Yin Sheng");clickDescription("First position");
        click("Play from here");click("Legal moves…");clickDialog("b2–e2");
        assertBoardTurn("Black");await("Exploring a variation",3000);
        assertEquals(1,progress());
        recreate();await("Exploring a variation",8000);assertEquals(1,progress());
        click("‹  Library");search("Nguyen Thanh Bao");open("Nguyen Thanh Bao");
        clickDescription("First position");clickDescription("Next move");
        click("‹  Library");search("Yin Sheng");open("Yin Sheng");
        await("Exploring a variation",3000);assertEquals(1,progress());
        click("Return to recorded game");await("Recorded game · 1 / 108",3000);
        assertNull("Original mode must remove legal-move entry",findPrefix(root(),"Legal moves…"));
        recreate();await("Recorded game · 1 / 108",8000);
    }

    public void testCompletedAnalysisAndLifecycleCancellation() throws Exception {
        search("Yin Sheng");open("Yin Sheng");clickDescription("First position");
        click("Analyze");awaitContains("Depth ",12000);
        assertNotNull("Completed analysis must expose a legal suggestion",findPrefix(root(),"Explore suggested move"));
        click("Explore suggested move");assertBoardTurn("Black");await("Exploring a variation",3000);
        click("Return to recorded game");click("Analyze");
        recreate();await("Recorded game",8000);
        main(()->{try{Field field=MainActivity.class.getDeclaredField("analysisFuture");field.setAccessible(true);assertNull("Recreation must cancel the obsolete search",field.get(activity));}catch(Exception e){throw new RuntimeException(e);}});
        click("Analyze");click("‹  Library");await("Xiangqi Study",8000);
        Thread.sleep(3500);
        assertNotNull("Old engine callback must not replace library UI",findPrefix(root(),"Xiangqi Study"));
    }

    public void testSwipeReturnAndSliderGestureDoNotConflict() throws Exception {
        search("Yin Sheng");open("Yin Sheng");clickDescription("First position");
        clickDescription("Next move");clickDescription("Next move");assertEquals(2,progress());
        click("‹  Library");await("Return to current game",3000);
        swipe(false);await("MOVE RECORD",8000);assertEquals("Swipe must restore the current position",2,progress());
        swipe(true);await("Xiangqi Study",8000);
        click("Return to current game");await("MOVE RECORD",8000);assertEquals(2,progress());
        reveal(SeekBar.class);
        final int[] location=new int[2],size=new int[2];
        main(()->{View slider=findType(root(),SeekBar.class);slider.getLocationOnScreen(location);size[0]=slider.getWidth();size[1]=slider.getHeight();});
        gesture(location[0]+size[0]*.15f,location[1]+size[1]*.5f,location[0]+size[0]*.85f,location[1]+size[1]*.5f);
        assertNotNull("Scrubbing the slider must keep the replay open",findPrefix(root(),"MOVE RECORD"));
        assertTrue("The slider must still navigate",progress()>2);
    }

    public void testQueuedImportSurvivesActivityRecreation() throws Exception {
        File savedFile=new File(getInstrumentation().getTargetContext().getFilesDir(),"imported-games.json");
        byte[] backup=savedFile.isFile()?Files.readAllBytes(savedFile.toPath()):null;
        int initial=new GameRepository(getInstrumentation().getTargetContext()).load().size();
        Field field=MainActivity.class.getDeclaredField("FILE_IO");field.setAccessible(true);
        ExecutorService fileIo=(ExecutorService)field.get(null);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        fileIo.submit(()->{entered.countDown();try{release.await(15,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(3,TimeUnit.SECONDS));
        try {
            Method method=MainActivity.class.getDeclaredMethod("importText",String.class);method.setAccessible(true);
            main(()->{try{method.invoke(activity,"[Event \"Rotation import audit\"]\n[Red \"Rotation Audit Red\"]\n[Black \"Rotation Audit Black\"]\n[Result \"*\"]\n\n1. b2e2 b9c7 2. a0a1 *\n");}catch(Exception e){throw new RuntimeException(e);}});
            Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
            main(()->activity.recreate());Activity replacement=monitor.waitForActivityWithTimeout(8000);
            getInstrumentation().removeMonitor(monitor);assertNotNull(replacement);activity=replacement;
            release.countDown();await((initial+1)+" of ",12000);
            assertEquals(initial+1,new GameRepository(getInstrumentation().getTargetContext()).load().size());
            search("Rotation Audit Red");await("1 of ",5000);
        } finally {
            release.countDown();fileIo.submit(()->{}).get(15,TimeUnit.SECONDS);
            if(backup==null){if(savedFile.exists())assertTrue(savedFile.delete());}else Files.write(savedFile.toPath(),backup);
        }
    }

    private Activity launch() throws Exception {
        Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity launched=getInstrumentation().startActivitySync(intent);activity=launched;
        long until=SystemClock.uptimeMillis()+10000;final boolean[] ready={false};
        while(!ready[0]&&SystemClock.uptimeMillis()<until){main(()->ready[0]=findPrefix(root(),"Xiangqi Study")!=null||findPrefix(root(),"‹  Library")!=null);if(!ready[0])Thread.sleep(100);}
        assertTrue("Activity must restore its library or replay",ready[0]);
        main(()->{View back=findPrefix(root(),"‹  Library");if(back!=null)back.performClick();});
        await("Xiangqi Study",8000);return launched;
    }
    private void recreate() throws Exception {
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        main(()->activity.recreate());
        Activity replacement=monitor.waitForActivityWithTimeout(10000);getInstrumentation().removeMonitor(monitor);
        assertNotNull("Activity must recreate",replacement);activity=replacement;getInstrumentation().waitForIdleSync();
    }
    private void open(String player) throws Exception {
        main(()->{View text=findPrefix(root(),player);assertNotNull(text);((View)text.getParent().getParent()).performClick();});
        await("MOVE RECORD",10000);
    }
    private void search(String player) throws Exception {
        main(()->{EditText text=(EditText)findType(root(),EditText.class);assertNotNull(text);text.setText(player);});
        getInstrumentation().waitForIdleSync();
    }
    private int progress() throws Exception {final int[] value={-1};main(()->value[0]=((SeekBar)findType(root(),SeekBar.class)).getProgress());return value[0];}
    private void click(String text) throws Exception {
        if(text.equals("Analyze")){clickDescription("Game menu");clickDialog("Start analysis");return;}
        main(()->{View view=findPrefix(root(),text);assertNotNull("Missing control "+text,view);view.performClick();});getInstrumentation().waitForIdleSync();
    }
    private void clickDescription(String text)throws Exception{
        main(()->{
            if(text.equals("First position")||text.equals("Last position")){
                SeekBar slider=(SeekBar)findType(root(),SeekBar.class);android.os.Bundle args=new android.os.Bundle();
                args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,text.equals("First position")?0:slider.getMax());
                slider.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args);return;
            }
            View view=findDescription(root(),text);assertNotNull("Missing control "+text,view);view.performClick();
        });getInstrumentation().waitForIdleSync();
    }
    private void clickDialog(String text)throws Exception{
        assertTrue("Missing dialog option "+text,UiTestActions.clickPopup(getInstrumentation(),text));
    }
    private void await(String prefix,long timeout)throws Exception{awaitText(prefix,timeout,false);}
    private void awaitContains(String text,long timeout)throws Exception{awaitText(text,timeout,true);}
    private void awaitText(String text,long timeout,boolean contains)throws Exception{
        long until=SystemClock.uptimeMillis()+timeout;final boolean[] found={false};
        while(SystemClock.uptimeMillis()<until){main(()->found[0]=(contains?findContains(root(),text):findPrefix(root(),text))!=null);if(found[0])return;Thread.sleep(100);}fail("UI timed out waiting for "+text);
    }
    private void main(Runnable work){getInstrumentation().runOnMainSync(work);}
    private void assertBoardTurn(String side){main(()->assertTrue(findType(root(),BoardView.class).getContentDescription().toString().contains(side+" to move.")));}
    private void reveal(Class<?> type)throws Exception{main(()->{View view=findType(root(),type);assertNotNull(view);view.requestRectangleOnScreen(new android.graphics.Rect(0,0,view.getWidth(),view.getHeight()),true);});getInstrumentation().waitForIdleSync();}
    private void swipe(boolean right)throws Exception{
        final int[] dimensions=new int[2];main(()->{dimensions[0]=root().getWidth();dimensions[1]=root().getHeight();});
        float y=dimensions[1]*(right?.4f:.72f);
        gesture(dimensions[0]*(right?.15f:.85f),y,dimensions[0]*(right?.85f:.15f),y);
    }
    private void gesture(float x1,float y1,float x2,float y2)throws Exception{
        long down=SystemClock.uptimeMillis();
        send(down,MotionEvent.ACTION_DOWN,x1,y1);
        for(int step=1;step<=10;step++){SystemClock.sleep(20);float t=step/10f;send(down,MotionEvent.ACTION_MOVE,x1+(x2-x1)*t,y1+(y2-y1)*t);}
        send(down,MotionEvent.ACTION_UP,x2,y2);getInstrumentation().waitForIdleSync();
    }
    private void send(long down,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);try{getInstrumentation().sendPointerSync(event);}finally{event.recycle();}}
    private View root(){return activity.getWindow().getDecorView();}
    private static View findPrefix(View v,String text){return findText(v,text,false);}
    private static View findContains(View v,String text){return findText(v,text,true);}
    private static View findText(View v,String text,boolean contains){if(v instanceof TextView&&!(v instanceof EditText)){String value=((TextView)v).getText().toString();if(contains?value.contains(text):value.startsWith(text))return v;}if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){View hit=findText(group.getChildAt(i),text,contains);if(hit!=null)return hit;}}return null;}
    private static View findType(View v,Class<?> type){if(type.isInstance(v))return v;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){View hit=findType(group.getChildAt(i),type);if(hit!=null)return hit;}}return null;}
    private static View findDescription(View v,String text){if(text.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){View hit=findDescription(group.getChildAt(i),text);if(hit!=null)return hit;}}return null;}
    private static AccessibilityNodeInfo nodeText(AccessibilityNodeInfo node,String text){if(node==null)return null;if(text.contentEquals(node.getText()==null?"":node.getText()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo hit=nodeText(node.getChild(i),text);if(hit!=null)return hit;}return null;}
    private static AccessibilityNodeInfo nodeDescription(AccessibilityNodeInfo node,String text){if(node==null)return null;if(text.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo hit=nodeDescription(node.getChild(i),text);if(hit!=null)return hit;}return null;}
}
