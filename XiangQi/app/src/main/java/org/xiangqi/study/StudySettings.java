package org.xiangqi.study;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;

/** Persisted appearance and spoiler preferences, separate from game study state. */
public final class StudySettings {
    static final String PREFERENCES = "appearance";
    public final String boardStyle, glyphStyle, discStyle, coordinateStyle;
    public final boolean hideResults;
    public final Integer boardColor, redBackground, blackBackground;
    private StudySettings(String board,String glyph,String disc,String coordinates,boolean hide,Integer customBoard,Integer customRed,Integer customBlack) {
        boardStyle=choice(board,"moss","wood","ink","rosewood","slate","bamboo");
        glyphStyle=choice(glyph,"traditional","simplified");
        discStyle=choice(disc,"cream","wood","jade","porcelain","rosewood","obsidian");coordinateStyle=choice(coordinates,"none","iccs","numeric");hideResults=hide;
        boardColor=opaque(customBoard);redBackground=opaque(customRed);blackBackground=opaque(customBlack);
    }
    public static StudySettings load(Context context) {
        SharedPreferences p=context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE);
        return new StudySettings(p.getString("board","moss"),p.getString("glyph","traditional"),
            p.getString("disc","cream"),p.getString("coordinates","none"),p.getBoolean("hideResults",true),
            custom(p,"boardColor"),custom(p,"redBackground"),custom(p,"blackBackground"));
    }
    public static void save(Context context,String board,String glyph,String disc,boolean hide) {
        save(context,board,glyph,disc,load(context).coordinateStyle,hide);
    }
    public static void save(Context context,String board,String glyph,String disc,String coordinates,boolean hide) {
        StudySettings old=load(context);
        StudySettings s=new StudySettings(board,glyph,disc,coordinates,hide,old.boardColor,old.redBackground,old.blackBackground);
        context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).edit().putString("board",s.boardStyle)
            .putString("glyph",s.glyphStyle).putString("disc",s.discStyle).putString("coordinates",s.coordinateStyle).putBoolean("hideResults",s.hideResults).apply();
    }
    public static void setCustomColor(Context context,String surface,Integer color) {
        if(!Arrays.asList("boardColor","redBackground","blackBackground").contains(surface))throw new IllegalArgumentException("Unknown color surface");
        SharedPreferences.Editor edit=context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).edit();
        if(color==null)edit.remove(surface);else edit.putInt(surface,opaque(color));edit.apply();
    }
    public static void resetCustomColors(Context context) {
        context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).edit().remove("boardColor").remove("redBackground").remove("blackBackground").apply();
    }
    private static Integer custom(SharedPreferences preferences,String key){return preferences.contains(key)?preferences.getInt(key,0):null;}
    private static Integer opaque(Integer color){return color==null?null:color|0xff000000;}
    private static String choice(String value,String... choices) { return Arrays.asList(choices).contains(value)?value:choices[0]; }
}
