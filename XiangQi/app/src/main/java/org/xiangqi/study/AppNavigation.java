package org.xiangqi.study;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.*;
import java.util.function.Consumer;
import org.xiangqi.study.core.UiPalette;

/** The same compact navigation on play, database, options and recorded-game review. */
final class AppNavigation {
    private AppNavigation(){}
    static LinearLayout create(Context context,String selected,Consumer<String> action){
        UiPalette colors=AppTheme.load(context).colors;int ACCENT=colors.accent,MUTED=colors.muted;
        LinearLayout bar=new LinearLayout(context);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);
        String[] ids={"play","database","options"},labels={"Play","Database","Options"};
        for(int i=0;i<ids.length;i++){String id=ids[i];boolean active=id.equals(selected);Button b=new Button(context);b.setAllCaps(false);b.setText(labels[i]);b.setTextSize(11);b.setTextColor(active?ACCENT:MUTED);b.setBackgroundColor(Color.TRANSPARENT);b.setPadding(dp(context,4),dp(context,2),dp(context,4),dp(context,2));b.setMinWidth(0);b.setMinimumWidth(0);b.setSelected(active);b.setContentDescription(labels[i]+" tab");b.setCompoundDrawablesWithIntrinsicBounds(null,new TabIcon(context,id,active),null,null);b.setCompoundDrawablePadding(dp(context,3));b.setOnClickListener(v->action.accept(id));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(context,54),1);if(i>0)p.leftMargin=dp(context,8);bar.addView(b,p);}
        return bar;
    }
    private static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    private static final class TabIcon extends Drawable {
        final Context context;final boolean selected;final UiPalette colors;final AppIcon glyph;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        TabIcon(Context c,String name,boolean selected){context=c;this.selected=selected;colors=AppTheme.load(c).colors;glyph=new AppIcon(name,selected?colors.accent:colors.muted,dp(c,20));}
        @Override public void draw(Canvas canvas){Rect b=getBounds();if(selected){paint.setColor(colors.selected);canvas.drawRoundRect(b.left,b.top,b.right,b.bottom,dp(context,11),dp(context,11),paint);}int half=dp(context,10);glyph.setBounds(b.centerX()-half,b.centerY()-half,b.centerX()+half,b.centerY()+half);glyph.draw(canvas);}
        @Override public int getIntrinsicWidth(){return dp(context,52);}@Override public int getIntrinsicHeight(){return dp(context,28);}@Override public void setAlpha(int alpha){glyph.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){glyph.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
