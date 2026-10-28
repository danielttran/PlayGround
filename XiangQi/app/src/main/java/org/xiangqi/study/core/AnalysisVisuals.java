package org.xiangqi.study.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Projects real, legal PV continuations into side-aware board annotations. */
public final class AnalysisVisuals {
    private AnalysisVisuals() {}

    public static final class Arrow {
        public final String move;
        public final int rank;
        public final boolean red,reply;
        private Arrow(String move,int rank,boolean red,boolean reply){this.move=move;this.rank=rank;this.red=red;this.reply=reply;}
    }

    /** A reply is checked after its own first move, never against a different PV. */
    public static List<Arrow> arrows(Position root,Engine.Result result,boolean rootRedToMove){
        if(root==null||result==null||root.redToMove!=rootRedToMove)return Collections.emptyList();
        List<Engine.Line> lines=new ArrayList<>(result.lines);
        if(lines.isEmpty()){
            List<String> pv=result.pv;
            if(pv==null||pv.isEmpty())pv=result.bestMove==null?Collections.emptyList():Collections.singletonList(result.bestMove);
            lines.add(new Engine.Line(1,result.score,result.depth,pv));
        }
        lines.sort(Comparator.comparingInt(line->line.rank));
        List<Arrow> arrows=new ArrayList<>();
        for(Engine.Line line:lines){
            if(line.rank<1||line.pv.isEmpty())continue;
            Position position=root;
            for(int ply=0;ply<Math.min(2,line.pv.size());ply++){
                String move;
                try{move=Position.normalizeMove(line.pv.get(ply));position=position.play(move);}
                catch(IllegalArgumentException invalid){break;}
                arrows.add(new Arrow(move,line.rank,ply==0?rootRedToMove:!rootRedToMove,ply==1));
            }
        }
        return Collections.unmodifiableList(arrows);
    }

    /** A bounded visual scale, not a claimed winning probability. */
    public static double redFraction(boolean active,Integer redCentipawns){
        if(!active||redCentipawns==null)return .5;
        return .5+.48*Math.tanh(redCentipawns/600.0);
    }
}
