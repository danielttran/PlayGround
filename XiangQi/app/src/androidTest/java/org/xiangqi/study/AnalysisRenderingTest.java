package org.xiangqi.study;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.Gravity;
import android.widget.LinearLayout;
import org.xiangqi.study.core.Engine;
import org.xiangqi.study.core.Position;
import org.xiangqi.study.core.AppearancePresets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Real Canvas output for side-colored PV arrows and the neutral/off evaluation rail. */
public final class AnalysisRenderingTest extends InstrumentationTestCase {
    private static final int RED_ARROW=0xffe75c50,BLACK_ARROW=0xff243644;
    public void testTwoAndThreeLinesDrawBothSidesAndRetainSharedReplyRanks(){
        for(boolean red:new boolean[]{true,false})for(int count:new int[]{2,3})for(boolean flipped:new boolean[]{false,true}){
            onMain(()->{
                BoardView board=board(red,flipped);board.setAnalysisLines(result(red,count),red);
                RecordingCanvas canvas=new RecordingCanvas(board.getWidth(),board.getHeight());board.draw(canvas);
                try{
                    assertEquals("Three colored segments form every first/reply arrow",count*6,canvas.arrows.size());
                    assertEquals(count*3,countColor(canvas,RED_ARROW));assertEquals(count*3,countColor(canvas,BLACK_ARROW));
                    assertEquals("The first arrow belongs to the current moving side",red?RED_ARROW:BLACK_ARROW,canvas.arrows.get(0).color);
                    assertTrue("Shared reply keeps every contributing rank",canvas.labels.contains(count==2?"1·2":"1·2·3"));
                    for(int rank=1;rank<=count;rank++)assertTrue(canvas.labels.contains(Integer.toString(rank)));
                    Stroke redLine=firstColor(canvas,RED_ARROW),blackLine=firstColor(canvas,BLACK_ARROW);
                    assertTrue("Flipping reverses the same logical side's board end",flipped?redLine.y1<blackLine.y1:redLine.y1>blackLine.y1);
                    assertTrue("Flipping reverses the root cannon arrow horizontally",flipped?canvas.arrows.get(0).x2<canvas.arrows.get(0).x1:canvas.arrows.get(0).x2>canvas.arrows.get(0).x1);
                }finally{canvas.bitmap.recycle();}
            });
        }
    }
    public void testNewResultsReplaceBothSidesAndStoppingOrPositionChangeClearsThem(){
        onMain(()->{
            BoardView board=board(true,false);board.setAnalysisLines(result(true,3),true);
            RecordingCanvas three=draw(board);try{assertEquals(18,three.arrows.size());}finally{three.bitmap.recycle();}
            board.setAnalysisLines(result(true,2),true);RecordingCanvas two=draw(board);try{assertEquals(12,two.arrows.size());assertFalse(two.labels.contains("3"));assertFalse(two.labels.contains("1·2·3"));}finally{two.bitmap.recycle();}
            board.setAnalysisLines(null,true);RecordingCanvas off=draw(board);try{assertTrue(off.arrows.isEmpty());}finally{off.bitmap.recycle();}
            board.setAnalysisLines(result(true,3),true);board.setPosition(Position.initial().play("b2e2"),"b2e2",false);
            RecordingCanvas changed=draw(board);try{assertTrue("Old-position PV arrows never remain",changed.arrows.isEmpty());}finally{changed.bitmap.recycle();}
            board.setAnalysisLines(result(true,3),true);RecordingCanvas stale=draw(board);try{assertTrue("A mismatched result cannot paint either side",stale.arrows.isEmpty());}finally{stale.bitmap.recycle();}
        });
    }
    public void testEvaluationRailUsesRedPerspectiveAndFlipsOnlyItsOrientation(){
        onMain(()->{
            EvaluationBarView bar=new EvaluationBarView(getInstrumentation().getTargetContext());
            bar.measure(View.MeasureSpec.makeMeasureSpec(dp(100),View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(dp(300),View.MeasureSpec.EXACTLY));
            assertEquals("The rail has a compact16dp preferred width",dp(16),bar.getMeasuredWidth());bar.layout(0,0,bar.getMeasuredWidth(),bar.getMeasuredHeight());
            for(boolean flipped:new boolean[]{false,true}){
                bar.setFlipped(flipped);bar.setEvaluation(true,600);Bitmap positive=draw(bar);
                bar.setEvaluation(true,-600);Bitmap negative=draw(bar);
                try{
                    assertTrue("Positive always gives Red the larger region",pixels(positive,EvaluationBarView.RED)>pixels(positive,EvaluationBarView.BLACK));
                    assertTrue("Negative always gives Black the larger region",pixels(negative,EvaluationBarView.BLACK)>pixels(negative,EvaluationBarView.RED));
                    int x=positive.getWidth()/2,top=positive.getHeight()/20,bottom=positive.getHeight()*19/20;
                    assertEquals(flipped?EvaluationBarView.RED:EvaluationBarView.BLACK,positive.getPixel(x,top));
                    assertEquals(flipped?EvaluationBarView.BLACK:EvaluationBarView.RED,positive.getPixel(x,bottom));
                }finally{positive.recycle();negative.recycle();}
            }
            bar.setEvaluation(true,0);Bitmap equal=draw(bar);bar.setEvaluation(true,null);Bitmap pending=draw(bar);
            try{assertTrue("Pending never retains the last advantage",Arrays.equals(data(equal),data(pending)));assertTrue(bar.getContentDescription().toString().contains("Waiting for an evaluation"));}finally{equal.recycle();pending.recycle();}
        });
    }
    public void testEvaluationOffDiscardsStaleScoreAndRendersOnlyNeutral(){
        onMain(()->{
            EvaluationBarView bar=new EvaluationBarView(getInstrumentation().getTargetContext());bar.measure(View.MeasureSpec.makeMeasureSpec(dp(16),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dp(300),View.MeasureSpec.EXACTLY));bar.layout(0,0,bar.getMeasuredWidth(),bar.getMeasuredHeight());
            bar.setEvaluation(true,30000);bar.setEvaluation(false,30000);Bitmap off=draw(bar);bar.setEvaluation(false,null);Bitmap cleared=draw(bar);
            try{
                assertTrue(Arrays.equals(data(off),data(cleared)));assertEquals(0,pixels(off,EvaluationBarView.RED));assertEquals(0,pixels(off,EvaluationBarView.BLACK));
                assertTrue(pixels(off,EvaluationBarView.NEUTRAL)>off.getWidth()*off.getHeight()*.8);
                String description=bar.getContentDescription().toString();assertTrue(description.contains("Analysis off. Neutral."));assertFalse(description.contains("300.00"));assertFalse(description.contains("advantage"));
            }finally{off.recycle();cleared.recycle();}
        });
    }
    public void testNarrowBoardRowsMatchRailHeightWithoutExtraBoardPadding(){
        onMain(()->{
            for(int width:new int[]{220,280,393}){
                LinearLayout row=new LinearLayout(getInstrumentation().getTargetContext());row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
                EvaluationBarView rail=new EvaluationBarView(getInstrumentation().getTargetContext());BoardView board=new BoardView(getInstrumentation().getTargetContext());
                LinearLayout.LayoutParams barSize=new LinearLayout.LayoutParams(dp(16),LinearLayout.LayoutParams.MATCH_PARENT);barSize.setMargins(0,0,dp(4),0);row.addView(rail,barSize);row.addView(board,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
                rail.setEvaluation(true,null);
                row.measure(View.MeasureSpec.makeMeasureSpec(dp(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));row.layout(0,0,row.getMeasuredWidth(),row.getMeasuredHeight());
                assertEquals("Rail and board have exactly matching heights at width "+width,board.getHeight(),rail.getHeight());
                assertEquals("The row follows the board's natural height",Math.round(board.getWidth()*1.10f),row.getHeight());
                assertEquals("Only the16dp rail and4dp gap consume board width",dp(width)-dp(16)-dp(4),board.getWidth());
                assertEquals("The board has no new horizontal padding",0,board.getPaddingLeft()+board.getPaddingRight());
                assertEquals("Rail and board align at the top",board.getTop(),rail.getTop());
                rail.setEvaluation(false,null);
                row.measure(View.MeasureSpec.makeMeasureSpec(dp(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));row.layout(0,0,row.getMeasuredWidth(),row.getMeasuredHeight());
                assertEquals("Analysis off releases the rail and gap",View.GONE,rail.getVisibility());
                assertEquals("Analysis off gives the board the full available width",dp(width),board.getWidth());
                assertEquals("The expanded board stays centered with no empty left gutter",0,board.getLeft());
                assertEquals(Math.round(board.getWidth()*1.10f),row.getHeight());
            }
        });
    }
    public void testBiggerDiscsKeepChineseOutlinesClearOfEveryInnerRim(){
        onMain(()->{
            String[] boards=AppearancePresets.boardIds(),discs=AppearancePresets.discIds();BoardView board=board(true,false);
            float[] innerRing={.88f,.76f,.88f,.76f,.78f,.71f,.79f,.72f};
            for(int i=0;i<boards.length;i++)for(String glyph:new String[]{"traditional","simplified"}){
                String disc=discs[i%discs.length];board.setAppearance(boards[i],glyph,disc);RecordingCanvas canvas=draw(board);
                try{
                    float step=boardFloat(board,"step"),radius=boardFloat(board,"radius");
                    assertEquals("Discs are modestly enlarged",step*.420f,radius,.01f);assertTrue("Neighboring discs retain a clear gap",radius*2<step*.90f);
                    assertEquals(32,canvas.glyphs.size());float safe=radius*(innerRing[AppearancePresets.disc(disc).finish]-.04f)+.25f;
                    for(GlyphSample sample:canvas.glyphs){assertEquals("Chinese text stays fully opaque",255,sample.alpha);assertTrue("The user requested bigger pieces, not bigger characters",sample.textSize<=step*.54f+.01f);assertTrue("Chinese outline touches inner rim: "+disc+" "+sample.text+" "+sample.extent+" > "+safe,sample.extent<=safe);assertTrue(sample.extent>0);}
                }finally{canvas.bitmap.recycle();}
            }
        });
    }
    public void testThickerArrowsCompositeAtHalfOpacityAndTextStaysOpaque(){
        onMain(()->{
            BoardView board=board(false,false);board.setPosition(Position.initial().play("g3g4"),"g3g4",false);board.setAnalysisLines(result(false,3),false);RecordingCanvas canvas=draw(board);
            try{
                float step=boardFloat(board,"step");assertEquals(18,canvas.arrows.size());
                for(Stroke arrow:canvas.arrows){assertEquals("Analysis arrows composite at50%",128,arrow.alpha);assertEquals("The shaft is thicker than shipped0.5.2",step*.13f,arrow.width,.01f);}
                int played=boardInt(board,"moveArrow"),playedSegments=0;
                for(Stroke stroke:canvas.allStrokes)if(stroke.color==played){playedSegments++;assertEquals("Played arrow also uses50% opacity",128,stroke.alpha);assertEquals(step*.095f,stroke.width,.01f);}
                assertEquals(3,playedSegments);assertEquals("Only one shared offscreen arrow layer is allocated",1,canvas.arrowLayers);
                for(int alpha:canvas.textAlphas)assertEquals("Transparency must never leak into Chinese text, river or rank badges",255,alpha);
            }finally{canvas.bitmap.recycle();}
        });
    }
    private BoardView board(boolean red,boolean flipped){BoardView board=new BoardView(getInstrumentation().getTargetContext());board.setPosition(Position.fromFen(Position.START_FEN.replace(" w ",red?" w ":" b ")),"",false);board.setFlipped(flipped);board.measure(View.MeasureSpec.makeMeasureSpec(dp(393),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));board.layout(0,0,board.getMeasuredWidth(),board.getMeasuredHeight());return board;}
    private static Engine.Result result(boolean red,int count){String[] first=red?new String[]{"b2e2","h2e2","b0c2"}:new String[]{"b7e7","h7e7","b9c7"};Engine.Result result=new Engine.Result();List<Engine.Line> lines=new ArrayList<>();for(int i=0;i<count;i++)lines.add(new Engine.Line(i+1,100-i,12,Arrays.asList(first[i],red?"b7e7":"b2e2")));result.lines=lines;result.pv=lines.get(0).pv;result.bestMove=result.pv.get(0);return result;}
    private static RecordingCanvas draw(BoardView board){RecordingCanvas canvas=new RecordingCanvas(board.getWidth(),board.getHeight());board.draw(canvas);return canvas;}
    private static Bitmap draw(View view){Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));return bitmap;}
    private static int[] data(Bitmap bitmap){int[] values=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(values,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());return values;}
    private static int pixels(Bitmap bitmap,int color){int count=0;for(int pixel:data(bitmap))if(pixel==color)count++;return count;}
    private static int countColor(RecordingCanvas canvas,int color){int count=0;for(Stroke line:canvas.arrows)if(line.color==color)count++;return count;}
    private static Stroke firstColor(RecordingCanvas canvas,int color){for(Stroke line:canvas.arrows)if(line.color==color)return line;throw new AssertionError("Missing side-colored arrow");}
    private int dp(int value){return Math.round(value*getInstrumentation().getTargetContext().getResources().getDisplayMetrics().density);}
    private void onMain(Runnable work){UiTestActions.runOnMain(getInstrumentation(),work);}
    private static float boardFloat(BoardView board,String name){try{java.lang.reflect.Field field=BoardView.class.getDeclaredField(name);field.setAccessible(true);return field.getFloat(board);}catch(Exception error){throw new RuntimeException(error);}}
    private static int boardInt(BoardView board,String name){try{java.lang.reflect.Field field=BoardView.class.getDeclaredField(name);field.setAccessible(true);return field.getInt(board);}catch(Exception error){throw new RuntimeException(error);}}
    private static final class Stroke {final int color,alpha;final float x1,y1,x2,y2,width;Stroke(int color,int alpha,float x1,float y1,float x2,float y2,float width){this.color=color;this.alpha=alpha;this.x1=x1;this.y1=y1;this.x2=x2;this.y2=y2;this.width=width;}}
    private static final class GlyphSample {final String text;final int alpha;final float textSize,extent;GlyphSample(String text,int alpha,float textSize,float extent){this.text=text;this.alpha=alpha;this.textSize=textSize;this.extent=extent;}}
    private static final class RecordingCanvas extends Canvas {
        final Bitmap bitmap;final List<Stroke> arrows=new ArrayList<>(),allStrokes=new ArrayList<>();final List<String> labels=new ArrayList<>();final List<Integer> textAlphas=new ArrayList<>();final List<GlyphSample> glyphs=new ArrayList<>();int layerAlpha=255,arrowLayers;
        RecordingCanvas(int width,int height){this(Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888));}private RecordingCanvas(Bitmap bitmap){super(bitmap);this.bitmap=bitmap;}
        @Override public int saveLayerAlpha(float left,float top,float right,float bottom,int alpha){layerAlpha=alpha;arrowLayers++;return super.saveLayerAlpha(left,top,right,bottom,alpha);}
        @Override public void restoreToCount(int count){super.restoreToCount(count);layerAlpha=255;}
        @Override public void drawLine(float sx,float sy,float ex,float ey,Paint paint){int color=paint.getColor()|0xff000000;Stroke stroke=new Stroke(color,Math.round(paint.getAlpha()*layerAlpha/255f),sx,sy,ex,ey,paint.getStrokeWidth());allStrokes.add(stroke);if(color==RED_ARROW||color==BLACK_ARROW)arrows.add(stroke);super.drawLine(sx,sy,ex,ey,paint);}
        @Override public void drawText(String text,float x,float y,Paint paint){
            textAlphas.add(paint.getAlpha());if(text.matches("[1-9·]+"))labels.add(text);
            if(text.length()==1&&text.charAt(0)>='\u4e00'&&text.charAt(0)<='\u9fff'){
                Paint.FontMetrics metrics=paint.getFontMetrics();float cy=y+(metrics.ascent+metrics.descent)/2;Path path=new Path();paint.getTextPath(text,0,text.length(),x,y,path);float extent=0;float[] points=path.approximate(.2f);for(int i=0;i+2<points.length;i+=3)extent=Math.max(extent,(float)Math.hypot(points[i+1]-x,points[i+2]-cy));glyphs.add(new GlyphSample(text,paint.getAlpha(),paint.getTextSize(),extent));
            }
            super.drawText(text,x,y,paint);
        }
    }
}
