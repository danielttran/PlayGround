package org.xiangqi.study.core;

import java.util.*;

/** Ranked UCI lines, score perspective, legal replies, and snapshot isolation. */
public final class MultiPvTest {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static void immutable(Runnable action,String message){boolean rejected=false;try{action.run();}catch(UnsupportedOperationException expected){rejected=true;}check(rejected,message);}
    private static void legalLines(Position root,Engine.Result result,int expected){
        check(result.lines.size()==expected,"Requested number of distinct legal root alternatives");
        Set<String> moves=new HashSet<>();int previous=root.redToMove?Integer.MAX_VALUE:Integer.MIN_VALUE;
        for(int i=0;i<result.lines.size();i++){
            Engine.Line line=result.lines.get(i);check(line.rank==i+1,"Consecutive root ranks");
            check(line.depth==result.depth&&line.pv.size()>=2,"Completed depth-two lines contain an actual reply");
            check(moves.add(line.bestMove),"Root alternatives are distinct");
            check(root.redToMove?line.score<=previous:line.score>=previous,"Ranking favors the actual root side");previous=line.score;
            Position cursor=root;
            for(String move:line.pv){check(cursor.legalMoves().contains(move),"Every PV move follows the actual preceding position");cursor=cursor.play(move);}
        }
        check(result.bestMove.equals(result.lines.get(0).bestMove)&&result.score==result.lines.get(0).score&&result.pv.equals(result.lines.get(0).pv),"Legacy fields are exactly the first line");
    }
    public static void main(String[] args){run();}
    public static void run(){
        Position root=Position.initial();Engine.Result parsed=new Engine.Result();
        UciInfo.update(parsed,root,"info depth 4 multipv 1 score cp 60 nodes 10 pv b2e2 b7e7",3);
        UciInfo.update(parsed,root,"info depth 4 multipv 2 score cp 20 nodes 20 pv h2e2 h7e7",3);
        UciInfo.update(parsed,root,"info depth 4 multipv 3 score cp -20 nodes 30 pv b0c2 b9c7",3);
        check(parsed.lines.size()==3&&parsed.score==60&&parsed.bestMove.equals("b2e2"),"Secondary ranks do not replace the principal score/PV");
        Engine.Result snapshot=parsed.copy();List<Engine.Line> before=parsed.lines;
        UciInfo.update(parsed,root,"info depth 5 multipv 2 score cp 900 lowerbound pv h0g2 h9g7",3);
        check(parsed.lines==before&&parsed.depth==4,"A secondary bound does not start a new exact iteration");
        UciInfo.update(parsed,root,"info depth 3 multipv 2 score cp 999 pv h0g2 h9g7",3);
        check(parsed.lines==before,"Late older-depth lines cannot restore stale arrows");
        UciInfo.update(parsed,root,"info depth 5 multipv 1 score cp 70 pv h0g2 h9g7",3);
        check(parsed.lines.size()==1&&parsed.lines.get(0).depth==5,"New exact depth clears old secondary lines");
        UciInfo.update(parsed,root,"info depth 4 multipv 2 score cp 500 pv b0c2 b9c7",3);
        check(parsed.lines.size()==1,"Previous-depth secondary output remains rejected");
        UciInfo.update(parsed,root,"info depth 5 multipv 2 score cp 30 pv b0c2 b9c7",3);
        check(parsed.lines.size()==2&&parsed.score==70&&parsed.lines.get(1).score==30,"A fresh secondary line updates independently");
        check(snapshot.lines.size()==3&&snapshot.lines.get(0).bestMove.equals("b2e2")&&snapshot.lines.get(1).score==20,"Older progress snapshots retain all ranked alternatives");
        check(snapshot.lines!=before&&snapshot.lines.get(0)!=before.get(0)&&snapshot.lines.get(0).pv!=before.get(0).pv,"Snapshots deep-copy lines and PV storage");
        immutable(()->snapshot.lines.clear(),"Snapshot line list is immutable");
        immutable(()->snapshot.lines.get(0).pv.add("a0a1"),"Snapshot line PV is immutable");
        before=parsed.lines;
        for(String bad:new String[]{"info depth 6 multipv 0 score cp 1 pv b0c2","info depth 6 multipv 6 score cp 1 pv b0c2","info depth nope multipv 2 score cp 1 pv b0c2","info depth 6 multipv 2 score cp 1 pv a0a9"})UciInfo.update(parsed,root,bad,3);
        check(parsed.lines==before,"Invalid rank, depth and illegal first moves do not mutate accepted lines");
        UciInfo.update(parsed,root,"info depth 5 multipv 3 score cp 2 pv",3);
        check(parsed.lines==before,"An empty secondary PV cannot define an alternative");
        UciInfo.update(parsed,root,"info depth 5 multipv 3 score cp 2 pv h0g2 h9g7",3);
        check(parsed.lines==before,"A duplicate lower-ranked root move cannot replace the principal alternative");
        UciInfo.update(parsed,root,"info depth 6 multipv 2 score cp 40 pv b0c2 b0c2",3);
        check(parsed.lines.size()==1&&parsed.lines.get(0).rank==2&&parsed.lines.get(0).pv.equals(Collections.singletonList("b0c2")),"Out-of-order new iteration drops stale lines and keeps only a legal PV prefix");
        Position black=root.play("b2e2");Engine.Result blackInfo=new Engine.Result();
        UciInfo.update(blackInfo,black,"info depth 4 multipv 1 score cp 50 pv b7e7 a0a1",3);
        UciInfo.update(blackInfo,black,"info depth 4 multipv 2 score cp -30 pv h7e7 h0g2",3);
        check(blackInfo.score==-50&&blackInfo.lines.get(0).score==-50&&blackInfo.lines.get(1).score==30,"Every Black-to-move line converts to Red perspective exactly once");
        UciInfo.update(blackInfo,black,"info depth 4 multipv 2 score mate -3 pv h7e7 h0g2",3);
        check(blackInfo.lines.get(1).score==29997&&blackInfo.score==-50,"Secondary mate winner does not change first-line evaluation");
        Engine.Result single=new Engine.Result();UciInfo.update(single,root,"info depth 2 multipv 2 score cp 10 pv b0c2 b9c7");
        check(single.lines.isEmpty(),"Legacy parser remains single-PV by default");
        List<String> original=new ArrayList<>(Arrays.asList("b2e2","b7e7"));Engine.Line line=new Engine.Line(1,10,2,original);original.clear();
        check(line.pv.size()==2&&line.bestMove.equals("b2e2"),"Constructed line owns its PV storage");
        List<Engine.Result> progress=new ArrayList<>();
        Engine.Result red=Engine.analyze(root,2,0,3,progress::add);legalLines(root,red,3);
        check(progress.size()==2&&progress.get(0).lines.size()==3&&progress.get(0).lines.get(0).depth==1,"Fallback emits complete distinct alternatives per completed iteration");
        legalLines(black,Engine.analyze(black,2,0,3,null),3);
        Position sparse=Position.fromFen("3k5/9/9/9/9/9/9/9/9/4K4 w - - 0 1");
        Engine.Result limited=Engine.analyze(sparse,2,0,5,null);
        check(limited.lines.size()==Math.min(5,sparse.legalMoves().size()),"Requested lines never exceed legal root moves");
        Thread.currentThread().interrupt();try{
            Engine.Result cancelled=Engine.analyze(root,64,0,5,null);
            check(cancelled.depth==0&&cancelled.lines.size()==1&&root.legalMoves().contains(cancelled.bestMove),"Cancelled MultiPV returns a legal fallback without publishing a partial iteration");
        }finally{Thread.interrupted();}
        Engine.Result stopped=Engine.analyze(root,64,0,3,value->Thread.currentThread().interrupt());Thread.interrupted();
        check(stopped.depth==1&&stopped.lines.size()==3,"MultiPV cancellation retains the last fully completed iteration");
        System.out.println("MultiPV: "+assertions+" assertions passed");
    }
}
