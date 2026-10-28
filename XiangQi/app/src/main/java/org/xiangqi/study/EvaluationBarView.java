package org.xiangqi.study;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import org.xiangqi.study.core.AnalysisVisuals;
import java.util.Locale;

/** Slim, Red-relative evaluation rail; off and pending never retain an old score. */
public final class EvaluationBarView extends View {
    static final int RED=0xffcf554a,BLACK=0xff202b33,NEUTRAL=0xff657169;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip=new Path();
    private boolean active,flipped;
    private Integer score;
    public EvaluationBarView(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setVisibility(GONE);describe();}
    public void setEvaluation(boolean active,Integer score){this.active=active;this.score=active?score:null;setVisibility(active?VISIBLE:GONE);describe();invalidate();}
    public void setFlipped(boolean flipped){this.flipped=flipped;describe();invalidate();}
    private void describe(){
        String state=!active?"Analysis off. Neutral.":score==null?"Analysis on. Waiting for an evaluation.":String.format(Locale.getDefault(),"Red perspective %+.2f. %s",score/100.0,score>0?"Red advantage.":score<0?"Black advantage.":"Equal evaluation.");
        setContentDescription("Evaluation bar. "+state+" Red at the "+(flipped?"top":"bottom")+". Black at the "+(flipped?"bottom":"top")+".");
    }
    // MATCH_PARENT in the horizontal board row gets its final exact height in
    // LinearLayout's uniform-height pass. Never make a narrow board row taller.
    @Override protected void onMeasure(int widthSpec,int heightSpec){float d=getResources().getDisplayMetrics().density;setMeasuredDimension(resolveSize(Math.round(16*d),widthSpec),resolveSize(0,heightSpec));}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float width=getWidth(),height=getHeight(),density=getResources().getDisplayMetrics().density;
        if(width<=0||height<=0)return;
        clip.reset();clip.addRoundRect(0,0,width,height,Math.min(width*.35f,3*density),Math.min(width*.35f,3*density),Path.Direction.CW);
        int save=canvas.save();canvas.clipPath(clip);
        if(!active){paint.setColor(NEUTRAL);canvas.drawRect(0,0,width,height,paint);}
        else{
            float fraction=(float)AnalysisVisuals.redFraction(true,score),split=height*(flipped?fraction:1-fraction);
            paint.setColor(flipped?RED:BLACK);canvas.drawRect(0,0,width,split,paint);
            paint.setColor(flipped?BLACK:RED);canvas.drawRect(0,split,width,height,paint);
            paint.setColor(0xfff3eddf);paint.setStrokeWidth(Math.max(1,density));canvas.drawLine(0,split,width,split,paint);
        }
        // A fixed middle tick lets users compare the split with equality.
        paint.setColor(Color.WHITE);paint.setAlpha(active?170:115);paint.setStrokeWidth(Math.max(1,density));
        canvas.drawLine(0,height*.5f,width*.28f,height*.5f,paint);canvas.drawLine(width*.72f,height*.5f,width,height*.5f,paint);
        paint.setAlpha(255);canvas.restoreToCount(save);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,density*.7f));paint.setColor(0xff93a193);
        canvas.drawRoundRect(.5f,.5f,width-.5f,height-.5f,Math.min(width*.35f,3*density),Math.min(width*.35f,3*density),paint);paint.setStyle(Paint.Style.FILL);
    }
}
