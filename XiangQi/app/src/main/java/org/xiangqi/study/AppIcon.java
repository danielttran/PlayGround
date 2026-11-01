package org.xiangqi.study;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small native navigation/action icons, drawn without font-dependent symbols. */
final class AppIcon extends Drawable {
    private final String name;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private final int color,size;
    AppIcon(String name,int color,int size){this.name=name;this.color=color;this.size=size;}
    @Override public void draw(Canvas c){int save=c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);path.reset();
        switch(name){
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
    @Override public void setAlpha(int alpha){p.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
