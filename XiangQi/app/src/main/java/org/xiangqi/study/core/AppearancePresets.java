package org.xiangqi.study.core;

/** Stable appearance IDs shared by settings and the Canvas renderer. */
public final class AppearancePresets {
    public static final int FLAT=0, WOOD=1, PAPER=2, STONE=3;
    public static final int PLAIN=0, ENGRAVED=1, GLAZED=2, LACQUER=3;
    public static final class Board {
        public final String id,label;
        public final int background,grid,playedArrow,bestArrow,border,texture;
        private Board(String id,String label,int background,int grid,int played,int best,int border,int texture){
            this.id=id;this.label=label;this.background=background;this.grid=grid;
            playedArrow=played;bestArrow=best;this.border=border;this.texture=texture;
        }
    }
    public static final class Disc {
        public final String id,label;
        public final int redBackground,blackBackground,redInk,blackInk,finish;
        private Disc(String id,String label,int redBackground,int blackBackground,int redInk,int blackInk,int finish){
            this.id=id;this.label=label;this.redBackground=redBackground;this.blackBackground=blackBackground;
            this.redInk=redInk;this.blackInk=blackInk;this.finish=finish;
        }
    }
    // The first six IDs retain the 0.3 selections and colors.
    private static final Board[] BOARDS={
        b("moss","Moss",0xff3f3b2a,0xff9f9065,0xffd7ae5c,0xffb3dea5,0,FLAT),
        b("wood","Wood",0xffd4b580,0xff785b36,0xff9a541e,0xff1b784e,0,FLAT),
        b("ink","Ink",0xffe8e6dc,0xff817c6c,0xffb56a29,0xff266c8a,0,FLAT),
        b("rosewood","Rosewood",0xff582e2a,0xffc99871,0xffffc476,0xffa6ebbe,0,FLAT),
        b("slate","Slate",0xff263843,0xffa4b7bd,0xffffc27e,0xffb6edca,0,FLAT),
        b("bamboo","Bamboo",0xffc1d29e,0xff627244,0xff9d4b25,0xff17634c,0,FLAT),
        b("xiangqi-paper","Xiangqi.com · Paper",0xffebe8df,0xff837f75,0xffa64a24,0xff216953,0xffc7c2b5,PAPER),
        b("xiangqi-maple","Xiangqi.com · Maple",0xfff7dba9,0xff996d3e,0xff9b431e,0xff186448,0xffdd7440,WOOD),
        b("classic-gold","Classic golden wood",0xffe6bc76,0xff795027,0xff9f3520,0xff175e48,0xff6a3e20,WOOD),
        b("sand","Sand parchment",0xffe8d9b5,0xff8b754f,0xffa04d25,0xff246e57,0xffb79b68,PAPER),
        b("walnut","Walnut",0xff74503b,0xffd0b18b,0xffffc77a,0xffb1e6cb,0xff472e23,WOOD),
        b("mahogany","Dark mahogany",0xff402a23,0xffc59c73,0xffffcb86,0xffb4e4ce,0xffb98c61,WOOD),
        b("one-ivory","Xiangqi One · Ivory",0xffefede6,0xff848278,0xffb44c2d,0xff2b745d,0xffde7657,FLAT),
        b("mist","Blue mist",0xffdce3e6,0xff667c88,0xffa04a2d,0xff286954,0xff9daeb3,STONE),
        b("jade-stone","Jade stone",0xffb7cfc0,0xff496e60,0xffa14129,0xff245c53,0xff7fa390,STONE),
        b("forest","Forest",0xff203b32,0xff92ac8c,0xffffca88,0xffb6e5df,0xff647f58,FLAT),
        b("midnight","Midnight",0xff16202e,0xff7f9aaf,0xffffca8a,0xffa7e2ce,0xff496b8a,FLAT),
        b("navy","Deep navy",0xff24364c,0xffa8bfca,0xffffc691,0xffb8ebd7,0xff5b7c96,FLAT),
        b("lavender","Lavender paper",0xffdcd8e6,0xff81728e,0xffa54c2c,0xff326650,0xffa89cb8,PAPER),
        b("terracotta","Terracotta",0xffbd8464,0xff573e2d,0xff631f19,0xff164c3b,0xff835338,STONE)
    };
    private static final Disc[] DISCS={
        d("cream","Cream",0xffe8debb,0xffe8debb,0xffb2392b,0xff274440,PLAIN),
        d("wood","Wood",0xffe1be80,0xffe1be80,0xffa02b21,0xff28332d,ENGRAVED),
        d("jade","Jade",0xffc2d9c4,0xffc2d9c4,0xffa02b21,0xff28332d,PLAIN),
        d("porcelain","Porcelain",0xfff6f2e9,0xfff6f2e9,0xffb32725,0xff253942,GLAZED),
        d("rosewood","Rosewood",0xff6d2d29,0xff382b27,0xffffb8aa,0xffedf5f2,ENGRAVED),
        d("obsidian","Obsidian",0xff323038,0xff171e25,0xffffb8aa,0xffedf5f2,GLAZED),
        d("xiangqi-com","Xiangqi.com · Classic",0xffac2825,0xff111111,0xffffffff,0xffffffff,LACQUER),
        d("xiangqi-soft","Xiangqi.com · Soft lacquer",0xffbd3d32,0xff282b2a,0xfffff6e9,0xfffff6e9,GLAZED),
        d("one-coral","Xiangqi One · Coral",0xffee7851,0xff282828,0xff4b1308,0xffffffff,LACQUER),
        d("classic-ivory","Golden ivory",0xfff1d58e,0xfff1d58e,0xffbf3727,0xff16392d,ENGRAVED),
        d("lacquer","Crimson and ink",0xff8b202b,0xff17242b,0xffffe4a6,0xffffe4a6,LACQUER),
        d("cinnabar","Cinnabar",0xffb84332,0xff303b38,0xfffff9eb,0xfffff9eb,PLAIN),
        d("celadon","Celadon",0xffd8e3c8,0xffb8cec8,0xff8b292a,0xff203f3c,GLAZED),
        d("blue-white","Blue and white porcelain",0xfff4f4e6,0xffe4eaf2,0xffaa272e,0xff244c80,GLAZED),
        d("walnut","Carved walnut",0xffb8844d,0xff8e5c37,0xff4d0000,0xfffff1d1,ENGRAVED),
        d("copper","Copper",0xffd5a070,0xffb47850,0xff681d17,0xff281b15,ENGRAVED),
        d("silver","Brushed silver",0xffdedde2,0xffbdc5cc,0xffa12634,0xff17232b,GLAZED),
        d("night-jade","Night jade",0xff285446,0xff1e3334,0xffffbcac,0xffebf6e8,GLAZED)
    };
    private AppearancePresets(){}
    private static Board b(String id,String name,int background,int grid,int played,int best,int border,int texture){return new Board(id,name,background,grid,played,best,border,texture);}
    private static Disc d(String id,String name,int red,int black,int redInk,int blackInk,int finish){return new Disc(id,name,red,black,redInk,blackInk,finish);}
    public static Board board(String id){for(Board preset:BOARDS)if(preset.id.equals(id))return preset;return BOARDS[0];}
    public static Disc disc(String id){for(Disc preset:DISCS)if(preset.id.equals(id))return preset;return DISCS[0];}
    public static String[] boardIds(){String[] result=new String[BOARDS.length];for(int i=0;i<result.length;i++)result[i]=BOARDS[i].id;return result;}
    public static String[] boardLabels(){String[] result=new String[BOARDS.length];for(int i=0;i<result.length;i++)result[i]=BOARDS[i].label;return result;}
    public static String[] discIds(){String[] result=new String[DISCS.length];for(int i=0;i<result.length;i++)result[i]=DISCS[i].id;return result;}
    public static String[] discLabels(){String[] result=new String[DISCS.length];for(int i=0;i<result.length;i++)result[i]=DISCS[i].label;return result;}
}
