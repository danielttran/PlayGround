package org.xiangqi.study.core;

import java.util.List;

public final class CoreTest {
    private static int assertions;
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void fails(Runnable action, String message) {
        boolean failed = false;
        try { action.run(); } catch (IllegalArgumentException expected) { failed = true; }
        check(failed, message);
    }
    private static Position bare() { return Position.fromFen("3k5/9/9/9/9/9/9/9/9/4K4 w - - 0 1"); }
    private static void piece(Position position, String square, char piece) { position.board[Position.index(square)] = piece; }
    private static long perft(Position p, int depth) {
        if (depth == 0) return 1;
        List<String> moves = p.legalMoves();
        if (depth == 1) return moves.size();
        long count = 0;
        for (String move : moves) count += perft(p.playUnchecked(move), depth - 1);
        return count;
    }
    public static void main(String[] args) {
        Position initial = Position.initial();
        check(initial.toFen().equals(Position.START_FEN), "FEN round trip");
        for (int i = 0; i < 90; i++) check(Position.index(Position.square(i)) == i, "ICCS round trip " + i);
        check(Position.index("a0") == 81 && Position.index("i9") == 8, "ICCS orientation");
        // Independently compared with official Pikafish by scripts/audit-moves.py.
        check(perft(initial, 1) == 44, "Perft depth 1 = 44");
        check(perft(initial, 2) == 1920, "Perft depth 2 = 1920");
        check(perft(initial, 3) == 79666, "Perft depth 3 = 79666");
        Position child = initial.play("b2-e2");
        check(!child.redToMove && child.board[Position.index("e2")] == 'C', "Move switches turn");
        check(initial.board[Position.index("b2")] == 'C', "Move does not mutate prior position");
        fails(() -> initial.play("a3a2"), "Soldier cannot retreat");
        fails(() -> initial.play("a3b3"), "Soldier cannot move sideways before river");
        fails(() -> initial.play("a0a9"), "Rook cannot pass blockers");
        fails(() -> Position.fromFen("9/9 w"), "Invalid FEN rejected");
        fails(() -> Position.fromFen("9/9/9/9/9/9/9/9/9/9 w"), "Missing generals rejected");

        Position horse = bare(); piece(horse, "b0", 'N'); piece(horse, "b1", 'P');
        check(!horse.legalMoves().contains("b0c2"), "Horse's leg blocks forward jump");
        check(horse.legalMoves().contains("b0d1"), "Other horse jump remains legal");
        Position elephant = bare(); piece(elephant, "c4", 'B');
        check(!elephant.legalMoves().contains("c4e6"), "Elephant cannot cross river");
        piece(elephant, "d3", 'P');
        check(!elephant.legalMoves().contains("c4e2"), "Elephant eye blocks diagonal");
        Position advisor = bare(); piece(advisor, "d0", 'A');
        check(advisor.legalMoves().contains("d0e1"), "Advisor moves diagonally in palace");
        check(!advisor.legalMoves().contains("d0c1"), "Advisor cannot leave palace");
        Position cannon = bare(); piece(cannon, "a0", 'C'); piece(cannon, "a3", 'p');
        check(!cannon.legalMoves().contains("a0a3"), "Cannon cannot capture without screen");
        piece(cannon, "a1", 'P');
        check(cannon.legalMoves().contains("a0a3"), "Cannon captures over one screen");
        piece(cannon, "a2", 'p');
        check(!cannon.legalMoves().contains("a0a3"), "Cannon cannot capture over two screens");
        Position pawn = bare(); piece(pawn, "a5", 'P');
        check(pawn.legalMoves().contains("a5b5"), "Crossed soldier can move sideways");
        check(!pawn.legalMoves().contains("a5a4"), "Crossed soldier cannot retreat");
        Position facing = Position.fromFen("4k4/9/9/9/4P4/9/9/9/9/4K4 w - - 0 1");
        check(!facing.isInCheck(true), "Screen protects generals");
        check(!facing.legalMoves().contains("e5d5"), "Pinned soldier cannot expose facing generals");
        piece(facing, "e5", (char)0);
        check(facing.isInCheck(true) && facing.isInCheck(false), "Flying general attack detected");
        Position rookCheck = bare(); piece(rookCheck, "e8", 'r');
        check(rookCheck.isInCheck(true), "Rook check detected");
        check(!rookCheck.legalMoves().contains("e0e1"), "General cannot stay in rook check");

        String pgn = "[Event \"World example\"]\n[Red \"A\"]\n[Black \"B\"]\n[Result \"*\"]\n1.b2e2 {note} b7-e7 (1...a9a8 (2.a0a1)) 2. g3g4 $1 g6g5 *";
        List<Pgn.Game> parsed = Pgn.parse(pgn);
        check(parsed.size() == 1 && parsed.get(0).moves.size() == 4, "Comments, NAGs, nested variations skipped");
        check(parsed.get(0).red.equals("A") && parsed.get(0).moves.get(1).equals("b7e7"), "PGN metadata and normalization");
        check(UciPosition.command(child, Position.START_FEN, java.util.Collections.singletonList("b2-e2")).equals("position startpos moves b2e2"), "UCI retains normalized game history");
        check(UciPosition.command(child, "", java.util.Collections.singletonList("b2e2")).equals("position startpos moves b2e2"), "Bundled games with omitted starting FEN reach the native engine");
        check(UciPosition.command(child, "  ", java.util.Collections.singletonList("b2e2")).equals("position startpos moves b2e2"), "Blank starting FEN defaults to the ordinary starting position");
        check(UciPosition.command(initial, null, java.util.Collections.emptyList()).equals("position startpos"), "Null optional starting FEN defaults consistently");
        check(UciPosition.command(bare(), bare().toFen(), java.util.Collections.emptyList()).startsWith("position fen "), "UCI retains custom starting FEN");
        fails(() -> UciPosition.command(initial, Position.START_FEN, java.util.Collections.singletonList("b2e2")), "Mismatched analysis history rejected");
        fails(() -> UciPosition.command(child, Position.START_FEN, java.util.Collections.singletonList("a0i9")), "Illegal analysis history rejected");
        Position largeClock=Position.fromFen(Position.START_FEN.replace("0 1","120 2147483647"));
        check(UciPosition.command(largeClock,largeClock.toFen(),java.util.Collections.emptyList()).equals("position startpos"),"Unsupported imported FEN clocks cannot block board analysis");
        check(Pgn.parse(pgn + "\n[Event \"Second\"]\n1. b2e2 *").size() == 2, "Multiple games imported");
        check(Pgn.parse("[TimeControl \"60+30\"]\n1.b2e2 *").get(0).timeControl.equals("60+30"), "PGN time control retained");
        check(Pgn.parse("[Result \"*\"]\r1.b2e2 ; old-file comment\rb7e7 *").get(0).moves.size()==2, "CR-only line comments preserve following moves");
        check(Pgn.parse("1.b2e2 (1...a9a8 ; variation note\r 2.a0a1) b7e7 *").get(0).moves.size()==2, "CR-only comments inside variations preserve main line");
        check(Pgn.parse("1.b2e2 (1...a9a8\n% ignored line containing (\n) b7e7 *").get(0).moves.size()==2, "Percent escape lines inside variations cannot corrupt nesting");
        fails(() -> Pgn.parse("[SetUp \"1\"]\n1.b2e2 *"), "Custom setup with missing FEN is rejected rather than guessed");
        fails(() -> Pgn.parse("1.b2e2 b7e7 2.e2e9 *"), "Illegal mainline rejected");
        fails(() -> Pgn.parse("1.e4 *"), "Western chess notation rejected");
        fails(() -> Pgn.parse("1.b2e2 {unfinished"), "Unfinished comment rejected");
        fails(() -> Pgn.parse("1.b2e2 (b7e7 *"), "Unfinished variation rejected");
        fails(() -> Pgn.parse("[Result \"1-0\"]\n1.b2e2 0-1"), "Mismatched result rejected");
        fails(() -> Pgn.parse(pgn + "\n[Event \"Bad\"]\n1. a0i9 *"), "Entire batch fails on invalid later game");
        fails(() -> Pgn.parse("{Only comment}"), "No-games import rejected");
        String longHeader="a".repeat(10000);
        check(Pgn.parse("[Event \""+longHeader+"\"]\n1.b2e2 *").get(0).event.equals(longHeader), "Long PGN tag values cannot overflow the parser stack");
        check(Pgn.parse("[Event \"Alpha \\\"quoted\\\" \\\\ path\"]\n1.b2e2 *").get(0).event.equals("Alpha \"quoted\" \\ path"), "Possessive tag parsing preserves escaped quotes and backslashes");
        fails(() -> Pgn.parse("[Event \""+longHeader+"\n1.b2e2 *"), "Malformed long tag rejects without stack overflow");
        boolean boundedImportError=false;try{Pgn.parse(longHeader);}catch(IllegalArgumentException e){boundedImportError=e.getMessage().length()<256;}
        check(boundedImportError, "Untrusted move token previews are bounded in import errors");
        boolean boundedMoveError=false;try{Position.normalizeMove(longHeader);}catch(IllegalArgumentException e){boundedMoveError=e.getMessage().length()<256;}
        check(boundedMoveError, "Untrusted JSON coordinate previews cannot fill error dialogs");

        Position stalemate = Position.fromFen("4k4/3R1R3/9/9/9/9/9/9/9/3K5 b - - 0 1");
        check(!stalemate.isInCheck(false) && stalemate.legalMoves().isEmpty(), "Stalemate fixture");
        Engine.Result terminal = Engine.analyze(stalemate, 3, 1000);
        check(terminal.score == 30000 && terminal.bestMove == null, "Stalemated Black loses");
        Position capture = bare(); piece(capture, "a0", 'R'); piece(capture, "a5", 'r');
        Engine.Result analysis = Engine.analyze(capture, 2, 1500);
        check(analysis.bestMove.equals("a0a5"), "Engine finds free rook capture");
        check(analysis.score > 500 && analysis.depth >= 1 && analysis.nodes > 0, "Engine reports completed search and Red perspective");
        Position blackCapture = Position.fromFen("r2k5/R8/9/9/9/9/9/9/9/4K4 b - - 0 1");
        Engine.Result blackAnalysis = Engine.analyze(blackCapture, 2, 1500);
        check(blackAnalysis.bestMove.equals("a9a8") && blackAnalysis.score < -500, "Black favorable score remains negative");
        Engine.Result uci = new Engine.Result();
        UciInfo.update(uci, initial, "info depth 12 multipv 1 score cp 87 nodes 1234 pv b2e2 b7e7");
        check(uci.score == 87 && uci.depth == 12 && uci.nodes == 1234 && uci.pv.size() == 2, "UCI info parses Red score and legal PV");
        UciInfo.update(uci, initial, "info depth 13 score cp 500 lowerbound nodes 999 pv b2e2");
        check(uci.score == 87 && uci.depth == 12, "UCI bound-only score ignored");
        UciInfo.update(uci, child, "info depth 8 score cp 50 nodes 900 pv b7e7");
        check(uci.score == -50 && uci.bestMove.equals("b7e7"), "UCI side-to-move score converted to Red");
        UciInfo.update(uci, child, "info depth 9 score mate -3 nodes 1500 pv b7e7 a0a1");
        check(uci.score == 29997, "UCI mate score converted to Red");
        UciInfo.update(uci, child, "info depth 99 score cp 200 nodes 2000 pv a0a1");
        check(uci.depth == 9, "UCI illegal first PV move rejected");
        UciInfo.update(uci, child, "info depth nope score cp 200 pv b7e7");
        check(uci.depth == 9, "UCI malformed numeric data ignored");
        Engine.Result drawnRoot=new Engine.Result();
        UciInfo.update(drawnRoot,initial,"info depth 1 score cp 0 nodes 0 pv");
        check(drawnRoot.depth==1&&drawnRoot.score==0&&drawnRoot.pv.isEmpty(),"Exact root draw with empty PV preserves its completed score");
        Engine.Result mateZero=new Engine.Result();
        UciInfo.update(mateZero, initial, "info depth 0 score mate 0");
        check(mateZero.score==-30000, "UCI mate zero means the current Red side has lost");
        UciInfo.update(mateZero, child, "info depth 0 score mate 0");
        check(mateZero.score==30000, "UCI mate zero converts a Black loss to Red perspective");
        UciInfo.update(mateZero, initial, "info depth 1 score mate -2147483648");
        check(mateZero.score==-29901, "UCI mate distance cannot overflow integer absolute value");
        Thread.currentThread().interrupt();
        fails(() -> Pgn.parse(pgn), "Interrupted PGN import stops without returning a partial game");
        Engine.Result cancelled = Engine.analyze(initial, 6, 1000);
        check(cancelled.depth == 0 && initial.legalMoves().contains(cancelled.bestMove), "Cancelled search returns legal fallback");
        Thread.interrupted();
        long started = System.nanoTime();
        Engine.Result bounded = Engine.analyze(initial, 12, 20);
        check((System.nanoTime() - started) / 1000000 < 1000 && initial.legalMoves().contains(bounded.bestMove), "Search respects time budget");
        List<Engine.Result> progress=new java.util.ArrayList<>();
        Engine.Result deepening=Engine.analyze(initial,3,0,progress::add);
        check(progress.size()==3&&deepening.depth==3, "Untimed analysis reaches configured depth with completed-iteration updates");
        check(progress.get(0).depth==1&&progress.get(1).depth==2&&progress.get(2).depth==3, "Progress depths arrive in increasing completed order");
        check(progress.get(0).nodes>0&&progress.get(2).nodes>progress.get(0).nodes, "Live progress includes actual cumulative search nodes");
        String firstBest=progress.get(0).bestMove;deepening.bestMove="changed";
        check(progress.get(0).bestMove.equals(firstBest)&&progress.get(0).pv.size()==1, "Progress values remain independent of later result updates");
        boolean pvImmutable=false;try{progress.get(0).pv.add("a0a1");}catch(UnsupportedOperationException expected){pvImmutable=true;}
        check(pvImmutable, "Progress PV is immutable");
        List<Engine.Result> stoppedProgress=new java.util.ArrayList<>();
        Engine.Result stopped=Engine.analyze(initial,64,0,snapshot->{stoppedProgress.add(snapshot);Thread.currentThread().interrupt();});
        check(stopped.depth==1&&stoppedProgress.size()==1, "Untimed deep search stops promptly when interrupted after live progress");
        Thread.interrupted();
        MultiPvTest.run();
        System.out.println("PASS: " + assertions + " assertions; start perft 44 / 1920 / 79666; rules, PGN import, engine and cancellation");
    }
}
