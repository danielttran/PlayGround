package org.xiangqi.study;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Persisted native analysis resource and search limits. No strength handicap is applied. */
public final class EngineSettings {
    private static final String PREFS = "engine-settings";
    private EngineSettings() {}
    private static int defaultHash(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return 128;
        if (manager.isLowRamDevice()) return 64;
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo(); manager.getMemoryInfo(memory);
        return memory.totalMem >= 4L * 1024 * 1024 * 1024 ? 256 : 128;
    }
    public static PikafishEngine.Options load(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        return new PikafishEngine.Options(128,0,cores,defaultHash(context),p.getInt("lines",1));
    }
    /** Finite tasks use the same unrestricted engine, with an automatic time budget. */
    public static PikafishEngine.Options timed(Context context,long millis,int lines){
        PikafishEngine.Options maximum=load(context);return new PikafishEngine.Options(128,millis,maximum.threads,maximum.hashMb,lines);
    }
    /** Caller retains the dialog and dismisses it when the activity is destroyed. */
    public static AlertDialog show(Activity activity, Runnable changed) {
        PikafishEngine.Options current=load(activity);
        LinearLayout content=new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        int pad=Math.round(20*activity.getResources().getDisplayMetrics().density); content.setPadding(pad,pad,pad,pad);
        TextView note=new TextView(activity);
        content.addView(note);
        note.setText("Pikafish "+PikafishRelease.VERSION+" always analyzes at maximum strength. Resources are managed automatically. Computer difficulty affects playing only.");
        Spinner lines=spinner(activity,content,"Analysis lines",new String[]{"1 line per side","2 lines per side","3 lines per side","4 lines per side","5 lines per side"},current.lines-1);
        TextView arrows=new TextView(activity);arrows.setText(R.string.analysis_arrows_help);content.addView(arrows);
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        return new AlertDialog.Builder(activity).setTitle("Analysis settings").setView(scroll)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",(dialog,which)->{
                activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                    .putInt("lines",lines.getSelectedItemPosition()+1).apply();
                if(changed!=null)changed.run();
            }).show();
    }
    private static Spinner spinner(Activity activity,LinearLayout parent,String label,String[] choices,int selected){
        TextView title=new TextView(activity);title.setText(label);title.setPadding(0,24,0,4);parent.addView(title);
        Spinner spinner=new Spinner(activity);spinner.setContentDescription(label);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,choices);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);spinner.setSelection(Math.max(0,selected));parent.addView(spinner,new LinearLayout.LayoutParams(-1,-2));return spinner;
    }
    private static String[] strings(int[] values,String suffix){String[] labels=new String[values.length];for(int i=0;i<values.length;i++)labels[i]=values[i]+suffix;return labels;}
    private static int index(int[] values,int current){for(int i=0;i<values.length;i++)if(values[i]==current)return i;return values.length-1;}
    private static int index(long[] values,long current){for(int i=0;i<values.length;i++)if(values[i]==current)return i;return 0;}
}
