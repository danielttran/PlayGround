package org.xiangqi.study.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds real app UCI history commands and independent expected legal-move sets. */
public final class NativePositionCases {
    private static void emit(String name,Position current,String startFen,List<String> history) {
        List<String> legal=new ArrayList<>(current.legalMoves());Collections.sort(legal);
        System.out.println(name+"\t"+UciPosition.command(current,startFen,history)+"\t"+String.join(" ",legal)+"\t"+current.toFen());
    }
    private static void custom(String name,String fen) {emit(name,Position.fromFen(fen),fen,Collections.emptyList());}
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Usage: NativePositionCases asset-games.tsv (id TAB optional-startFen TAB moves)");
        for(String line:Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8)) {
            if(line.trim().isEmpty())continue;
            String[] fields=line.split("\t",-1);
            if(fields.length!=3)throw new IllegalArgumentException("Expected three TSV fields");
            List<String> moves=fields[2].trim().isEmpty()?Collections.emptyList():Arrays.asList(fields[2].trim().split("\\s+"));
            Set<Integer> selected=new HashSet<>(Arrays.asList(0,1,moves.size()/2,moves.size()));
            Position current=fields[1].trim().isEmpty()?Position.initial():Position.fromFen(fields[1]),previous=null;
            for(int ply=0;ply<=moves.size();ply++) {
                if(selected.contains(ply))emit(fields[0]+"-ply"+ply,current,fields[1],moves.subList(0,ply));
                if(ply==moves.size()&&previous!=null&&current.legalMoves().isEmpty()&&!selected.contains(ply-1))
                    emit(fields[0]+"-last-legal-ply"+(ply-1),previous,fields[1],moves.subList(0,ply-1));
                if(ply<moves.size()){previous=current;current=current.play(moves.get(ply));}
            }
        }
        custom("custom-sparse","3k5/9/9/9/9/9/9/9/9/4K4 w - - 0 1");
        custom("custom-rook-check","3k5/4r4/9/9/9/9/9/9/9/4K4 w - - 0 1");
        custom("custom-cannon-check","3k5/4c4/9/9/4p4/9/9/9/9/4K4 w - - 0 1");
        custom("custom-horse-check","3k5/9/9/9/9/9/9/5n3/9/4K4 w - - 0 1");
        custom("custom-stalemate","4k4/3R1R3/9/9/9/9/9/9/9/3K5 b - - 0 1");
        custom("custom-checkmate","4k4/3RR4/9/9/9/9/9/9/9/5K3 b - - 0 1");
        List<String> branch=Arrays.asList("b2e2","b7e7","g3g4");Position variation=Position.initial();
        for(String move:branch)variation=variation.play(move);
        emit("analysis-variation",variation,"",branch);
    }
}
