package org.xiangqi.study.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Stable migration IDs and complete catalog metadata, independent of Android. */
public final class AppearancePresetsTest {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    public static void main(String[] ignored){
        String[] boards=AppearancePresets.boardIds(),discs=AppearancePresets.discIds();
        check(boards.length==20,"Twenty board presets");check(discs.length==18,"Eighteen piece presets");
        check(Arrays.equals(Arrays.copyOf(boards,6),new String[]{"moss","wood","ink","rosewood","slate","bamboo"}),"Original board preference IDs/order retained");
        check(Arrays.equals(Arrays.copyOf(discs,6),new String[]{"cream","wood","jade","porcelain","rosewood","obsidian"}),"Original disc preference IDs/order retained");
        check(new HashSet<>(Arrays.asList(boards)).size()==boards.length,"No duplicate board IDs");
        check(new HashSet<>(Arrays.asList(discs)).size()==discs.length,"No duplicate disc IDs");
        check(new HashSet<>(Arrays.asList(AppearancePresets.boardLabels())).size()==boards.length,"No duplicate board labels");
        check(new HashSet<>(Arrays.asList(AppearancePresets.discLabels())).size()==discs.length,"No duplicate disc labels");
        Set<Integer> boardColors=new HashSet<>(),discColors=new HashSet<>();
        for(String id:boards){
            AppearancePresets.Board board=AppearancePresets.board(id);check(id.equals(board.id),"Every board ID resolves");
            check(board.label.length()>2,"Every board has a readable label");check((board.background>>>24)==255&&(board.grid>>>24)==255,"Board is opaque");
            check(contrast(board.background,board.grid)>=3,"Grid and compact coordinates contrast at least3:1: "+id);
            check(board.texture>=AppearancePresets.FLAT&&board.texture<=AppearancePresets.STONE,"Recognized material");boardColors.add(board.background);
        }
        for(String id:discs){
            AppearancePresets.Disc disc=AppearancePresets.disc(id);check(id.equals(disc.id),"Every disc ID resolves");check(disc.label.length()>2,"Every disc has a readable label");
            check((disc.redBackground>>>24)==255&&(disc.blackBackground>>>24)==255,"Both piece backgrounds are opaque");
            check(disc.finish>=AppearancePresets.PLAIN&&disc.finish<=AppearancePresets.LACQUER,"Recognized finish");discColors.add(disc.redBackground);
        }
        check(boardColors.size()==boards.length,"All twenty board fills are distinct");check(discColors.size()==discs.length,"All eighteen Red disc fills are distinct");
        check(AppearancePresets.board(null).id.equals("moss")&&AppearancePresets.board("unknown").id.equals("moss"),"Unknown/missing board preference safely defaults");
        check(AppearancePresets.disc(null).id.equals("cream")&&AppearancePresets.disc("symbols").id.equals("cream"),"Unknown/unsupported symbols safely default to Chinese discs");
        boards[0]="changed";discs[0]="changed";String[] labels=AppearancePresets.boardLabels();labels[0]="changed";
        check(AppearancePresets.boardIds()[0].equals("moss")&&AppearancePresets.discIds()[0].equals("cream")&&AppearancePresets.boardLabels()[0].equals("Moss"),"Callers cannot mutate catalog arrays");
        check(AppearancePresets.disc("xiangqi-com").redInk==0xffffffff&&AppearancePresets.disc("xiangqi-com").blackInk==0xffffffff,"Xiangqi.com character discs use white glyphs");
        System.out.println("Appearance presets: "+assertions+" assertions passed");
    }
    private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    private static double luminance(int c){return .2126*channel((c>>16)&255)+.7152*channel((c>>8)&255)+.0722*channel(c&255);}
    private static double channel(int c){double s=c/255.0;return s<=.04045?s/12.92:Math.pow((s+.055)/1.055,2.4);}
}
