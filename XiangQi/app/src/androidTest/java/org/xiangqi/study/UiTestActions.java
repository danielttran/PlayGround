package org.xiangqi.study;

import android.app.Instrumentation;
import android.app.Activity;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.View;
import android.view.ViewGroup;

/** Touch real framework popup/dialog rows using freshly observed screen bounds. */
public final class UiTestActions {
    private UiTestActions() {}

    /** Assertions belong to the instrumentation thread, so JUnit can run cleanup. */
    public static void runOnMain(Instrumentation instrumentation,Runnable task) {
        final Throwable[] failure={null};
        instrumentation.runOnMainSync(()->{try{task.run();}catch(Throwable error){failure[0]=error;}});
        if(failure[0] instanceof Error)throw (Error)failure[0];
        if(failure[0] instanceof RuntimeException)throw (RuntimeException)failure[0];
        if(failure[0]!=null)throw new RuntimeException(failure[0]);
    }

    /** UI idle does not imply that a debounced background library query has finished. */
    public static void awaitLibrarySearch(Instrumentation instrumentation,Activity activity)throws Exception {
        long deadline=SystemClock.uptimeMillis()+15000;boolean[] ready={false};
        java.lang.reflect.Field pending=MainActivity.class.getDeclaredField("librarySearchPending");pending.setAccessible(true);
        java.lang.reflect.Method current=MainActivity.class.getDeclaredMethod("preparedLibraryCurrent");current.setAccessible(true);
        while(SystemClock.uptimeMillis()<deadline){
            runOnMain(instrumentation,()->{try{ready[0]=!activity.isDestroyed()&&!pending.getBoolean(activity)&&(Boolean)current.invoke(activity);}catch(Exception error){throw new RuntimeException(error);}});
            if(ready[0]){instrumentation.waitForIdleSync();return;}Thread.sleep(25);
        }
        throw new AssertionError("The latest background library query did not complete");
    }

