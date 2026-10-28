package org.xiangqi.study.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Small offline teaching engine; not a substitute for a championship engine. */
public final class Engine {
    private static final int MATE = 30000;
    private final long deadline;
    private long nodes;

    private Engine(long millis) { deadline = System.nanoTime() + Math.max(1, Math.min(millis, 60000)) * 1000000L; }

    public static final class Result {
        /** Centipawns from Red's perspective, including terminal scores +/-30000. */
        public int score;
        public int depth;
        public long nodes;
        public String bestMove;
        public List<String> pv = Collections.emptyList();
    }

    public static Result analyze(Position position, int maxDepth, long millis) {
        Engine search = new Engine(millis);
        Position root = position.copy();
        List<String> moves = root.legalMoves();
        Result result = new Result();
        result.score = evaluate(root);
        if (moves.isEmpty()) { result.score = root.redToMove ? -MATE : MATE; return result; }
        search.order(root, moves, null);
        result.bestMove = moves.get(0);
        result.pv = Collections.singletonList(result.bestMove);
        for (int depth = 1; depth <= Math.max(1, Math.min(maxDepth, 12)); depth++) {
            try {
                search.checkTime();
                List<String> pv = new ArrayList<>();
                int score = search.negamax(root, depth, -MATE - 1, MATE + 1, 0, pv, result.bestMove);
                result.depth = depth;
                result.score = root.redToMove ? score : -score;
                result.pv = Collections.unmodifiableList(new ArrayList<>(pv));
                result.bestMove = pv.isEmpty() ? null : pv.get(0);
                if (Math.abs(score) >= MATE - 100) break;
            } catch (TimeUp e) { break; }
        }
        result.nodes = search.nodes;
        return result;
    }

    private int negamax(Position position, int depth, int alpha, int beta, int ply, List<String> pv, String preferred) {
        checkTime(); nodes++;
        List<String> moves = position.legalMoves();
        if (moves.isEmpty()) return -MATE + ply; // Xiangqi stalemate is a loss too.
        if (depth == 0) return (position.redToMove ? 1 : -1) * evaluate(position);
        order(position, moves, preferred);
        int best = -MATE - 1;
        for (String move : moves) {
            List<String> childPv = new ArrayList<>();
            int score = -negamax(position.playUnchecked(move), depth - 1, -beta, -alpha, ply + 1, childPv, null);
            if (score > best) { best = score; pv.clear(); pv.add(move); pv.addAll(childPv); }
            alpha = Math.max(alpha, score);
            if (alpha >= beta) break;
        }
        return best;
    }

    private void order(Position position, List<String> moves, String preferred) {
        moves.sort(Comparator.comparingInt((String m) -> {
            if (m.equals(preferred)) return 100000;
            char captured = position.board[Position.index(m.substring(2))];
            char moving = position.board[Position.index(m.substring(0, 2))];
            return captured == 0 ? 0 : 10 * value(captured) - value(moving);
        }).reversed());
    }

    private static int evaluate(Position position) {
        int total = 0;
        for (int i = 0; i < 90; i++) {
            char p = position.board[i];
            if (p == 0) continue;
            boolean red = Position.isRed(p); int x = i % 9, advance = red ? 9 - i / 9 : i / 9;
            int bonus = 0;
            switch (Character.toLowerCase(p)) {
                case 'p': bonus = advance * 5 + (advance >= 5 ? 35 + (4 - Math.abs(x - 4)) * 5 : 0); break;
                case 'n': bonus = (4 - Math.abs(x - 4)) * 4 + Math.min(advance, 6) * 3; break;
                case 'c': case 'r': bonus = (4 - Math.abs(x - 4)) * 2; break;
                default: break;
            }
            total += (red ? 1 : -1) * (value(p) + bonus);
        }
        return total;
    }

    private static int value(char piece) {
        switch (Character.toLowerCase(piece)) {
            case 'k': return 10000;
            case 'r': return 900;
            case 'c': return 450;
            case 'n': return 400;
            case 'b': case 'a': return 200;
            case 'p': return 100;
            default: return 0;
        }
    }

    private void checkTime() {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) throw new TimeUp();
    }
    private static final class TimeUp extends RuntimeException {
        private static final long serialVersionUID = 1L;
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }
}
