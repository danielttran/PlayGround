package org.xiangqi.study;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;

/** Persisted appearance and spoiler preferences, separate from game study state. */
public final class StudySettings {
    static final String PREFERENCES = "appearance";
    public final String boardStyle, glyphStyle, discStyle, coordinateStyle;
    public final boolean hideResults;
    private StudySettings(String board,String glyph,String disc,String coordinates,boolean hide) {
        boardStyle=choice(board,"moss","wood","ink");
        glyphStyle=choice(glyph,"traditional","simplified");
        discStyle=choice(disc,"cream","wood","jade");coordinateStyle=choice(coordinates,"none","iccs","numeric");hideResults=hide;
    }
    public static StudySettings load(Context context) {
        SharedPreferences p=context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE);
        return new StudySettings(p.getString("board","moss"),p.getString("glyph","traditional"),
            p.getString("disc","cream"),p.getString("coordinates","none"),p.getBoolean("hideResults",true));
    }
    public static void save(Context context,String board,String glyph,String disc,boolean hide) {
        save(context,board,glyph,disc,load(context).coordinateStyle,hide);
    }
    public static void save(Context context,String board,String glyph,String disc,String coordinates,boolean hide) {
        StudySettings s=new StudySettings(board,glyph,disc,coordinates,hide);
        context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).edit().putString("board",s.boardStyle)
            .putString("glyph",s.glyphStyle).putString("disc",s.discStyle).putString("coordinates",s.coordinateStyle).putBoolean("hideResults",s.hideResults).apply();
    }
    private static String choice(String value,String... choices) { return Arrays.asList(choices).contains(value)?value:choices[0]; }
}