    public static boolean clickPopup(Instrumentation instrumentation,String text)throws Exception {
        UiAutomation automation=instrumentation.getUiAutomation();
        long deadline=SystemClock.uptimeMillis()+8000,stableSince=0;
        Rect previousBounds=null,attemptedBounds=null;int previousWindow=-1,attemptedWindow=-1;
        instrumentation.waitForIdleSync();
        while(SystemClock.uptimeMillis()<deadline){
            AccessibilityNodeInfo node=findText(automation.getRootInActiveWindow(),text);
            if(node!=null&&node.refresh()&&node.isVisibleToUser()&&node.isEnabled()){
                Rect bounds=new Rect();node.getBoundsInScreen(bounds);
                if(!bounds.isEmpty()){
                    int window=node.getWindowId();long now=SystemClock.uptimeMillis();
                    if(attemptedBounds!=null&&window!=attemptedWindow){instrumentation.waitForIdleSync();return true;}
                    if(window!=previousWindow||!bounds.equals(previousBounds)){
                        previousWindow=window;previousBounds=new Rect(bounds);stableSince=now;
                    }
                    // Popup animations can expose accessibility rows before
                    // their touch targets are ready. Observe a stable target.
                    if(now-stableSince<200){Thread.sleep(50);continue;}
                    float x=bounds.exactCenterX(),y=bounds.exactCenterY();
                    long downTime=SystemClock.uptimeMillis();
                    MotionEvent down=MotionEvent.obtain(downTime,downTime,MotionEvent.ACTION_DOWN,x,y,0);
                    down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                    boolean accepted;
                    try{accepted=automation.injectInputEvent(down,true);}finally{down.recycle();}
                    SystemClock.sleep(40);
                    MotionEvent up=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);
                    up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                    try{accepted=automation.injectInputEvent(up,true)&&accepted;}finally{up.recycle();}
                    attemptedWindow=window;attemptedBounds=new Rect(bounds);
                    if(accepted){
                        // Input acceptance is not evidence that the menu action
                        // ran. Require the original popup to close/transition.
                        long changedBy=Math.min(deadline,SystemClock.uptimeMillis()+4000),changedSince=0;
                        while(SystemClock.uptimeMillis()<changedBy){
                            AccessibilityNodeInfo root=automation.getRootInActiveWindow();
                            if(root!=null)root.refresh(); // Best effort: API 31 may reject a valid new root's refresh.
                            boolean changed=root!=null&&(root.getWindowId()!=window||!sameVisibleTarget(root,text,window,bounds));
                            if(changed){
                                if(changedSince==0)changedSince=SystemClock.uptimeMillis();
                                if(SystemClock.uptimeMillis()-changedSince>=100){instrumentation.waitForIdleSync();return true;}
                            }else changedSince=0;
                            Thread.sleep(50);
                        }
                    }
                    // Retry only while the original popup is still present;
                    // never tap through a newly opened dialog or the activity.
                    // A final confirmed transition is success even when the
                    // settling interval crossed the confirmation deadline.
                    if(transitioned(automation,text,window,bounds)){instrumentation.waitForIdleSync();return true;}
                    stableSince=SystemClock.uptimeMillis();
                }
            }else{previousBounds=null;previousWindow=-1;stableSince=0;}
            Thread.sleep(50);
        }
        if(attemptedBounds!=null&&transitioned(automation,text,attemptedWindow,attemptedBounds)){instrumentation.waitForIdleSync();return true;}
        diagnose(automation,text,previousWindow,previousBounds);return false;
    }

    private static boolean transitioned(UiAutomation automation,String text,int window,Rect bounds){
        AccessibilityNodeInfo root=automation.getRootInActiveWindow();
        if(root==null)return false;root.refresh(); // A failed refresh must not erase an observed transition.
        return root.getWindowId()!=window||!sameVisibleTarget(root,text,window,bounds);
    }

    /** A recycled window ID or a same-titled new dialog is not the old menu row. */
    private static boolean sameVisibleTarget(AccessibilityNodeInfo root,String text,int window,Rect bounds){
        if(root==null)return false;
        if(text.equalsIgnoreCase(root.getText()==null?"":root.getText().toString())&&root.refresh()&&root.isVisibleToUser()&&root.getWindowId()==window){
            Rect actual=new Rect();root.getBoundsInScreen(actual);if(bounds.equals(actual))return true;
        }
        for(int i=0;i<root.getChildCount();i++)if(sameVisibleTarget(root.getChild(i),text,window,bounds))return true;
        return false;
    }
    private static void diagnose(UiAutomation automation,String expected,int oldWindow,Rect oldBounds){
        AccessibilityNodeInfo root=automation.getRootInActiveWindow(),target=findText(root,expected);Rect bounds=new Rect();if(target!=null)target.getBoundsInScreen(bounds);
        StringBuilder summary=new StringBuilder();summarize(root,summary);
        Log.w("XiangqiUiTest","Popup action not confirmed: expected="+expected+", previousWindow="+oldWindow+", actualWindow="+(root==null?-1:root.getWindowId())+", previousBounds="+oldBounds+", target="+(target==null?"missing":target.getClassName()+" visible="+target.isVisibleToUser()+" bounds="+bounds)+", root="+(root==null?"none":root.getClassName())+", text="+summary);
    }
    private static void summarize(AccessibilityNodeInfo node,StringBuilder output){
        if(node==null||output.length()>=900)return;CharSequence value=node.getText();
        if(value!=null&&value.length()>0){if(output.length()>0){if(output.length()>896)return;output.append(" | ");}String text=value.toString();output.append(text,0,Math.min(text.length(),900-output.length()));}
        for(int i=0;i<node.getChildCount()&&output.length()<900;i++)summarize(node.getChild(i),output);
    }

    /** Wait for the tested Activity and a visible, measured anchor before opening. */
    public static void openGameMenu(Instrumentation instrumentation,Activity activity)throws Exception{
        long deadline=SystemClock.uptimeMillis()+8000;boolean[] ready={false};
        while(SystemClock.uptimeMillis()<deadline){
            runOnMain(instrumentation,()->{
                View menu=findDescription(activity.getWindow().getDecorView(),"Game menu");
                ready[0]=activity.hasWindowFocus()&&menu!=null&&menu.isShown()&&menu.getWindowToken()!=null&&menu.getWidth()>0&&menu.getHeight()>0;
                if(ready[0])menu.requestRectangleOnScreen(new Rect(0,0,menu.getWidth(),menu.getHeight()),true);
            });
            if(ready[0]){
                instrumentation.waitForIdleSync();
                runOnMain(instrumentation,()->{
                    View menu=findDescription(activity.getWindow().getDecorView(),"Game menu");Rect visible=new Rect();
                    ready[0]=activity.hasWindowFocus()&&menu!=null&&menu.isShown()&&menu.getGlobalVisibleRect(visible)&&visible.width()>0&&visible.height()>0;
                    if(ready[0])menu.performClick();
                });
                if(ready[0]){instrumentation.waitForIdleSync();return;}
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Game menu anchor did not become visible and focused");
    }

    /** Exercise the user-visible replacement for the removed position slider. */
    public static void goToMove(Instrumentation instrumentation,Activity activity,int halfMove)throws Exception {
        openGameMenu(instrumentation,activity);
        if(!clickPopup(instrumentation,"Go to move…"))throw new AssertionError("Go to move menu action did not run");
        long deadline=SystemClock.uptimeMillis()+5000;boolean ready=false;String value=String.valueOf(halfMove);
        while(SystemClock.uptimeMillis()<deadline){
            AccessibilityNodeInfo input=findDescription(instrumentation.getUiAutomation().getRootInActiveWindow(),"Half-move number");
            if(input!=null){
                if(value.contentEquals(input.getText()==null?"":input.getText())){ready=true;break;}
                android.os.Bundle arguments=new android.os.Bundle();arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
                input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arguments);
            }
            Thread.sleep(50);
        }
        if(!ready)throw new AssertionError("Go to position input did not accept the half-move number");
        if(!clickPopup(instrumentation,"Go"))throw new AssertionError("Go button did not close the position dialog");
    }

    private static View findDescription(View view,String text){
        if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }

    private static AccessibilityNodeInfo findText(AccessibilityNodeInfo node,String text){
        if(node==null)return null;
        if(text.equalsIgnoreCase(node.getText()==null?"":node.getText().toString()))return node;
        for(int index=0;index<node.getChildCount();index++){
            AccessibilityNodeInfo found=findText(node.getChild(index),text);
            if(found!=null)return found;
        }
        return null;
    }
    private static AccessibilityNodeInfo findDescription(AccessibilityNodeInfo node,String text){
        if(node==null)return null;if(text.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;
        for(int index=0;index<node.getChildCount();index++){AccessibilityNodeInfo found=findDescription(node.getChild(index),text);if(found!=null)return found;}
        return null;
    }
}
