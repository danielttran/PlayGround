package org.xiangqi.study;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small native navigation/action icons, drawn without font-dependent symbols. */
final class AppIcon extends Drawable {
    private final String name;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private final int color,size;private int alpha=255;private boolean enabled=true;
    AppIcon(String name,int color,int size){this.name=name;this.color=color;this.size=size;}
    @Override public void draw(Canvas c){int save=c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);p.setColor(color);p.setAlpha(enabled?alpha:Math.round(alpha*.4f));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);path.reset();
        switch(name){
            case "back":case "left":path.moveTo(15,5);path.lineTo(8,12);path.lineTo(15,19);c.drawPath(path,p);break;
            case "right":path.moveTo(9,5);path.lineTo(16,12);path.lineTo(9,19);c.drawPath(path,p);break;
            case "menu":p.setStyle(Paint.Style.FILL);for(int y:new int[]{5,12,19})c.drawCircle(12,y,1.6f,p);break;
            case "pause":c.drawLine(8,5,8,19,p);c.drawLine(16,5,16,19,p);break;
            case "previous-game":case "next-game":if(name.equals("next-game")){c.translate(24,0);c.scale(-1,1);}c.drawLine(5,5,5,19,p);path.moveTo(19,5);path.lineTo(8,12);path.lineTo(19,19);path.close();c.drawPath(path,p);break;
            case "cpu":c.drawRoundRect(6,6,18,18,2,2,p);c.drawRect(9,9,15,15,p);for(int n:new int[]{9,15}){c.drawLine(n,2,n,5,p);c.drawLine(n,19,n,22,p);c.drawLine(2,n,5,n,p);c.drawLine(19,n,22,n,p);}break;
            case "palette":c.drawCircle(12,12,9,p);for(int[] at:new int[][]{{8,8},{14,7},{17,12},{9,16}})c.drawCircle(at[0],at[1],1,p);break;
            case "grid":c.drawRect(4,4,20,20,p);for(int n:new int[]{9,15}){c.drawLine(4,n,20,n,p);c.drawLine(n,4,n,20,p);}break;
            case "timer":c.drawCircle(12,13,8,p);c.drawLine(9,2,15,2,p);c.drawLine(12,8,12,13,p);c.drawLine(12,13,16,15,p);break;
            case "eye":path.moveTo(2,12);path.quadTo(12,0,22,12);path.quadTo(12,24,2,12);c.drawPath(path,p);c.drawCircle(12,12,3,p);c.drawLine(3,3,21,21,p);break;
            case "flip":c.drawLine(4,4,20,4,p);c.drawLine(4,20,20,20,p);c.drawLine(12,7,12,17,p);path.moveTo(8,10);path.lineTo(12,6);path.lineTo(16,10);path.moveTo(8,14);path.lineTo(12,18);path.lineTo(16,14);c.drawPath(path,p);break;
            case "arrow":c.drawLine(4,12,20,12,p);path.moveTo(15,7);path.lineTo(20,12);path.lineTo(15,17);c.drawPath(path,p);break;
            case "play":path.moveTo(8,5);path.lineTo(19,12);path.lineTo(8,19);path.close();c.drawPath(path,p);break;
            case "database":for(int x:new int[]{4,10,16})c.drawRoundRect(x,4,x+4,20,1,1,p);break;
            case "options":for(int y:new int[]{6,12,18})c.drawLine(3,y,21,y,p);for(int[] v:new int[][]{{8,6},{16,12},{10,18}}){p.setStyle(Paint.Style.FILL);c.drawCircle(v[0],v[1],3,p);p.setStyle(Paint.Style.STROKE);}break;
            case "undo":path.moveTo(8,5);path.lineTo(3,10);path.lineTo(8,15);c.drawPath(path,p);path.reset();path.moveTo(3,10);path.lineTo(14,10);path.cubicTo(22,10,22,21,14,21);c.drawPath(path,p);break;
            case "surrender":c.drawLine(5,3,5,21,p);path.moveTo(5,4);path.lineTo(20,4);path.lineTo(17,9);path.lineTo(20,14);path.lineTo(5,14);c.drawPath(path,p);break;
            case "hint":c.drawCircle(12,9,6,p);c.drawLine(9,15,9,19,p);c.drawLine(15,15,15,19,p);c.drawLine(9,19,15,19,p);c.drawLine(10,22,14,22,p);break;
            case "delete":c.drawLine(5,6,19,6,p);c.drawLine(9,3,15,3,p);c.drawRect(7,7,17,21,p);c.drawLine(10,10,10,18,p);c.drawLine(14,10,14,18,p);break;
            case "import":c.drawRoundRect(4,3,17,21,2,2,p);c.drawLine(11,12,22,12,p);c.drawLine(18,8,22,12,p);c.drawLine(18,16,22,12,p);break;
            default:c.drawCircle(12,12,7,p);break;
        }c.restoreToCount(save);
    }
    @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
    @Override public boolean isStateful(){return true;}@Override protected boolean onStateChange(int[] state){boolean next=false;for(int value:state)if(value==android.R.attr.state_enabled)next=true;if(next==enabled)return false;enabled=next;invalidateSelf();return true;}
    @Override public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}@Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
