package org.xiangqi.study.core;

/** App chrome colors, independent of the board and Chinese piece palettes. */
public final class UiPalette {
    public final String id,label;
    public final boolean dark;
    public final int background,surface,soft,text,muted,line,accent,onAccent,selected,red,black;
    private UiPalette(String id,String label,boolean dark,int... c){
        this.id=id;this.label=label;this.dark=dark;background=c[0];surface=c[1];soft=c[2];text=c[3];muted=c[4];line=c[5];accent=c[6];onAccent=c[7];selected=c[8];
        red=dark?0xfff59da8:0xffa31b34;black=dark?0xffdce0e8:0xff283440;
    }
    private static final UiPalette[] VALUES={
        new UiPalette("cobalt","Cobalt",false,0xfff4f6fa,0xffffffff,0xffe7ebf4,0xff182030,0xff566276,0xffd6dce7,0xff2e58b5,0xffffffff,0xffe4ecff),
        new UiPalette("cobalt","Cobalt",true,0xff11151d,0xff1c2230,0xff293144,0xffedf2fc,0xffaab5c8,0xff364154,0xffa9c4ff,0xff16284b,0xff293c60),
        new UiPalette("amber","Amber",false,0xfff7f7f8,0xffffffff,0xffeaecef,0xff24272d,0xff5a606a,0xffd8dce0,0xff8a5900,0xffffffff,0xfff8edcf),
        new UiPalette("amber","Amber",true,0xff18191c,0xff24262b,0xff303238,0xfff1f1f3,0xffb8bac2,0xff41454e,0xfff0c46e,0xff352700,0xff4c3e25),
        new UiPalette("plum","Plum",false,0xfff8f4fa,0xffffffff,0xffefe7f4,0xff2d2035,0xff6c5879,0xffdfd3e6,0xff754096,0xffffffff,0xfff0e3fa),
        new UiPalette("plum","Plum",true,0xff1a141f,0xff29212f,0xff392f41,0xfff4ecfa,0xffc3b0cf,0xff4b3b56,0xffd6afea,0xff3c204d,0xff4a305c),
        new UiPalette("graphite","Graphite",false,0xfff6f5f2,0xffffffff,0xffeae8e2,0xff292725,0xff67635e,0xffd8d5cf,0xff373b42,0xffffffff,0xffe5e6e8),
        new UiPalette("graphite","Graphite",true,0xff161616,0xff242424,0xff333333,0xfff2f1ee,0xffbcb9b4,0xff464646,0xffdedbd5,0xff252525,0xff3f3e3b)
    };
    public static String[] ids(){return new String[]{"cobalt","amber","plum","graphite"};}
    public static UiPalette get(String id,boolean dark){for(UiPalette p:VALUES)if(p.id.equals(id)&&p.dark==dark)return p;return VALUES[dark?1:0];}
}
