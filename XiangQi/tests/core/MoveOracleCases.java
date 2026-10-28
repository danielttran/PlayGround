package org.xiangqi.study.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Deterministic reachable positions for comparison with an independent engine. */
public final class MoveOracleCases {
    private static void emit(String name, Position position) {
        List<String> moves=new ArrayList<>(position.legalMoves()); Collections.sort(moves);
        System.out.println(name+"\t"+position.toFen()+"\t"+String.join(" ",moves));
    }
    public static void main(String[] args) {
        emit("initial",Position.initial());
        emit("stalemate",Position.fromFen("4k4/3R1R3/9/9/9/9/9/9/9/3K5 b - - 0 1"));
        emit("checkmate",Position.fromFen("4k4/3RR4/9/9/9/9/9/9/9/5K3 b - - 0 1"));
        Random random=new Random(0x5849414e475149L); Set<String> seen=new HashSet<>();
        Position position=Position.initial(); int sinceReset=0, count=0;
        while(count<256) {
            List<String> legal=position.legalMoves();
            if(legal.isEmpty()||sinceReset>=160) {position=Position.initial();sinceReset=0;continue;}
            List<String> captures=new ArrayList<>();
            for(String move:legal)if(position.board[Position.index(move.substring(2))]!=0)captures.add(move);
            List<String> choices=!captures.isEmpty()&&random.nextInt(3)==0?captures:legal;
            position=position.playUnchecked(choices.get(random.nextInt(choices.size()))); sinceReset++;
            if(sinceReset%3==0&&seen.add(position.toFen()))emit("reachable-"+(++count),position);
        }
    }
}
