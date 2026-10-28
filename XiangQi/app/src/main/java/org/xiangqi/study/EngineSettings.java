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
        return new PikafishEngine.Options(p.getInt("depth",128), p.getLong("millis",0),
            p.getInt("threads",cores), p.getInt("hashMb",defaultHash(context)));
    }
    /** Caller retains the dialog and dismisses it when the activity is destroyed. */
    public static AlertDialog show(Activity activity, Runnable changed) {
        PikafishEngine.Options current=load(activity);
        int[] depths={12,18,24,30,40,64,128}, hashes={64,128,256,512};
        long[] times={0,3000,10000,30000,60000};
        LinearLayout content=new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        int pad=Math.round(20*activity.getResources().getDisplayMetrics().density); content.setPadding(pad,pad,pad,pad);
        TextView note=new TextView(activity);
        note.setText(activity.getString(R.string.engine_settings_summary,PikafishRelease.VERSION));
        content.addView(note);
        Spinner depth=spinner(activity,content,"Maximum depth",strings(depths,""),index(depths,current.maxDepth));
        Spinner time=spinner(activity,content,"Time per position",new String[]{"Until stopped / depth limit","3 seconds","10 seconds","30 seconds","60 seconds"},index(times,current.millis));
        List<Integer> coreValues=new ArrayList<>();int cores=Math.max(1,Runtime.getRuntime().availableProcessors());
        for(int i=1;i<=cores;i++)coreValues.add(i);
        String[] coreLabels=new String[coreValues.size()];for(int i=0;i<coreLabels.length;i++)coreLabels[i]=coreValues.get(i)+(i+1==cores?" (all available cores)":"");
        Spinner threads=spinner(activity,content,"Engine threads",coreLabels,Math.min(current.threads,cores)-1);
        Spinner hash=spinner(activity,content,"Hash memory",strings(hashes," MiB"),index(hashes,current.hashMb));
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        return new AlertDialog.Builder(activity).setTitle("Analysis settings").setView(scroll)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",(dialog,which)->{
                activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                    .putInt("depth",depths[depth.getSelectedItemPosition()]).putLong("millis",times[time.getSelectedItemPosition()])
                    .putInt("threads",coreValues.get(threads.getSelectedItemPosition())).putInt("hashMb",hashes[hash.getSelectedItemPosition()]).apply();
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
