package org.xiangqi.study.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Legal two-sided PV projection and evaluation scale without Android. */
public final class AnalysisVisualsTest {
    private static int assertions;
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] ignored){
        for(boolean red:new boolean[]{true,false})for(int count:new int[]{1,2,3}){
            Position position=Position.fromFen(Position.START_FEN.replace(" w ",red?" w ":" b "));
            Engine.Result result=result(red,count);String original=position.toFen();
            List<AnalysisVisuals.Arrow> arrows=AnalysisVisuals.arrows(position,result,red);
            check(arrows.size()==count*2,"Each line contributes both legal sides");
            for(int i=0;i<count;i++){
                AnalysisVisuals.Arrow first=arrows.get(i*2),reply=arrows.get(i*2+1);
                check(first.rank==i+1&&reply.rank==i+1,"Ranks stay paired");
                check(first.red==red&&reply.red!=red,"Arrows use their moving side, not root perspective");
                check(!first.reply&&reply.reply,"Reply has its own role");
                position.play(first.move).play(reply.move);
                check(reply.move.equals(red?"b7e7":"b2e2"),"Shared reply remains present for each line");
            }
            check(original.equals(position.toFen()),"Projection never mutates the current position");
            check(AnalysisVisuals.arrows(position,result,!red).isEmpty(),"Stale opposite-turn result is ignored");
            boolean immutable=false;try{arrows.clear();}catch(UnsupportedOperationException expected){immutable=true;}check(immutable,"Rendering snapshot is immutable");
        }
        Position root=Position.initial();Engine.Result malformed=new Engine.Result();
        malformed.lines=Arrays.asList(new Engine.Line(1,0,8,Arrays.asList("a0a9","b7e7")),new Engine.Line(2,0,8,Arrays.asList("b2b9","b9c7")),new Engine.Line(3,0,8,Collections.singletonList("h2e2")));
        List<AnalysisVisuals.Arrow> partial=AnalysisVisuals.arrows(root,malformed,true);
        check(partial.size()==2,"Illegal root move and captured-piece reply are never fabricated");
        check(partial.get(0).rank==2&&!partial.get(0).reply&&partial.get(0).move.equals("b2b9"),"Valid first move survives invalid reply");
        check(partial.get(1).rank==3&&!partial.get(1).reply,"A terminal or short PV has no invented reply");
        Engine.Result legacy=new Engine.Result();legacy.bestMove="b2e2";legacy.pv=Arrays.asList("b2e2","b7e7");
        check(AnalysisVisuals.arrows(root,legacy,true).size()==2,"Legacy result supports its actual PV reply");
        legacy.pv=Collections.emptyList();check(AnalysisVisuals.arrows(root,legacy,true).size()==1,"Best move alone never fabricates a reply");
        check(AnalysisVisuals.arrows(root,null,true).isEmpty(),"Stopping clears all PV arrows");
        check(AnalysisVisuals.redFraction(false,30000)==.5,"Off discards even an extreme old evaluation");
        check(AnalysisVisuals.redFraction(true,null)==.5,"Pending is neutral");
        check(AnalysisVisuals.redFraction(true,0)==.5,"Equality is the middle");
        double previous=0;
        for(int score:new int[]{Integer.MIN_VALUE,-30000,-600,-100,0,100,600,30000,Integer.MAX_VALUE}){
            double fraction=AnalysisVisuals.redFraction(true,score);check(Double.isFinite(fraction)&&fraction>=.02-1e-9&&fraction<=.98+1e-9,"Rail keeps both sides visible");check(fraction>=previous,"More Red advantage monotonically increases its share");previous=fraction;
        }
        check(Math.abs(AnalysisVisuals.redFraction(true,600)+AnalysisVisuals.redFraction(true,-600)-1)<1e-12,"The scale is side symmetric");
        System.out.println("Analysis visuals: "+assertions+" assertions passed");
    }
    private static Engine.Result result(boolean red,int count){
        String[] first=red?new String[]{"b2e2","h2e2","b0c2"}:new String[]{"b7e7","h7e7","b9c7"};
        Engine.Result result=new Engine.Result();java.util.ArrayList<Engine.Line> lines=new java.util.ArrayList<>();
        for(int rank=1;rank<=count;rank++)lines.add(new Engine.Line(rank,100-rank,8,Arrays.asList(first[rank-1],red?"b7e7":"b2e2")));
        result.lines=Collections.unmodifiableList(lines);result.pv=lines.get(0).pv;result.bestMove=result.pv.get(0);return result;
    }
}
