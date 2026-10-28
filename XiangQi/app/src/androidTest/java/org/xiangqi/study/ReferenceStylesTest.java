package org.xiangqi.study;

import android.graphics.*;
import android.test.InstrumentationTestCase;
import android.view.View;
import org.xiangqi.study.core.AppearancePresets;
import org.xiangqi.study.core.Position;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Locale;

/** Actual Canvas output for the four supplied reference looks, without URL text. */
public final class ReferenceStylesTest extends InstrumentationTestCase {
    private static final String[] IDS={"warm-maple","neon-violet","antique-gold","carved-honey"};
    public void testReferencePairsDrawChineseWithoutUrlsAndExportMatchedGallery()throws Exception{
        Bitmap gallery=Bitmap.createBitmap(840,1000,Bitmap.Config.ARGB_8888);Canvas output=new Canvas(gallery);output.drawColor(0xff1e2520);
        Paint title=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);title.setTextSize(18);title.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));title.setColor(0xffe7e7d5);
        File file=new File(getInstrumentation().getTargetContext().getCacheDir(),"reference-gallery.png");
        try{
            for(int n=0;n<IDS.length;n++){
                String id=IDS[n];final Bitmap[] bitmap={null};final int[] glyphs={0};final double[] lowest={21};
                getInstrumentation().runOnMainSync(()->{
                    BoardView board=board(id,false,"numeric");bitmap[0]=Bitmap.createBitmap(board.getWidth(),board.getHeight(),Bitmap.Config.ARGB_8888);
                    board.draw(new Canvas(bitmap[0]){
                        @Override public void drawText(String text,float x,float y,Paint paint){
                            String lower=text.toLowerCase(Locale.ROOT);
                            assertFalse("Board must not draw URL/domain text",lower.contains("http")||lower.contains("www.")||lower.contains("xiangqi.com"));
                            if(text.length()==1&&text.charAt(0)>='\u4e00'&&text.charAt(0)<='\u9fff'){
                                int fill=board.getPieceBackground(glyphs[0]>=16);glyphs[0]++;lowest[0]=Math.min(lowest[0],contrast(fill,paint.getColor()));
                            }
                            super.drawText(text,x,y,paint);
                        }
                    });
                });
                try{
                    assertEquals("Reference pieces are Chinese text: "+id,32,glyphs[0]);assertTrue("Reference glyph contrast: "+id,lowest[0]>=4.5);
                    int x=n%2*420,y=n/2*500;output.drawText(AppearancePresets.board(id).label,x+12,y+27,title);
                    output.drawBitmap(bitmap[0],null,new Rect(x+12,y+44,x+408,y+480),title);
                }finally{bitmap[0].recycle();}
            }
            try(FileOutputStream stream=new FileOutputStream(file)){assertTrue(gallery.compress(Bitmap.CompressFormat.PNG,100,stream));}
        }finally{gallery.recycle();}
    }
    public void testReferenceCoordinatesKeepGeometryAndCustomColorsStayExact()throws Exception{
        for(String id:IDS)for(boolean flipped:new boolean[]{false,true}){
            getInstrumentation().runOnMainSync(()->{
                BoardView off=board(id,flipped,"none"),coordinates=board(id,flipped,"numeric");
                Bitmap a=Bitmap.createBitmap(off.getWidth(),off.getHeight(),Bitmap.Config.ARGB_8888),b=Bitmap.createBitmap(coordinates.getWidth(),coordinates.getHeight(),Bitmap.Config.ARGB_8888);
                try{
                    off.draw(new Canvas(a));coordinates.draw(new Canvas(b));
                    for(String field:new String[]{"left","top","step","radius"})assertEquals("Coordinates must not move geometry: "+id,value(off,field),value(coordinates,field),.001f);
                    int boardFill=0xff123456,redFill=0xffe4d9c2,blackFill=0xff183629;
                    coordinates.setCustomColors(boardFill,redFill,blackFill);coordinates.draw(new Canvas(b));
                    assertEquals(boardFill,coordinates.getBoardColor());assertEquals(redFill,coordinates.getPieceBackground(true));assertEquals(blackFill,coordinates.getPieceBackground(false));
                    assertEquals("Custom board must suppress tint: "+id,boardFill,b.getPixel(b.getWidth()/2,b.getHeight()/2));
                    int[] pixels=new int[b.getWidth()*b.getHeight()];b.getPixels(pixels,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());int reds=0,blacks=0;
                    for(int pixel:pixels){if(pixel==redFill)reds++;if(pixel==blackFill)blacks++;}
                    assertTrue("Custom Red faces are exact opaque RGB: "+id,reds>4000);assertTrue("Custom Black faces are exact opaque RGB: "+id,blacks>4000);
                }finally{a.recycle();b.recycle();}
            });
        }
    }
    private BoardView board(String id,boolean flipped,String coordinates){
        BoardView board=new BoardView(getInstrumentation().getTargetContext());board.setAppearance(id,"traditional",id);board.setPosition(Position.initial(),"",false);board.setFlipped(flipped);board.setCoordinates(coordinates);
        int width=Math.round(393*getInstrumentation().getTargetContext().getResources().getDisplayMetrics().density);
        board.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));board.layout(0,0,board.getMeasuredWidth(),board.getMeasuredHeight());return board;
    }
    private static float value(BoardView board,String name){try{Field field=BoardView.class.getDeclaredField(name);field.setAccessible(true);return field.getFloat(board);}catch(Exception e){throw new RuntimeException(e);}}
    private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    private static double luminance(int c){return .2126*channel((c>>16)&255)+.7152*channel((c>>8)&255)+.0722*channel(c&255);}
    private static double channel(int c){double s=c/255.0;return s<=.04045?s/12.92:Math.pow((s+.055)/1.055,2.4);}
}
