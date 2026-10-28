package org.xiangqi.study;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import org.xiangqi.study.core.Position;
import org.xiangqi.study.core.AppearancePresets;
import org.xiangqi.study.core.AnalysisVisuals;
import org.xiangqi.study.core.Engine;
import java.util.*;

/** A resolution-independent Xiangqi board using intersection coordinates. */
public final class BoardView extends View {
    public interface MoveListener { void onMove(String move); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fontMetrics=new Paint.FontMetrics();
    private final Path glyphOutline=new Path();
    private final Map<String,Float> fittedGlyphSizes=new HashMap<>();
    private float fittedGlyphStep;
    private Position position = Position.initial();
    private boolean flipped, editing;
    private int selected = -1;
    private String lastMove = "", hintMove = "";
    private List<AnalysisVisuals.Arrow> analysisArrows=Collections.emptyList();
    private static final int RED_ANALYSIS=0xffe75c50,BLACK_ANALYSIS=0xff243644;
    private List<String> legal = new ArrayList<>();
    private MoveListener listener;
    private float step, left, top, radius;
    private int bronze=0xff9f9065, boardColor=0xff3f3b2a, redDiscColor=0xffe8debb, blackDiscColor=0xffe8debb,
        red=0xffb2392b, black=0xff274440, moveArrow=0xffd7ae5c, bestArrow=0xffb3dea5;
    private int presetBoard=boardColor,presetRedDisc=redDiscColor,presetBlackDisc=blackDiscColor,
        presetGrid=bronze,presetRed=red,presetBlack=black,presetMove=moveArrow,presetBest=bestArrow;
    private Integer customBoard,customRedDisc,customBlackDisc;
    private boolean simplified;
    private int boardTexture=AppearancePresets.FLAT,boardBorder,discFinish=AppearancePresets.PLAIN;
    private final Path surfaceClip=new Path(),surfaceGrain=new Path();
    private final Typeface pieceTypeface=Typeface.create("serif",Typeface.NORMAL);
    private String coordinateStyle="none";
    private static final String[] FILE_LABELS={"a","b","c","d","e","f","g","h","i"};
    private static final String[] NUMBER_LABELS={"0","1","2","3","4","5","6","7","8","9"};
    public BoardView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE,null); setFocusable(true); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }
    public void setMoveListener(MoveListener listener) { this.listener=listener; }
    public void setAppearance(String boardStyle,String glyphStyle,String discStyle) {
        AppearancePresets.Board board=AppearancePresets.board(boardStyle);
        AppearancePresets.Disc disc=AppearancePresets.disc(discStyle);
        simplified="simplified".equals(glyphStyle);
        boardTexture=board.texture;boardBorder=board.border;discFinish=disc.finish;
        palette(board.background,board.grid,disc.redInk,disc.blackInk,board.playedArrow,board.bestArrow);
        presetRedDisc=disc.redBackground;presetBlackDisc=disc.blackBackground;
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
        this.position=p; this.lastMove=last==null?"":last; this.editing=editing; selected=-1; hintMove="";analysisArrows=Collections.emptyList();
        legal=editing?p.legalMoves():Collections.emptyList();
        setContentDescription("Xiangqi board. " + (p.redToMove?"Red":"Black") + " to move. " + (editing?"Explore mode. Tap a piece, then a legal destination. Use the legal move list for accessible input.":"Replay mode. Use the move navigation controls.")); invalidate();
    }
    public void setFlipped(boolean flipped) { this.flipped=flipped; invalidate(); }
    public void setCoordinates(String style) { coordinateStyle="iccs".equals(style)||"numeric".equals(style)?style:"none";invalidate(); }
    public void setHint(String move) { hintMove=move==null?"":move;analysisArrows=Collections.emptyList();invalidate(); }
    /** Every selected engine line contributes its first legal move and legal reply. */
    public void setAnalysisLines(Engine.Result result,boolean rootRedToMove){
        analysisArrows=AnalysisVisuals.arrows(position,result,rootRedToMove);hintMove="";
        for(AnalysisVisuals.Arrow arrow:analysisArrows)if(arrow.rank==1&&!arrow.reply){hintMove=arrow.move;break;}
        invalidate();
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) { int w=MeasureSpec.getSize(widthSpec); setMeasuredDimension(w,Math.round(w*1.10f)); }
    private float x(int index) { int file=index%9; return left+(flipped?8-file:file)*step; }
    private float y(int index) { int rank=index/9; return top+(flipped?9-rank:rank)*step; }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);paint.setAlpha(255);float w=getWidth(), h=getHeight();
        // Coordinates share the existing rim. Turning them on never shrinks the
        // grid or discs, and touch targets remain on the same intersections.
        float density=getResources().getDisplayMetrics().density,margin=8*density;
        // The outer intersections need half a cell beyond the grid for the discs
        // and selection rings. Fixed pixel insets clipped them on dense displays.
        step=Math.min((w-2*margin)/9,(h-2*margin)/10);
        left=(w-step*8)/2; top=(h-step*9)/2; radius=step*0.420f;
        paint.setColor(boardColor); c.drawRoundRect(0,0,w,h,14*density,14*density,paint);
        drawSurface(c,w,h,density);
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
        drawArrow(c,lastMove,moveArrow,.11f,.29f,false);
        // Paint every shadow/halo before any opaque face, so adjacent glowing
        // pieces never wash out a character that has already been drawn.
        if(discFinish>=AppearancePresets.BEVELED)for(int i=0;i<90;i++)if(position.board[i]!=0&&position.board[i]!='.')
            drawReferenceUnderlay(c,i,Character.isUpperCase(position.board[i]));
        for(int i=0;i<90;i++) if(position.board[i]!=0 && position.board[i]!='.') {
            boolean redSide=Character.isUpperCase(position.board[i]);
            int fill=redSide?redDiscColor:blackDiscColor,ink=redSide?red:black;
            if(discFinish>=AppearancePresets.BEVELED)drawReferenceFace(c,i,fill,ink,redSide);
            else {
            paint.setColor(fill); c.drawCircle(x(i),y(i),radius,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(step*.026f); paint.setColor(Character.isUpperCase(position.board[i])?red:black);
            c.drawCircle(x(i),y(i),radius*.88f,paint); paint.setStyle(Paint.Style.FILL);
            if(discFinish==AppearancePresets.ENGRAVED){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(step*.012f);c.drawCircle(x(i),y(i),radius*.76f,paint);paint.setStyle(Paint.Style.FILL);}
            if(discFinish==AppearancePresets.GLAZED){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(step*.018f);paint.setColor(readableMonochrome(fill));paint.setAlpha(70);
                c.drawArc(x(i)-radius*.96f,y(i)-radius*.96f,x(i)+radius*.96f,y(i)+radius*.96f,205,105,false,paint);
                paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setColor(Character.isUpperCase(position.board[i])?red:black);
            }
            if(discFinish==AppearancePresets.LACQUER){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(step*.014f);c.drawCircle(x(i),y(i),radius*.76f,paint);
                paint.setColor(readableMonochrome(fill));paint.setAlpha(110);paint.setStrokeWidth(step*.018f);
                c.drawArc(x(i)-radius*.97f,y(i)-radius*.97f,x(i)+radius*.97f,y(i)+radius*.97f,205,110,false,paint);
                paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);paint.setColor(Character.isUpperCase(position.board[i])?red:black);
            }
            }
            paint.setColor(ink);
            String character=glyph(position.board[i]);paint.setTypeface(pieceTypeface);paint.setTextSize(fittedGlyphSize(character,density));paint.setAlpha(255);
            paint.getFontMetrics(fontMetrics);c.drawText(character,x(i),y(i)-(fontMetrics.ascent+fontMetrics.descent)/2,paint);
            if(i==selected) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(step*.075f); paint.setColor(Color.rgb(183,211,168)); c.drawCircle(x(i),y(i),radius*1.12f,paint); paint.setStyle(Paint.Style.FILL); }
        }
        if(selected>=0) for(String move:legal) if(Position.index(move.substring(0,2))==selected) {
            int dest=Position.index(move.substring(2)); paint.setColor(Color.argb(175,182,213,170)); c.drawCircle(x(dest),y(dest),step*.12f,paint);
        }
        if(analysisArrows.isEmpty())drawArrow(c,hintMove,bestArrow,.14f,.34f,true);
        else {
            // Composite each shaft and its contrast outline together at40%,
            // rather than stacking two translucent strokes into75% opacity.
            int arrowsLayer=c.saveLayerAlpha(0,0,w,h,102);
            for(AnalysisVisuals.Arrow arrow:analysisArrows)drawAnalysisArrow(c,arrow,false);
            c.restoreToCount(arrowsLayer);
            for(AnalysisVisuals.Arrow arrow:analysisArrows)drawAnalysisArrow(c,arrow,true);
        }
        drawTurnIndicator(c,density);
    }
    /** Light procedural material cues; custom RGB always keeps a flat exact fill. */
    private void drawSurface(Canvas canvas,float w,float h,float density){
        if(customBoard!=null)return;
        if(boardTexture>=AppearancePresets.LIGHT_MAPLE){drawReferenceSurface(canvas,w,h,density);return;}
        if(boardTexture!=AppearancePresets.FLAT){
            surfaceClip.reset();surfaceClip.addRoundRect(0,0,w,h,14*density,14*density,Path.Direction.CW);
            int save=canvas.save();canvas.clipPath(surfaceClip);
            paint.setColor(readableMonochrome(boardColor));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.5f*density);
            if(boardTexture==AppearancePresets.WOOD){
                paint.setAlpha(13);
                for(int line=1;line<35;line++){
                    float xx=w*line/35f,bend=(line%5-2)*density;
                    surfaceGrain.reset();surfaceGrain.moveTo(xx,0);surfaceGrain.cubicTo(xx+bend,h*.3f,xx-bend,h*.7f,xx,h);canvas.drawPath(surfaceGrain,paint);
                }
            }else if(boardTexture==AppearancePresets.PAPER){
                paint.setAlpha(7);
                for(int line=1;line<32;line++){float yy=h*line/32f;canvas.drawLine(0,yy,w,yy+(line%3-1)*density,paint);}
            }else{
                paint.setAlpha(9);
                for(int line=1;line<20;line++){
                    float yy=h*line/20f;surfaceGrain.reset();surfaceGrain.moveTo(0,yy);surfaceGrain.cubicTo(w*.25f,yy-4*density,w*.7f,yy+5*density,w,yy);canvas.drawPath(surfaceGrain,paint);
                }
            }
            paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);canvas.restoreToCount(save);
        }
        if(boardBorder!=0){
            paint.setColor(boardBorder);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density);
            canvas.drawRoundRect(2*density,2*density,w-2*density,h-2*density,12*density,12*density,paint);
            paint.setAlpha(85);paint.setStrokeWidth(.6f*density);
            canvas.drawRoundRect(4*density,4*density,w-4*density,h-4*density,10*density,10*density,paint);
            paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);
        }
    }

    /** Original procedural materials inspired by the supplied reference boards. */
    private void drawReferenceSurface(Canvas canvas,float w,float h,float density){
        surfaceClip.reset();surfaceClip.addRoundRect(0,0,w,h,14*density,14*density,Path.Direction.CW);
        int saved=canvas.save();canvas.clipPath(surfaceClip);
        if(boardTexture==AppearancePresets.NEON){
            paint.setShader(new RadialGradient(w*.50f,h*.50f,w*.76f,new int[]{0xff4c076c,boardColor,0xff0c0033},new float[]{0,.58f,1},Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
        }else if(boardTexture==AppearancePresets.ANTIQUE){
            paint.setShader(new RadialGradient(w*.50f,h*.46f,w*.86f,new int[]{mix(boardColor,Color.WHITE,.16f),boardColor,mix(boardColor,0xff6d3515,.32f)},new float[]{0,.52f,1},Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
            // Fine, deterministic paper mottling rather than a stretched bitmap.
            paint.setColor(0xff75401a);paint.setAlpha(17);
            for(int i=0;i<1250;i++){
                float xx=((i*73+19)%997)/997f*w,yy=((i*193+47)%991)/991f*h;
                canvas.drawCircle(xx,yy,(.22f+(i%5)*.13f)*density,paint);
            }
            paint.setAlpha(255);drawAntiqueCorner(canvas,w,h,density,false);drawAntiqueCorner(canvas,w,h,density,true);
        }else{
            boolean maple=boardTexture==AppearancePresets.LIGHT_MAPLE;
            paint.setShader(new LinearGradient(0,0,w,0,new int[]{mix(boardColor,Color.WHITE,maple?.16f:.07f),boardColor,mix(boardColor,0xff9a582d,maple?.07f:.12f)},new float[]{0,.47f,1},Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(.55f,density*.45f));
            for(int line=1;line<110;line++){
                float xx=w*line/110f,bend=((line*7)%9-4)*density*.45f;
                paint.setColor(line%3==0?Color.WHITE:0xff9e6839);paint.setAlpha(maple?(line%3==0?31:16):11);
                surfaceGrain.reset();surfaceGrain.moveTo(xx,0);surfaceGrain.cubicTo(xx+bend,h*.27f,xx-bend*.8f,h*.76f,xx,h);canvas.drawPath(surfaceGrain,paint);
            }
            paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);
        }
        // Rounded outer rails sit in the existing rim; the grid never shrinks.
        paint.setStyle(Paint.Style.STROKE);paint.setColor(boardBorder);
        paint.setStrokeWidth((boardTexture==AppearancePresets.NEON?2.2f:3.2f)*density);
        canvas.drawRoundRect(2*density,2*density,w-2*density,h-2*density,12*density,12*density,paint);
        paint.setStrokeWidth(.65f*density);paint.setColor(mix(boardBorder,Color.WHITE,.45f));
        canvas.drawRoundRect(5*density,5*density,w-5*density,h-5*density,9*density,9*density,paint);
        if(boardTexture==AppearancePresets.LIGHT_MAPLE||boardTexture==AppearancePresets.CARVED_WOOD){
            paint.setColor(bronze);paint.setAlpha(125);paint.setStrokeWidth(.8f*density);
            canvas.drawLine(left-step*.08f,top,left-step*.08f,top+9*step,paint);
            canvas.drawLine(left+step*8.08f,top,left+step*8.08f,top+9*step,paint);
        }
        paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);paint.setShader(null);canvas.restoreToCount(saved);
    }

    private void drawAntiqueCorner(Canvas canvas,float w,float h,float density,boolean opposite){
        int saved=canvas.save();if(opposite){canvas.translate(w,h);canvas.rotate(180);}
        paint.setColor(0xff8c5025);paint.setAlpha(58);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(1.7f*density);
        surfaceGrain.reset();surfaceGrain.moveTo(-2*density,h*.20f);surfaceGrain.cubicTo(w*.045f,h*.12f,w*.025f,h*.065f,w*.17f,-3*density);canvas.drawPath(surfaceGrain,paint);
        surfaceGrain.reset();surfaceGrain.moveTo(w*.036f,h*.13f);surfaceGrain.quadTo(w*.085f,h*.105f,w*.12f,h*.115f);canvas.drawPath(surfaceGrain,paint);
        surfaceGrain.reset();surfaceGrain.moveTo(w*.064f,h*.061f);surfaceGrain.quadTo(w*.10f,h*.056f,w*.14f,h*.039f);canvas.drawPath(surfaceGrain,paint);
        paint.setStyle(Paint.Style.FILL);
        float[][] blooms={{.052f,.105f},{.115f,.112f},{.086f,.057f},{.137f,.036f},{.027f,.161f}};
        for(int n=0;n<blooms.length;n++){
            float cx=blooms[n][0]*w,cy=blooms[n][1]*h,r=(1.1f+(n%2)*.4f)*density;
            for(int petal=0;petal<5;petal++){double angle=petal*Math.PI*2/5;canvas.drawCircle(cx+(float)Math.cos(angle)*r,cy+(float)Math.sin(angle)*r,r*.80f,paint);}
            paint.setColor(0xffe3b76c);paint.setAlpha(90);canvas.drawCircle(cx,cy,r*.45f,paint);paint.setColor(0xff8c5025);paint.setAlpha(58);
        }
        paint.setAlpha(255);paint.setStrokeCap(Paint.Cap.BUTT);canvas.restoreToCount(saved);
    }

    private void drawReferenceUnderlay(Canvas canvas,int index,boolean redSide){
        float cx=x(index),cy=y(index);paint.setStyle(Paint.Style.FILL);
        if(discFinish==AppearancePresets.NEON_GLOW){
            int halo=redSide?0xffff58bd:0xff43e5ff;
            paint.setShader(new RadialGradient(cx,cy,radius*1.58f,new int[]{withAlpha(halo,130),withAlpha(halo,86),0},new float[]{0,.68f,1},Shader.TileMode.CLAMP));
            canvas.drawCircle(cx,cy,radius*1.58f,paint);paint.setShader(null);
            paint.setColor(redSide?0xffee70ca:0xff7960ed);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(radius*.11f);canvas.drawCircle(cx,cy,radius*1.045f,paint);paint.setStyle(Paint.Style.FILL);
        }else{
            float offset=discFinish==AppearancePresets.CARVED?radius*.19f:radius*.10f;
            paint.setShader(new RadialGradient(cx+radius*.10f,cy+offset,radius*1.34f,new int[]{0x79000000,0x41000000,0},new float[]{0,.73f,1},Shader.TileMode.CLAMP));
            canvas.drawCircle(cx+radius*.10f,cy+offset,radius*1.34f,paint);paint.setShader(null);
            if(discFinish==AppearancePresets.CARVED){
                paint.setColor(mix(redSide?redDiscColor:blackDiscColor,0xff794921,.48f));canvas.drawCircle(cx,cy+radius*.12f,radius,paint);
            }
        }
    }

    private void drawReferenceFace(Canvas canvas,int index,int fill,int ink,boolean redSide){
        float cx=x(index),cy=y(index);boolean custom=redSide?customRedDisc!=null:customBlackDisc!=null;
        paint.setStyle(Paint.Style.FILL);paint.setColor(fill);
        if(!custom&&discFinish!=AppearancePresets.NEON_GLOW){
            paint.setShader(new RadialGradient(cx-radius*.17f,cy-radius*.19f,radius*1.24f,new int[]{fill,fill,mix(fill,Color.WHITE,discFinish==AppearancePresets.CARVED?.24f:.16f),mix(fill,Color.BLACK,.30f)},new float[]{0,.57f,.76f,1},Shader.TileMode.CLAMP));
        }
        canvas.drawCircle(cx,cy,radius,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        if(discFinish==AppearancePresets.NEON_GLOW){
            paint.setColor(redSide?0xffff6dbf:0xff4be5ff);paint.setStrokeWidth(radius*.075f);canvas.drawCircle(cx,cy,radius*.94f,paint);
            paint.setColor(redSide?0xfffffbff:0xfff0ffff);paint.setStrokeWidth(radius*.025f);canvas.drawCircle(cx,cy,radius*.82f,paint);
            paint.setColor(redSide?0xffad428d:0xff7052bc);paint.setStrokeWidth(radius*.025f);canvas.drawCircle(cx,cy,radius*.71f,paint);
        }else if(discFinish==AppearancePresets.GILDED){
            paint.setColor(ink);paint.setStrokeWidth(radius*.026f);canvas.drawCircle(cx,cy,radius*.94f,paint);canvas.drawCircle(cx,cy,radius*.79f,paint);
            paint.setStrokeWidth(radius*.011f);paint.setAlpha(135);canvas.drawCircle(cx,cy,radius*.86f,paint);paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);paint.setAlpha(170);
            for(int dot=0;dot<44;dot++){double a=dot*Math.PI*2/44;canvas.drawCircle(cx+(float)Math.cos(a)*radius*.875f,cy+(float)Math.sin(a)*radius*.875f,radius*.012f,paint);}
            paint.setAlpha(255);paint.setStyle(Paint.Style.STROKE);
        }else if(discFinish==AppearancePresets.CARVED){
            paint.setColor(mix(fill,0xff5e351c,.55f));paint.setStrokeWidth(radius*.045f);canvas.drawCircle(cx,cy,radius*.94f,paint);
            paint.setColor(mix(fill,Color.WHITE,.45f));paint.setStrokeWidth(radius*.06f);canvas.drawArc(cx-radius*.87f,cy-radius*.87f,cx+radius*.87f,cy+radius*.87f,205,135,false,paint);
            paint.setColor(mix(fill,0xff71461e,.42f));paint.setStrokeWidth(radius*.025f);canvas.drawCircle(cx,cy,radius*.72f,paint);
        }else{
            paint.setColor(ink);paint.setStrokeWidth(radius*.032f);canvas.drawCircle(cx,cy,radius*.91f,paint);canvas.drawCircle(cx,cy,radius*.78f,paint);
            paint.setColor(mix(fill,Color.WHITE,.65f));paint.setStrokeWidth(radius*.035f);canvas.drawArc(cx-radius*.96f,cy-radius*.96f,cx+radius*.96f,cy+radius*.96f,210,112,false,paint);
        }
        paint.setStyle(Paint.Style.FILL);paint.setColor(ink);paint.setAlpha(255);
    }
    private static int withAlpha(int color,int alpha){return (color&0xffffff)|(alpha<<24);}
    private static int mix(int first,int second,float amount){return Color.rgb(Math.round(Color.red(first)*(1-amount)+Color.red(second)*amount),Math.round(Color.green(first)*(1-amount)+Color.green(second)*amount),Math.round(Color.blue(first)*(1-amount)+Color.blue(second)*amount));}
    /** Fit the actual Chinese outline, including diagonals, inside its inner ring. */
    private float fittedGlyphSize(String text,float density){
        if(fittedGlyphStep!=step){fittedGlyphSizes.clear();fittedGlyphStep=step;}
        String key=text+discFinish;Float cached=fittedGlyphSizes.get(key);if(cached!=null)return cached;
        float requested=step*.54f;paint.setTextSize(requested);paint.getFontMetrics(fontMetrics);
        glyphOutline.reset();paint.getTextPath(text,0,text.length(),0,-(fontMetrics.ascent+fontMetrics.descent)/2,glyphOutline);
        float extent=0;float[] points=glyphOutline.approximate(.2f);
        for(int i=0;i+2<points.length;i+=3)extent=Math.max(extent,(float)Math.hypot(points[i+1],points[i+2]));
        float ring=.88f;
        if(discFinish==AppearancePresets.ENGRAVED||discFinish==AppearancePresets.LACQUER)ring=.76f;
        else if(discFinish==AppearancePresets.BEVELED)ring=.78f;
        else if(discFinish==AppearancePresets.NEON_GLOW)ring=.71f;
        else if(discFinish==AppearancePresets.GILDED)ring=.79f;
        else if(discFinish==AppearancePresets.CARVED)ring=.72f;
        float available=radius*ring-Math.max(.65f*density,radius*.05f);
        float size=extent>available&&extent>0?requested*available/extent:requested;
        fittedGlyphSizes.put(key,size);return size;
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
        paint.setStyle(Paint.Style.STROKE);paint.setColor(color);paint.setAlpha(102);
        paint.setStrokeWidth(step*width);paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(startX,startY,endX,endY,paint);
        float headLength=Math.min(step*head,length*.32f);double angle=Math.atan2(dy,dx);
        canvas.drawLine(endX,endY,endX-(float)Math.cos(angle-.55)*headLength,endY-(float)Math.sin(angle-.55)*headLength,paint);
        canvas.drawLine(endX,endY,endX-(float)Math.cos(angle+.55)*headLength,endY-(float)Math.sin(angle+.55)*headLength,paint);
        paint.setStrokeCap(Paint.Cap.BUTT);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
    }
    private void drawAnalysisArrow(Canvas canvas,AnalysisVisuals.Arrow arrow,boolean badgeOnly){
        int from=squareIndex(arrow.move,0),to=squareIndex(arrow.move,2),duplicates=0,order=0;
        for(AnalysisVisuals.Arrow other:analysisArrows)if(other.move.equals(arrow.move)){if(other==arrow)order=duplicates;duplicates++;}
        float sx=x(from),sy=y(from),ex=x(to),ey=y(to),dx=ex-sx,dy=ey-sy,length=(float)Math.hypot(dx,dy);if(length==0)return;
        float ux=dx/length,uy=dy/length,lane=(order-(duplicates-1)*.5f)*step*.14f;
        sx+=ux*radius*.83f-uy*lane;sy+=uy*radius*.83f+ux*lane;
        float targetInset=position.board[to]!=0?radius*.96f:radius*.18f;
        ex-=ux*targetInset+uy*lane;ey-=uy*targetInset-ux*lane;
        float head=Math.min(step*.25f,length*.22f);double angle=Math.atan2(dy,dx);
        float hx1=ex-(float)Math.cos(angle-.55)*head,hy1=ey-(float)Math.sin(angle-.55)*head;
        float hx2=ex-(float)Math.cos(angle+.55)*head,hy2=ey-(float)Math.sin(angle+.55)*head;
        int color=arrow.red?RED_ANALYSIS:BLACK_ANALYSIS;
        if(!badgeOnly){
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setAlpha(255);
            paint.setColor(readableMonochrome(boardColor));paint.setStrokeWidth(step*.205f);
            canvas.drawLine(sx,sy,ex,ey,paint);canvas.drawLine(ex,ey,hx1,hy1,paint);canvas.drawLine(ex,ey,hx2,hy2,paint);
            paint.setColor(color);paint.setStrokeWidth(step*.15f);
            canvas.drawLine(sx,sy,ex,ey,paint);canvas.drawLine(ex,ey,hx1,hy1,paint);canvas.drawLine(ex,ey,hx2,hy2,paint);
            paint.setStrokeCap(Paint.Cap.BUTT);paint.setStyle(Paint.Style.FILL);return;
        }
        // Shared replies use one combined rank badge, so even a one-square
        // arrow can retain every rank without stacking illegible tiny labels.
        if(order==duplicates-1){
            StringBuilder ranks=new StringBuilder();for(AnalysisVisuals.Arrow other:analysisArrows)if(other.move.equals(arrow.move)){if(ranks.length()>0)ranks.append('·');ranks.append(other.rank);}
            float bx=sx+(ex-sx)*.54f+uy*lane,by=sy+(ey-sy)*.54f-ux*lane,badge=step*.155f;
            paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(step*.225f);
            float half=Math.max(badge,paint.measureText(ranks.toString())*.5f+step*.06f),edge=step*.025f;
            paint.setColor(readableMonochrome(boardColor));canvas.drawRoundRect(bx-half-edge,by-badge-edge,bx+half+edge,by+badge+edge,badge,badge,paint);
            paint.setColor(color);canvas.drawRoundRect(bx-half,by-badge,bx+half,by+badge,badge,badge,paint);
            paint.setColor(readableMonochrome(color));paint.getFontMetrics(fontMetrics);
            canvas.drawText(ranks.toString(),bx,by-(fontMetrics.ascent+fontMetrics.descent)/2,paint);
        }
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
