package org.xiangqi.study;

import org.xiangqi.study.core.Engine;
import org.xiangqi.study.core.Position;
import java.util.*;

/** App difficulty scale with graded search budgets; it does not claim human Elo ratings. */
public final class ComputerDifficulty {
    private ComputerDifficulty(){}
    public static int clamp(int level){return Math.max(1,Math.min(100,level));}
    public static int depth(int level){double n=(clamp(level)-1)/99.0;return 1+(int)Math.round(13*Math.pow(n,1.35));}
    public static long nodes(int level){return Math.round(500*Math.pow(400,(clamp(level)-1)/99.0));}
    public static long millis(int level){return 150+clamp(level)*15L;}
    public static String label(int level){return level<20?"Beginner":level<40?"Casual":level<65?"Club":level<85?"Advanced":level<100?"Expert":"Elite";}
    public static String choose(Engine.Result result,Position position,int level,long seed){
        List<Engine.Line> lines=result.lines;if(level>=100||lines.size()<2)return result.bestMove;
        int direction=position.redToMove?1:-1,best=direction*result.score;
        double temperature=6+480*Math.pow(1-(clamp(level)-1)/99.0,2.4),total=0;
        List<Engine.Line> candidates=new ArrayList<>();List<Double> weights=new ArrayList<>();
        for(Engine.Line line:lines){
            if(line.bestMove==null)continue;int loss=Math.max(0,best-direction*line.score);
            if(loss>Math.max(35,temperature*3.0))continue;double weight=Math.exp(-loss/temperature);candidates.add(line);weights.add(weight);total+=weight;
        }
        double draw=new Random(seed).nextDouble()*total;for(int i=0;i<candidates.size();i++){draw-=weights.get(i);if(draw<=0)return candidates.get(i).bestMove;}
        return result.bestMove;
    }
}
