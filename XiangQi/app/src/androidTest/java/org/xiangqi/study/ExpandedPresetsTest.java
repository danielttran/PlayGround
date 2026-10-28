package org.xiangqi.study;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.test.InstrumentationTestCase;
import android.view.View;
import org.xiangqi.study.core.AppearancePresets;
import org.xiangqi.study.core.Position;
import java.io.File;
import java.io.FileOutputStream;

/** Exercises every catalog entry through the real Canvas renderer. */
public final class ExpandedPresetsTest extends InstrumentationTestCase {
    public void testEveryExpandedStyleRendersReadableChinesePieces()throws Exception{
        String[] boards=AppearancePresets.boardIds(),discs=AppearancePresets.discIds();
        int entries=Math.max(boards.length,discs.length),columns=4,panelWidth=320,panelHeight=396;
        Bitmap gallery=Bitmap.createBitmap(columns*panelWidth,((entries+columns-1)/columns)*panelHeight,Bitmap.Config.ARGB_8888);
        Canvas galleryCanvas=new Canvas(gallery);galleryCanvas.drawColor(0xff1e2520);
        Paint galleryPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        File galleryFile=new File(getInstrumentation().getTargetContext().getCacheDir(),"preset-gallery.png");
        if(galleryFile.exists())assertTrue("Remove stale gallery before this audit",galleryFile.delete());
        try{
        for(int i=0;i<Math.max(boards.length,discs.length);i++){
            String boardStyle=boards[i%boards.length],discStyle=discs[i%discs.length];boolean flipped=i%2==1;
            final int[] glyphs={0};final double[] lowest={21};final Bitmap[] image={null};
            UiTestActions.runOnMain(getInstrumentation(),()->{
                BoardView board=new BoardView(getInstrumentation().getTargetContext());board.setAppearance(boardStyle,"traditional",discStyle);
                board.setPosition(Position.initial(),"",false);board.setFlipped(flipped);board.setCoordinates("numeric");
                int width=Math.round(320*getInstrumentation().getTargetContext().getResources().getDisplayMetrics().density);
                board.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                board.layout(0,0,board.getMeasuredWidth(),board.getMeasuredHeight());image[0]=Bitmap.createBitmap(board.getWidth(),board.getHeight(),Bitmap.Config.ARGB_8888);
                board.draw(new Canvas(image[0]){
                    @Override public void drawText(String text,float x,float y,Paint paint){
                        if(text.length()==1&&text.charAt(0)>='\u4e00'&&text.charAt(0)<='\u9fff'){
                            int fill=board.getPieceBackground(glyphs[0]>=16);glyphs[0]++;lowest[0]=Math.min(lowest[0],contrast(fill,paint.getColor()));
                        }
                        super.drawText(text,x,y,paint);
                    }
                });
            });
            try{
                assertEquals("Every disc remains Chinese text: "+discStyle,32,glyphs[0]);assertTrue("Readable glyph contrast: "+discStyle,lowest[0]>=4.5);
                int x=(i%columns)*panelWidth,y=(i/columns)*panelHeight;
                galleryPaint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));galleryPaint.setTextSize(13);galleryPaint.setColor(0xffe7e7d5);
                galleryCanvas.drawText((i+1)+". "+AppearancePresets.board(boardStyle).label,x+8,y+16,galleryPaint);
                galleryPaint.setTypeface(Typeface.SANS_SERIF);galleryPaint.setTextSize(11);galleryPaint.setColor(0xffb8c8b8);
                galleryCanvas.drawText(AppearancePresets.disc(discStyle).label,x+8,y+32,galleryPaint);
                galleryCanvas.drawBitmap(image[0],null,new Rect(x+8,y+42,x+312,y+376),galleryPaint);
            }finally{image[0].recycle();}
        }
        try(FileOutputStream output=new FileOutputStream(galleryFile)){assertTrue("Write actual-renderer preset gallery",gallery.compress(Bitmap.CompressFormat.PNG,100,output));}
        }finally{gallery.recycle();}
    }
    public void testCustomColorsRemainExactAcrossNewMaterials()throws Exception{
        for(String id:AppearancePresets.boardIds()){
            UiTestActions.runOnMain(getInstrumentation(),()->{
                BoardView board=new BoardView(getInstrumentation().getTargetContext());board.setAppearance(id,"simplified","xiangqi-com");
                board.setCustomColors(0xff123456,0xffeee0ce,0xff081522);
                assertEquals(0xff123456,board.getBoardColor());assertEquals(0xffeee0ce,board.getPieceBackground(true));assertEquals(0xff081522,board.getPieceBackground(false));
                board.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));board.layout(0,0,400,board.getMeasuredHeight());
                Bitmap image=Bitmap.createBitmap(board.getWidth(),board.getHeight(),Bitmap.Config.ARGB_8888);
                try{board.draw(new Canvas(image));assertEquals("Custom board color must suppress material tint: "+id,0xff123456,image.getPixel(200,board.getHeight()/2));}finally{image.recycle();}
            });
        }
    }
    private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    private static double luminance(int c){return .2126*channel((c>>16)&255)+.7152*channel((c>>8)&255)+.0722*channel(c&255);}
    private static double channel(int c){double s=c/255.0;return s<=.04045?s/12.92:Math.pow((s+.055)/1.055,2.4);}
}
