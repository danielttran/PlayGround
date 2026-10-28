package org.xiangqi.study;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Touch real framework popup/dialog rows using freshly observed screen bounds. */
public final class UiTestActions {
    private UiTestActions() {}

    public static boolean clickPopup(Instrumentation instrumentation,String text)throws Exception {
        UiAutomation automation=instrumentation.getUiAutomation();
        long deadline=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<deadline){
            AccessibilityNodeInfo node=findText(automation.getRootInActiveWindow(),text);
            if(node!=null&&node.isVisibleToUser()){
                Rect bounds=new Rect();node.getBoundsInScreen(bounds);
                if(!bounds.isEmpty()){
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
                    if(accepted){instrumentation.waitForIdleSync();return true;}
                }
            }
            Thread.sleep(50);
        }
        return false;
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
}
