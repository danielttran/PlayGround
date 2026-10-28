package org.xiangqi.study;

import android.content.Context;
import android.graphics.*;
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
    private int bronze=0xff9f9065, boardColor=0xff3f3b2a, redDiscColor=0xffe8debb, blackDiscColor=0xffe8debb,
        red=0xffb2392b, black=0xff274440, moveArrow=0xffd7ae5c, bestArrow=0xffb3dea5;
    private int presetBoard=boardColor,presetRedDisc=redDiscColor,presetBlackDisc=blackDiscColor,
        presetGrid=bronze,presetRed=red,presetBlack=black,presetMove=moveArrow,presetBest=bestArrow;
    private Integer customBoard,customRedDisc,customBlackDisc;
    private boolean simplified;
    private String discStyle="cream";
    private final Typeface pieceTypeface=Typeface.create("serif",Typeface.NORMAL);
    private String coordinateStyle="none";
    private static final String[] FILE_LABELS={"a","b","c","d","e","f","g","h","i"};
    private static final String[] NUMBER_LABELS={"0","1","2","3","4","5","6","7","8","9"};
    public BoardView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE,null); setFocusable(true); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }
    public void setMoveListener(MoveListener listener) { this.listener=listener; }
    public void setAppearance(String boardStyle,String glyphStyle,String discStyle) {
        simplified="simplified".equals(glyphStyle);this.discStyle=discStyle==null?"cream":discStyle;
        if("wood".equals(boardStyle)) {
            palette(0xffd4b580,0xff785b36,0xffa02b21,0xff28332d,0xff9a541e,0xff1b784e);
        } else if("ink".equals(boardStyle)) {
            palette(0xffe8e6dc,0xff817c6c,0xffb32725,0xff253942,0xffb56a29,0xff266c8a);
        } else if("rosewood".equals(boardStyle)) {
            palette(0xff582e2a,0xffc99871,0xffa02b21,0xff28332d,0xffffc476,0xffa6ebbe);
        } else if("slate".equals(boardStyle)) {
            palette(0xff263843,0xffa4b7bd,0xffa02b21,0xff28332d,0xffffc27e,0xffb6edca);
        } else if("bamboo".equals(boardStyle)) {
            palette(0xffc1d29e,0xff627244,0xffa02b21,0xff28332d,0xff9d4b25,0xff17634c);
        } else {
            palette(0xff3f3b2a,0xff9f9065,0xffb2392b,0xff274440,0xffd7ae5c,0xffb3dea5);
        }
        if("wood".equals(discStyle))presetRedDisc=presetBlackDisc=0xffe1be80;
        else if("jade".equals(discStyle))presetRedDisc=presetBlackDisc=0xffc2d9c4;
        else if("porcelain".equals(discStyle))presetRedDisc=presetBlackDisc=0xfff6f2e9;
        else if("rosewood".equals(discStyle)){presetRedDisc=0xff6d2d29;presetBlackDisc=0xff382b27;}
        else if("obsidian".equals(discStyle)){presetRedDisc=0xff323038;presetBlackDisc=0xff171e25;}
        else presetRedDisc=presetBlackDisc=0xffe8debb;
        applyColors();
    }
    private void palette(int board,int grid,int redInk,int blackInk,int played,int best){presetBoard=board;presetGrid=grid;presetRed=redInk;presetBlack=blackInk;presetMove=played;presetBest=best;}
    /** Null resets only that surface to its selected preset; picker input is opaque RGB. */
    public void setCustomColors(Integer board,Integer redBackground,Integer blackBackground){customBoard=board;customRedDisc=redBackground;customBlackDisc=blackBackground;applyColors();}
    public int getBoardColor(){return boardColor;}
    public int getPieceBackground(boolean redSide){return redSide?redDiscColor:blackDiscColor;}
    private void applyColors(){
        boardColor=opaque(customBoard==null?presetBoard:customBoard);
        redDiscColor=opaque(customRedDisc==null?presetRedDisc:customRedDisc);
        blackDiscColor=opaque(customBlackDisc==null?presetBlackDisc:customBlackDisc);
        bronze=customBoard==null?presetGrid:readableMonochrome(boardColor);
        red=readableInk(redDiscColor,presetRed,true);black=readableInk(blackDiscColor,presetBlack,false);
        moveArrow=customBoard==null?presetMove:readableInk(boardColor,0xffa3521d,true);
        bestArrow=customBoard==null?presetBest:readableInk(boardColor,0xff167249,false);
        invalidate();
    }
    private static int opaque(int color){return color|0xff000000;}
    private static double channel(int value){double s=value/255.0;return s<=.04045?s/12.92:Math.pow((s+.055)/1.055,2.4);}
    private static double luminance(int color){return .2126*channel(Color.red(color))+.7152*channel(Color.green(color))+.0722*channel(Color.blue(color));}
    private static double contrast(int a,int b){double first=luminance(a),second=luminance(b);return (Math.max(first,second)+.05)/(Math.min(first,second)+.05);}
    private static int readableMonochrome(int background){return contrast(background,Color.BLACK)>=contrast(background,Color.WHITE)?Color.BLACK:Color.WHITE;}
    private static int readableInk(int background,int preferred,boolean redSide){
        if(contrast(background,preferred)>=4.5)return preferred;
        int light=redSide?0xffffb8aa:0xffedf5f2,dark=redSide?0xff460000:0xff10221c;
        if(contrast(background,light)>=4.5)return light;
        if(contrast(background,dark)>=4.5)return dark;
        return readableMonochrome(background);
    }
    public void setPosition(Position p,String last,boolean editing) {
        this.position=p; this.lastMove=last==null?"":last; this.editing=editing; selected=-1; hintMove="";
        legal=editing?p.legalMoves():Collections.emptyList();
        setContentDescription("Xiangqi board. " + (p.redToMove?"Red":"Black") + " to move. " + (editing?"Explore mode. Tap a piece, then a legal destination. Use the legal move list for accessible input.":"Replay mode. Use the move navigation controls.")); invalidate();
    }
    public void setFlipped(boolean flipped) { this.flipped=flipped; invalidate(); }
    public void setCoordinates(String style) { coordinateStyle="iccs".equals(style)||"numeric".equals(style)?style:"none";invalidate(); }
    public void setHint(String move) { hintMove=move==null?"":move; invalidate(); }
    @Override protected void onMeasure(int widthSpec,int heightSpec) { int w=MeasureSpec.getSize(widthSpec); setMeasuredDimension(w,Math.round(w*1.10f)); }
    private float x(int index) { int file=index%9; return left+(flipped?8-file:file)*step; }
    private float y(int index) { int rank=index/9; return top+(flipped?9-rank:rank)*step; }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); float w=getWidth(), h=getHeight();
        // Coordinates share the existing rim. Turning them on never shrinks the
        // grid or discs, and touch targets remain on the same intersections.
        float density=getResources().getDisplayMetrics().density,margin=8*density;
        // The outer intersections need half a cell beyond the grid for the discs
        // and selection rings. Fixed pixel insets clipped them on dense displays.
        step=Math.min((w-2*margin)/9,(h-2*margin)/10);
        left=(w-step*8)/2; top=(h-step*9)/2; radius=step*0.405f;
        paint.setColor(boardColor); c.drawRoundRect(0,0,w,h,14*density,14*density,paint);
        paint.setColor(bronze); paint.setStrokeWidth(Math.max(1,w/400));
        for(int row=0;row<10;row++) c.drawLine(left,top+row*step,left+8*step,top+row*step,paint);
        for(int col=0;col<9;col++) {
            float xx=left+col*step;
            if(col==0||col==8) c.drawLine(xx,top,xx,top+9*step,paint);
            else { c.drawLine(xx,top,xx,top+4*step,paint); c.drawLine(xx,top+5*step,xx,top+9*step,paint); }
        }
        c.drawLine(left+3*step,top,left+5*step,top+2*step,paint); c.drawLine(left+5*step,top,left+3*step,top+2*step,paint);
        c.drawLine(left+3*step,top+7*step,left+5*step,top+9*step,paint); c.drawLine(left+5*step,top+7*step,left+3*step,top+9*step,paint);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(pieceTypeface); paint.setTextSize(step*.34f);
        c.drawText("楚 河",left+2*step,top+4.63f*step,paint); c.drawText("漢 界",left+6*step,top+4.63f*step,paint);
        if(!"none".equals(coordinateStyle))drawCoordinates(c,density);
        // The played move is a directional arrow, never source/destination tiles.
        drawArrow(c,lastMove,moveArrow,.075f,.27f,false);
        for(int i=0;i<90;i++) if(position.board[i]!=0 && position.board[i]!='.') {
            int fill=Character.isUpperCase(position.board[i])?redDiscColor:blackDiscColor;
            paint.setColor(fill); c.drawCircle(x(i),y(i),radius,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(step*.026f); paint.setColor(Character.isUpperCase(position.board[i])?red:black);
            c.drawCircle(x(i),y(i),radius*.88f,paint); paint.setStyle(Paint.Style.FILL);
            if("wood".equals(discStyle)||"rosewood".equals(discStyle)){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(step*.012f);c.drawCircle(x(i),y(i),radius*.76f,paint);paint.setStyle(Paint.Style.FILL);}
            if("porcelain".equals(discStyle)||"obsidian".equals(discStyle)){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(step*.018f);paint.setColor(readableMonochrome(fill));paint.setAlpha(70);
                c.drawArc(x(i)-radius*.96f,y(i)-radius*.96f,x(i)+radius*.96f,y(i)+radius*.96f,205,105,false,paint);
                paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setColor(Character.isUpperCase(position.board[i])?red:black);
            }
            paint.setTextSize(step*.60f); paint.setTypeface(pieceTypeface);
            paint.getFontMetrics(fontMetrics); c.drawText(glyph(position.board[i]),x(i),y(i)-(fontMetrics.ascent+fontMetrics.descent)/2,paint);
            if(i==selected) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(step*.075f); paint.setColor(Color.rgb(183,211,168)); c.drawCircle(x(i),y(i),radius*1.12f,paint); paint.setStyle(Paint.Style.FILL); }
        }
        if(selected>=0) for(String move:legal) if(Position.index(move.substring(0,2))==selected) {
            int dest=Position.index(move.substring(2)); paint.setColor(Color.argb(175,182,213,170)); c.drawCircle(x(dest),y(dest),step*.12f,paint);
        }
        drawArrow(c,hintMove,bestArrow,.115f,.32f,true);
        drawTurnIndicator(c,density);
    }
    private void drawCoordinates(Canvas canvas,float density) {
        float horizontalRim=left-radius,verticalRim=top-radius;
        paint.setTypeface(Typeface.SANS_SERIF);paint.setTextSize(Math.min(10*density,Math.min(horizontalRim,verticalRim)*.72f));
        paint.setTextAlign(Paint.Align.CENTER);paint.setColor(bronze);paint.getFontMetrics(fontMetrics);
        float baseline=-(fontMetrics.ascent+fontMetrics.descent)/2;
        for(int file=0;file<9;file++) {
            int logical=flipped?8-file:file;
            String label="numeric".equals(coordinateStyle)?NUMBER_LABELS[logical]:FILE_LABELS[logical];
            float xx=left+file*step;
            canvas.drawText(label,xx,verticalRim*.48f+baseline,paint);
            canvas.drawText(label,xx,getHeight()-verticalRim*.48f+baseline,paint);
        }
        for(int row=0;row<10;row++) {
            String label=NUMBER_LABELS[flipped?row:9-row];float yy=top+row*step+baseline;
            canvas.drawText(label,horizontalRim*.48f,yy,paint);
            canvas.drawText(label,getWidth()-horizontalRim*.48f,yy,paint);
        }
    }
    private void drawTurnIndicator(Canvas canvas,float density){
        boolean bottom=position.redToMove!=flipped;
        float xx=getWidth()-8*density,yy=bottom?getHeight()-8*density:8*density;
        paint.setColor(readableMonochrome(boardColor));canvas.drawCircle(xx,yy,4.4f*density,paint);
        paint.setColor(position.redToMove?0xffd84d42:0xff162128);canvas.drawCircle(xx,yy,3.0f*density,paint);
    }
    private void drawArrow(Canvas canvas,String move,int color,float width,float head,boolean best) {
        if(!validMove(move))return;
        int from=squareIndex(move,0),to=squareIndex(move,2);
        float startX=x(from),startY=y(from),endX=x(to),endY=y(to);
        float dx=endX-startX,dy=endY-startY,length=(float)Math.hypot(dx,dy);if(length==0)return;
        float ux=dx/length,uy=dy/length;
        // Keep both Chinese characters readable, even for an adjacent-square move.
        float sourceInset=best?radius*.82f:0,destInset=best&&position.board[to]!=0?radius*.94f:radius*.15f;
        if(!best)destInset=radius*1.04f;
        startX+=ux*sourceInset;startY+=uy*sourceInset;endX-=ux*destInset;endY-=uy*destInset;
        paint.setStyle(Paint.Style.STROKE);paint.setColor(color);paint.setAlpha(best?220:230);
        paint.setStrokeWidth(step*width);paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(startX,startY,endX,endY,paint);
        float headLength=Math.min(step*head,length*.32f);double angle=Math.atan2(dy,dx);
        canvas.drawLine(endX,endY,endX-(float)Math.cos(angle-.55)*headLength,endY-(float)Math.sin(angle-.55)*headLength,paint);
        canvas.drawLine(endX,endY,endX-(float)Math.cos(angle+.55)*headLength,endY-(float)Math.sin(angle+.55)*headLength,paint);
        paint.setStrokeCap(Paint.Cap.BUTT);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
    }
    private static boolean validMove(String move){return move!=null&&move.length()==4&&move.charAt(0)>='a'&&move.charAt(0)<='i'&&move.charAt(2)>='a'&&move.charAt(2)<='i'&&move.charAt(1)>='0'&&move.charAt(1)<='9'&&move.charAt(3)>='0'&&move.charAt(3)<='9';}
    private static int squareIndex(String move,int offset){return (9-(move.charAt(offset+1)-'0'))*9+move.charAt(offset)-'a';}
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
    private String glyph(char p) {
        switch(p) { case 'K':return simplified?"帅":"帥";case 'k':return simplified?"将":"將";case 'A':return "仕";case 'a':return "士";case 'B':return "相";case 'b':return "象";case 'N':case 'n':return simplified?"马":"馬";case 'R':case 'r':return simplified?"车":"車";case 'C':return "炮";case 'c':return simplified?"炮":"砲";case 'P':return "兵";case 'p':return "卒";default:return ""; }
    }
}
