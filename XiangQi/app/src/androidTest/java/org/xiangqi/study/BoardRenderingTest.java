package org.xiangqi.study;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.test.InstrumentationTestCase;
import android.view.View;
import org.xiangqi.study.core.Position;
import java.util.function.Consumer;
import java.util.HashSet;
import java.util.Set;

/** Pixel-level regressions for coordinate sizing, independent fills and turn cues. */
public final class BoardRenderingTest extends InstrumentationTestCase {
    public void testCoordinatesDoNotShrinkOrMoveTheRenderedBoard() throws Exception {
        for(int width:new int[]{320,393})for(boolean flipped:new boolean[]{false,true}) {
            Bitmap off=render(width,Position.initial(),flipped,"none",b->{});
            Bitmap iccs=render(width,Position.initial(),flipped,"iccs",b->{});
            Bitmap numeric=render(width,Position.initial(),flipped,"numeric",b->{});
            try {
                assertEquals(off.getWidth(),iccs.getWidth());assertEquals(off.getHeight(),iccs.getHeight());
                int[] plain=pixels(off),letters=pixels(iccs),numbers=pixels(numeric);
                int rim=dp(16),w=off.getWidth(),h=off.getHeight();int changed=0;
                for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                    int i=y*w+x;if(plain[i]!=letters[i])changed++;
                    if(x>=rim&&x<w-rim&&y>=rim&&y<h-rim) {
                        if(plain[i]!=letters[i])fail("Coordinates moved a grid/piece pixel at "+x+","+y);
                        if(plain[i]!=numbers[i])fail("Numeric coordinates moved a grid/piece pixel at "+x+","+y);
                    }
                }
                assertTrue("Enabled coordinates must actually be drawn",changed>50);
            } finally {off.recycle();iccs.recycle();numeric.recycle();}
        }
    }

    public void testAllBoardAndPiecePresetsProduceDistinctSurfaces() throws Exception {
        Set<Integer> boards=new HashSet<>(),pieces=new HashSet<>();
        for(String style:new String[]{"moss","wood","ink","rosewood","slate","bamboo"}) {
            Bitmap bitmap=render(320,Position.initial(),false,"none",b->{b.setAppearance(style,"traditional","cream");boards.add(b.getBoardColor());});
            try{assertTrue(count(bitmap,bitmap.getPixel(bitmap.getWidth()/2,bitmap.getHeight()/2))>1000);}finally{bitmap.recycle();}
        }
        for(String style:new String[]{"cream","wood","jade","porcelain","rosewood","obsidian"}) {
            final int[] color={0};Bitmap bitmap=render(320,Position.initial(),false,"none",b->{b.setAppearance("moss","simplified",style);color[0]=b.getPieceBackground(true);pieces.add(color[0]);});
            try{assertTrue("Piece preset must affect rendered discs",count(bitmap,color[0])>1000);}finally{bitmap.recycle();}
        }
        assertEquals(6,boards.size());assertEquals(6,pieces.size());
    }

    public void testCustomColorsKeepChineseTextReadable() throws Exception {
        int[] colors={0xff000000,0xffffffff,0xff808080,0xffff0000,0xff00ff00,0xff0000ff};
        for(int fill:colors){
            final double[] contrast={21};final int[] glyphs={0};final Bitmap[] image={null};
            UiTestActions.runOnMain(getInstrumentation(),()->{
                BoardView board=new BoardView(getInstrumentation().getTargetContext());board.setAppearance("slate","simplified","obsidian");
                board.setCustomColors(0xff454545,fill,fill);board.setPosition(Position.initial(),"",false);
                board.measure(View.MeasureSpec.makeMeasureSpec(dp(320),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                board.layout(0,0,board.getMeasuredWidth(),board.getMeasuredHeight());image[0]=Bitmap.createBitmap(board.getWidth(),board.getHeight(),Bitmap.Config.ARGB_8888);
                Canvas recording=new Canvas(image[0]){
                    @Override public void drawText(String text,float x,float y,Paint paint){
                        if(text.length()==1&&text.charAt(0)>='\u4e00'&&text.charAt(0)<='\u9fff'){
                            glyphs[0]++;contrast[0]=Math.min(contrast[0],contrastRatio(fill,paint.getColor()));
                        }
                        super.drawText(text,x,y,paint);
                    }
                };
                board.draw(recording);
            });
            try{assertEquals("Every piece is rendered as Chinese text",32,glyphs[0]);assertTrue("Custom fill reduced glyph contrast below4.5:1",contrast[0]>=4.5);}finally{image[0].recycle();}
        }
    }

    public void testCustomBackgroundsAreOpaqueIndependentAndResetIndividually() throws Exception {
        final int board=0xff123456,red=0xffec6723,black=0xff31acdd;
        Bitmap custom=render(393,Position.initial(),false,"numeric",b->b.setCustomColors(0x00123456,0x00ec6723,0x0031acdd));
        Bitmap partialReset=render(393,Position.initial(),false,"numeric",b->{
            b.setAppearance("wood","traditional","jade");b.setCustomColors(board,red,black);
            b.setCustomColors(null,red,null);
        });
        try {
            assertEquals("The board picker must affect the actual canvas",board,custom.getPixel(custom.getWidth()/2,custom.getHeight()/2));
            assertTrue(count(custom,red)>1000);assertTrue(count(custom,black)>1000);
            assertFillInsideCanvas(custom,red);assertFillInsideCanvas(custom,black);
            assertEquals("Resetting board color restores the selected preset",0xffd4b580,partialReset.getPixel(partialReset.getWidth()/2,partialReset.getHeight()/2));
            assertTrue("Reset must preserve the independent Red override",count(partialReset,red)>1000);
            assertTrue("Resetting Black restores the current Jade finish",count(partialReset,0xffc2d9c4)>1000);
            assertEquals(0,count(partialReset,black));
        } finally {custom.recycle();partialReset.recycle();}
    }

    public void testTurnIndicatorChangesEndsWithoutChangingBoardSize() throws Exception {
        Position redTurn=Position.initial(),blackTurn=redTurn.play("b2e2");
        Bitmap red=render(393,redTurn,false,"iccs",b->{}),black=render(393,blackTurn,false,"iccs",b->{});
        Bitmap redFlipped=render(393,redTurn,true,"iccs",b->{}),blackFlipped=render(393,blackTurn,true,"iccs",b->{});
        try {
            assertEquals(red.getWidth(),black.getWidth());assertEquals(red.getHeight(),black.getHeight());
            int x=red.getWidth()-dp(8),top=dp(8),bottom=red.getHeight()-dp(8);
            assertEquals(0xffd84d42,red.getPixel(x,bottom));assertNotSameColor(0xffd84d42,red.getPixel(x,top));
            assertEquals(0xff162128,black.getPixel(x,top));assertNotSameColor(0xff162128,black.getPixel(x,bottom));
            assertEquals(0xffd84d42,redFlipped.getPixel(x,top));assertEquals(0xff162128,blackFlipped.getPixel(x,bottom));
        } finally {red.recycle();black.recycle();redFlipped.recycle();blackFlipped.recycle();}
    }

    private Bitmap render(int widthDp,Position position,boolean flipped,String coordinates,Consumer<BoardView> setup) {
        final Bitmap[] image={null};UiTestActions.runOnMain(getInstrumentation(),()->{
            BoardView board=new BoardView(getInstrumentation().getTargetContext());setup.accept(board);
            board.setPosition(position,"",false);board.setFlipped(flipped);board.setCoordinates(coordinates);
            board.measure(View.MeasureSpec.makeMeasureSpec(dp(widthDp),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            board.layout(0,0,board.getMeasuredWidth(),board.getMeasuredHeight());
            image[0]=Bitmap.createBitmap(board.getWidth(),board.getHeight(),Bitmap.Config.ARGB_8888);board.draw(new Canvas(image[0]));
        });return image[0];
    }
    private int dp(int value){return Math.round(value*getInstrumentation().getTargetContext().getResources().getDisplayMetrics().density);}
    private static int[] pixels(Bitmap bitmap){int[] data=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(data,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());return data;}
    private static int count(Bitmap bitmap,int color){int found=0;for(int pixel:pixels(bitmap))if(pixel==color)found++;return found;}
    private void assertFillInsideCanvas(Bitmap bitmap,int color){
        int w=bitmap.getWidth(),h=bitmap.getHeight(),rim=dp(2);int[] data=pixels(bitmap);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(data[y*w+x]==color)
            assertTrue("Outer piece fill was clipped",x>=rim&&x<w-rim&&y>=rim&&y<h-rim);
    }
    private static void assertNotSameColor(int unexpected,int actual){assertTrue(unexpected!=actual);}
    private static double contrastRatio(int first,int second){double a=luminance(first),b=luminance(second);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);}
    private static double luminance(int color){return .2126*linear((color>>16)&255)+.7152*linear((color>>8)&255)+.0722*linear(color&255);}
    private static double linear(int channel){double value=channel/255.0;return value<=.04045?value/12.92:Math.pow((value+.055)/1.055,2.4);}
}
