package org.xiangqi.study;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

/** Opaque RGB picker with swatches, channels and an exact hexadecimal entry. */
public final class ColorPickerDialog {
    public interface Listener { void selected(Integer color); }
    private ColorPickerDialog() {}
    public static AlertDialog show(Activity activity,String title,int initial,Listener listener) {
        float density=activity.getResources().getDisplayMetrics().density;
        int padding=Math.round(18*density),light=0xffe7e7d5;
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(padding,padding,padding,padding);
        TextView preview=new TextView(activity);preview.setText(R.string.color_preview);preview.setTextSize(25);preview.setGravity(android.view.Gravity.CENTER);
        preview.setContentDescription("Selected color preview");body.addView(preview,new LinearLayout.LayoutParams(-1,Math.round(64*density)));
        EditText hex=new EditText(activity);hex.setSingleLine(true);hex.setTextColor(light);hex.setHintTextColor(0xffb8c8b8);
        hex.setHint("#RRGGBB");hex.setContentDescription("Hex color");hex.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});body.addView(hex,new LinearLayout.LayoutParams(-1,Math.round(52*density)));
        int[] selected={initial|0xff000000};boolean[] syncing={false};
        SeekBar[] sliders=new SeekBar[3];TextView[] labels=new TextView[3];String[] channelNames={"Red","Green","Blue"};
        for(int i=0;i<3;i++) {
            labels[i]=new TextView(activity);labels[i].setTextColor(light);body.addView(labels[i]);
            sliders[i]=new SeekBar(activity);sliders[i].setMax(255);sliders[i].setContentDescription(channelNames[i]+" channel");
            body.addView(sliders[i],new LinearLayout.LayoutParams(-1,Math.round(42*density)));
        }
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(activity,android.R.style.Theme_Material_Dialog_Alert).setTitle(title).setView(scroll)
            .setNegativeButton("Cancel",null).setNeutralButton("Use preset",(d,w)->listener.selected(null)).setPositiveButton("Apply",null).create();
        Runnable refresh=()->{
            syncing[0]=true;
            int[] values={Color.red(selected[0]),Color.green(selected[0]),Color.blue(selected[0])};
            for(int i=0;i<3;i++){sliders[i].setProgress(values[i]);labels[i].setText(activity.getString(R.string.color_channel,channelNames[i],values[i]));}
            String value=String.format(Locale.ROOT,"#%06X",selected[0]&0xffffff);
            if(!value.equalsIgnoreCase(hex.getText().toString())){hex.setText(value);hex.setSelection(hex.length());}
            hex.setError(null);preview.setBackgroundColor(selected[0]);preview.setTextColor(Color.luminance(selected[0])>.42?Color.BLACK:Color.WHITE);
            if(dialog.isShowing())dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            syncing[0]=false;
        };
        for(SeekBar slider:sliders)slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar view,int progress,boolean user){if(!syncing[0]&&user){selected[0]=Color.rgb(sliders[0].getProgress(),sliders[1].getProgress(),sliders[2].getProgress());refresh.run();}}
            public void onStartTrackingTouch(SeekBar view){}public void onStopTrackingTouch(SeekBar view){}
        });
        hex.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){
            if(syncing[0])return;String value=s.toString().trim();
            if(value.matches("#?[0-9a-fA-F]{6}")){selected[0]=(int)Long.parseLong(value.replace("#",""),16)|0xff000000;refresh.run();}
            else{hex.setError("Enter six hexadecimal digits, for example #245A3B");if(dialog.isShowing())dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);}
        }public void afterTextChanged(Editable value){}});
        int[] palette={0xff3f3b2a,0xffd4b580,0xffe8e6dc,0xff332019,0xff45515c,0xffc8cf9b,0xffe8debb,0xffc2d9c4,
            0xffa72d37,0xff171b22,0xfff2e9e0,0xff6d402e,0xff234d5b,0xff7f6ba1,0xffead37f,0xfff0f0f0};
        TextView swatchTitle=new TextView(activity);swatchTitle.setText(R.string.color_swatches);swatchTitle.setTextColor(light);swatchTitle.setPadding(0,padding,0,0);body.addView(swatchTitle);
        for(int row=0;row<4;row++) {
            LinearLayout line=new LinearLayout(activity);body.addView(line);
            for(int column=0;column<4;column++) {
                int color=palette[row*4+column];Button swatch=new Button(activity);String name=String.format(Locale.ROOT,"#%06X",color&0xffffff);
                swatch.setText(name.substring(1));swatch.setTextSize(10);swatch.setAllCaps(false);swatch.setContentDescription("Choose color "+name);
                GradientDrawable background=new GradientDrawable();background.setColor(color);background.setCornerRadius(7*density);background.setStroke(Math.round(density),0xffa0a0a0);swatch.setBackground(background);
                swatch.setTextColor(Color.luminance(color)>.42?Color.BLACK:Color.WHITE);swatch.setPadding(0,0,0,0);
                LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,Math.round(48*density),1);layout.setMargins(Math.round(2*density),Math.round(3*density),Math.round(2*density),Math.round(3*density));line.addView(swatch,layout);
                swatch.setOnClickListener(view->{selected[0]=color;refresh.run();});
            }
        }
        dialog.setOnShowListener(d->{refresh.run();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{listener.selected(selected[0]);dialog.dismiss();});});
        dialog.show();return dialog;
    }
}
