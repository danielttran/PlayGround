package org.xiangqi.study.core;

import java.util.*;

/** Readability across all light/dark palette pairs, including selected states. */
public final class UiPaletteTest {
    private static int assertions;
    public static void main(String[] args){
        Set<Integer> lightAccents=new HashSet<>();
        for(String id:UiPalette.ids())for(boolean dark:new boolean[]{false,true}){
            UiPalette p=UiPalette.get(id,dark);if(!dark)lightAccents.add(p.accent);
            check(p.id.equals(id)&&p.dark==dark,"Palette selection");
            for(int surface:new int[]{p.background,p.surface,p.selected}){
                check(contrast(p.text,surface)>=7,"Primary text "+id);
                check(contrast(p.muted,surface)>=4.5,"Secondary text "+id);
                check(contrast(p.red,surface)>=4.5,"Red player label "+id);
                check(contrast(p.black,surface)>=4.5,"Black player label "+id);
            }
            check(contrast(p.accent,p.background)>=4.5,"Accent on page "+id);
            check(contrast(p.accent,p.surface)>=4.5,"Accent on panel "+id);
            check(contrast(p.accent,p.selected)>=4.5,"Accent on selection "+id);
            check(contrast(p.onAccent,p.accent)>=4.5,"Primary button text "+id);
        }
        check(lightAccents.size()==4,"Themes need distinct main colors");
        check(UiPalette.get("unknown",false)==UiPalette.get("cobalt",false),"Safe light fallback");
        check(UiPalette.get(null,true)==UiPalette.get("cobalt",true),"Safe dark fallback");
        System.out.println("UI palettes: "+assertions+" contrast and selection assertions passed");
    }
    private static double luminance(int color){double[] c={(color>>16&255)/255.0,(color>>8&255)/255.0,(color&255)/255.0};for(int i=0;i<3;i++)c[i]=c[i]<=.04045?c[i]/12.92:Math.pow((c[i]+.055)/1.055,2.4);return c[0]*.2126+c[1]*.7152+c[2]*.0722;}
    private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
}
