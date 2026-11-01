package org.xiangqi.study;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import java.util.Arrays;
import org.xiangqi.study.core.UiPalette;

/** Persisted app theme; board colors and engine settings have separate keys. */
final class AppTheme {
    static final String MODE="uiMode",PALETTE="uiPalette";
    final String mode;final UiPalette colors;
    private AppTheme(String mode,UiPalette colors){this.mode=mode;this.colors=colors;}
    static AppTheme load(Context context){
        android.content.SharedPreferences prefs=context.getSharedPreferences(StudySettings.PREFERENCES,Context.MODE_PRIVATE);
        String mode=prefs.getString(MODE,"dark");if(!Arrays.asList("system","light","dark").contains(mode))mode="dark";
        boolean dark="dark".equals(mode)||"system".equals(mode)&&(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        return new AppTheme(mode,UiPalette.get(prefs.getString(PALETTE,"cobalt"),dark));
    }
    static void setMode(Context context,String mode){if(!Arrays.asList("system","light","dark").contains(mode))throw new IllegalArgumentException("Unknown display mode");context.getSharedPreferences(StudySettings.PREFERENCES,Context.MODE_PRIVATE).edit().putString(MODE,mode).apply();}
    static void setPalette(Context context,String palette){if(!Arrays.asList(UiPalette.ids()).contains(palette))throw new IllegalArgumentException("Unknown app theme");context.getSharedPreferences(StudySettings.PREFERENCES,Context.MODE_PRIVATE).edit().putString(PALETTE,palette).apply();}
    boolean matches(AppTheme other){return mode.equals(other.mode)&&colors.id.equals(other.colors.id)&&colors.dark==other.colors.dark;}
    void apply(Activity activity){
        int style;
        switch(colors.id){
            case "amber":style=colors.dark?R.style.AppTheme_Amber_Dark:R.style.AppTheme_Amber_Light;break;
            case "plum":style=colors.dark?R.style.AppTheme_Plum_Dark:R.style.AppTheme_Plum_Light;break;
            case "graphite":style=colors.dark?R.style.AppTheme_Graphite_Dark:R.style.AppTheme_Graphite_Light;break;
            default:style=colors.dark?R.style.AppTheme_Cobalt_Dark:R.style.AppTheme_Cobalt_Light;
        }activity.setTheme(style);
    }
    void decorate(Activity activity){
        Window window=activity.getWindow();window.setBackgroundDrawable(new ColorDrawable(colors.background));window.setStatusBarColor(colors.background);window.setNavigationBarColor(colors.background);
        View decor=window.getDecorView();int mask=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;decor.setSystemUiVisibility(colors.dark?decor.getSystemUiVisibility()&~mask:decor.getSystemUiVisibility()|mask);
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=window.getInsetsController();if(controller!=null){int appearance=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;controller.setSystemBarsAppearance(colors.dark?0:appearance,appearance);}}
    }
}
