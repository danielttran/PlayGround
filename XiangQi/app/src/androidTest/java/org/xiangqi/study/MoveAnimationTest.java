package org.xiangqi.study;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import org.xiangqi.study.core.*;
import java.lang.reflect.Field;

/** Compare actual rendered frames, including captures, reverse and interrupted moves. */
public final class MoveAnimationTest extends InstrumentationTestCase {
    public void testEveryPieceFinishSlidesCapturesAndReversesToTheExactFinalBoard(){for(String style:AppearancePresets.discIds())main(()->{
        Position start=Position.initial(),capture=start.play("b2b9");String original=start.toFen();
            BoardView view=board(style,1000);view.setPosition(start,"",false);Bitmap initial=draw(view);initial.recycle();view.setPosition(capture,"b2b9",false);
            Bitmap first=at(view,0),middle=at(view,500),end=at(view,1100);BoardView settled=board(style,0);settled.setPosition(capture,"b2b9",false);Bitmap expected=draw(settled);
            assertFalse("Piece must move between frames: "+style,first.sameAs(middle));assertFalse("Mid-slide differs from destination: "+style,middle.sameAs(end));assertTrue("Capture ends without ghosts: "+style,end.sameAs(expected));
            first.recycle();middle.recycle();end.recycle();expected.recycle();view.setPosition(start,"",false);middle=at(view,500);end=at(view,1100);settled.setPosition(start,"",false);expected=draw(settled);assertFalse(middle.sameAs(end));assertTrue("Reverse capture restores both pieces: "+style,end.sameAs(expected));middle.recycle();end.recycle();expected.recycle();
        assertEquals("Rendering never edits the game position",original,start.toFen());
    });}
    public void testRapidMovesFlipAndDisabledAnimationAlwaysShowTheLatestPosition(){main(()->{
        Position start=Position.initial(),one=start.play("b2e2"),two=one.play("b9c7");BoardView view=board("obsidian",1000);view.setPosition(start,"",false);Bitmap frame=draw(view);frame.recycle();view.setPosition(one,"b2e2",false);frame=at(view,500);frame.recycle();view.setPosition(two,"b9c7",false);Bitmap end=at(view,1100);BoardView expected=board("obsidian",0);expected.setPosition(two,"b9c7",false);Bitmap fixed=draw(expected);assertTrue("Interrupted move has no stale sprite",end.sameAs(fixed));end.recycle();fixed.recycle();
        view.setPosition(one,"b2e2",false);view.setFlipped(true);expected.setPosition(one,"b2e2",false);expected.setFlipped(true);end=draw(view);fixed=draw(expected);assertTrue("Flipping settles the current position",end.sameAs(fixed));end.recycle();fixed.recycle();view.setPosition(start,"",false);view.setMoveAnimationMillis(0);expected.setPosition(start,"",false);end=draw(view);fixed=draw(expected);assertTrue("Off is immediate",end.sameAs(fixed));end.recycle();fixed.recycle();
    });}
    private BoardView board(String disc,int millis){BoardView b=new TimedBoard(getInstrumentation().getTargetContext());b.setAppearance("warm-maple","traditional",disc);b.setMoveAnimationMillis(millis);b.measure(View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));b.layout(0,0,540,594);return b;}
    private Bitmap draw(BoardView b){Bitmap image=Bitmap.createBitmap(540,594,Bitmap.Config.ARGB_8888);b.draw(new Canvas(image));return image;}
    private Bitmap at(BoardView b,int elapsed){try{Field f=BoardView.class.getDeclaredField("slideStarted");f.setAccessible(true);((TimedBoard)b).now=f.getLong(b)+elapsed;}catch(Exception e){throw new RuntimeException(e);}return draw(b);}
    private static final class TimedBoard extends BoardView {long now=1000;TimedBoard(android.content.Context c){super(c);}@Override protected long animationTimeMillis(){return now;}}
    private void main(Runnable r){UiTestActions.runOnMain(getInstrumentation(),r);}
}
