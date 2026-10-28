package org.xiangqi.study;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import org.xiangqi.study.core.Position;
import java.util.*;

/** A resolution-independent Xiangqi board using intersection coordinates. */
public final class BoardView extends View {
    public interface MoveListener { void onMove(String move); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fontMetrics=new Paint.FontMetrics();
    private Position position = Position.initial();
    private boolean flipped, editing;
    private int selected = -1;
    private String lastMove = "", hintMove = "";
    private List<String> legal = new ArrayList<>();
    private MoveListener listener;
    private float step, left, top, radius;
    private final int bronze=Color.rgb(159,144,101), cream=Color.rgb(232,222,187), red=Color.rgb(178,57,43), black=Color.rgb(39,68,64);
    public BoardView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE,null); setFocusable(true); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }
    public void setMoveListener(MoveListener listener) { this.listener=listener; }
    public void setPosition(Position p,String last,boolean editing) {
        this.position=p; this.lastMove=last==null?"":last; this.editing=editing; selected=-1; hintMove="";
        legal=editing?p.legalMoves():Collections.emptyList();
        setContentDescription("Xiangqi board. " + (p.redToMove?"Red":"Black") + " to move. " + (editing?"Explore mode. Tap a piece, then a legal destination. Use the legal move list for accessible input.":"Replay mode. Use the move navigation controls.")); invalidate();
    }
    public void setFlipped(boolean flipped) { this.flipped=flipped; invalidate(); }
    public void setHint(String move) { hintMove=move==null?"":move; invalidate(); }
    @Override protected void onMeasure(int widthSpec,int heightSpec) { int w=MeasureSpec.getSize(widthSpec); setMeasuredDimension(w,Math.round(w*1.10f)); }
    private float x(int index) { int file=index%9; return left+(flipped?8-file:file)*step; }
    private float y(int index) { int rank=index/9; return top+(flipped?9-rank:rank)*step; }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); float w=getWidth(), h=getHeight();
        float density=getResources().getDisplayMetrics().density,margin=8*density;
        // The outer intersections need half a cell beyond the grid for the discs
        // and selection rings. Fixed pixel insets clipped them on dense displays.
        step=Math.min((w-2*margin)/9,(h-2*margin)/10);
        left=(w-step*8)/2; top=(h-step*9)/2; radius=step*0.405f;
        paint.setColor(Color.rgb(63,59,42)); c.drawRoundRect(0,0,w,h,14*density,14*density,paint);
        paint.setColor(bronze); paint.setStrokeWidth(Math.max(1,w/400));
        for(int row=0;row<10;row++) c.drawLine(left,top+row*step,left+8*step,top+row*step,paint);
        for(int col=0;col<9;col++) {
            float xx=left+col*step;
            if(col==0||col==8) c.drawLine(xx,top,xx,top+9*step,paint);
            else { c.drawLine(xx,top,xx,top+4*step,paint); c.drawLine(xx,top+5*step,xx,top+9*step,paint); }
        }
        c.drawLine(left+3*step,top,left+5*step,top+2*step,paint); c.drawLine(left+5*step,top,left+3*step,top+2*step,paint);
        c.drawLine(left+3*step,top+7*step,left+5*step,top+9*step,paint); c.drawLine(left+5*step,top+7*step,left+3*step,top+9*step,paint);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.create("serif",Typeface.NORMAL)); paint.setTextSize(step*.34f);
        c.drawText("楚 河",left+2*step,top+4.63f*step,paint); c.drawText("漢 界",left+6*step,top+4.63f*step,paint);
        if(lastMove.length()==4) for(int i=0;i<4;i+=2) {
            int sq=Position.index(lastMove.substring(i,i+2)); paint.setColor(Color.argb(95,172,203,152));
            c.drawRoundRect(x(sq)-step*.46f,y(sq)-step*.46f,x(sq)+step*.46f,y(sq)+step*.46f,8,8,paint);
        }
        for(int i=0;i<90;i++) if(position.board[i]!=0 && position.board[i]!='.') {
            paint.setColor(cream); c.drawCircle(x(i),y(i),radius,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(step*.026f); paint.setColor(Character.isUpperCase(position.board[i])?red:black);
            c.drawCircle(x(i),y(i),radius*.88f,paint); paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(step*.60f); paint.setTypeface(Typeface.create("serif",Typeface.NORMAL));
            paint.getFontMetrics(fontMetrics); c.drawText(glyph(position.board[i]),x(i),y(i)-(fontMetrics.ascent+fontMetrics.descent)/2,paint);
            if(i==selected) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(step*.075f); paint.setColor(Color.rgb(183,211,168)); c.drawCircle(x(i),y(i),radius*1.12f,paint); paint.setStyle(Paint.Style.FILL); }
        }
        if(selected>=0) for(String move:legal) if(Position.index(move.substring(0,2))==selected) {
            int dest=Position.index(move.substring(2)); paint.setColor(Color.argb(175,182,213,170)); c.drawCircle(x(dest),y(dest),step*.12f,paint);
        }
        if(hintMove.length()==4) {
            int from=Position.index(hintMove.substring(0,2)),to=Position.index(hintMove.substring(2));
            paint.setColor(Color.argb(200,177,211,164)); paint.setStrokeWidth(step*.12f); paint.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(x(from),y(from),x(to),y(to),paint);
            double a=Math.atan2(y(to)-y(from),x(to)-x(from));
            c.drawLine(x(to),y(to),x(to)-(float)Math.cos(a-.55)*step*.42f,y(to)-(float)Math.sin(a-.55)*step*.42f,paint);
            c.drawLine(x(to),y(to),x(to)-(float)Math.cos(a+.55)*step*.42f,y(to)-(float)Math.sin(a+.55)*step*.42f,paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(!editing) return super.onTouchEvent(event);
        if(event.getAction()==MotionEvent.ACTION_DOWN) return true;
        if(event.getAction()!=MotionEvent.ACTION_UP) return true;
        performClick();
        int file=Math.round((event.getX()-left)/step),rank=Math.round((event.getY()-top)/step);
        if(file<0||file>8||rank<0||rank>9) return true;
        int sq=(flipped?9-rank:rank)*9+(flipped?8-file:file);
        if(selected>=0) {
            String move=Position.square(selected)+Position.square(sq);
            if(legal.contains(move)) { selected=-1; if(listener!=null) listener.onMove(move); return true; }
        }
        char piece=position.board[sq]; selected=piece!=0 && piece!='.' && Character.isUpperCase(piece)==position.redToMove?sq:-1;
        invalidate(); return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    private static String glyph(char p) {
        switch(p) { case 'K':return "帥";case 'k':return "將";case 'A':return "仕";case 'a':return "士";case 'B':return "相";case 'b':return "象";case 'N':case 'n':return "馬";case 'R':case 'r':return "車";case 'C':case 'c':return "炮";case 'P':return "兵";case 'p':return "卒";default:return "?"; }
    }
}
