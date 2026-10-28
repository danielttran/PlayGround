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

    private Engine(long millis) { deadline = millis <= 0 ? Long.MAX_VALUE : System.nanoTime() + Math.min(millis, 3600000L) * 1000000L; }

    public interface ProgressListener { void onProgress(Result snapshot); }

    /** One ranked root alternative. Scores always favor Red when positive. */
    public static final class Line {
        public final int rank, score, depth;
        public final String bestMove;
        public final List<String> pv;
        public Line(int rank, int score, int depth, List<String> pv) {
            this.rank = rank; this.score = score; this.depth = depth;
            this.pv = Collections.unmodifiableList(new ArrayList<>(pv));
            this.bestMove = this.pv.isEmpty() ? null : this.pv.get(0);
        }
        public Line copy() { return new Line(rank, score, depth, pv); }
    }

    public static final class Result {
        /** Centipawns from Red's perspective, including terminal scores +/-30000. */
        public int score;
        public int depth;
        public long nodes;
        public String bestMove;
        public List<String> pv = Collections.emptyList();
        /** Immutable ranked alternatives from the current exact root iteration. */
        public List<Line> lines = Collections.emptyList();
        // Parser bookkeeping belongs to this search, never global/shared state.
        int uciIterationDepth = -1;
        String uciRoot;

        /** An independent progress snapshot; later search updates cannot change its PV. */
        public Result copy() {
            Result copy = new Result(); copy.score = score; copy.depth = depth;
            copy.nodes = nodes; copy.bestMove = bestMove;
            copy.pv = Collections.unmodifiableList(new ArrayList<>(pv));
            List<Line> alternatives = new ArrayList<>();
            for (Line line : lines) alternatives.add(line.copy());
            copy.lines = Collections.unmodifiableList(alternatives);
            copy.uciIterationDepth = uciIterationDepth; copy.uciRoot = uciRoot;
            return copy;
        }
    }

    public static Result analyze(Position position, int maxDepth, long millis) {
        return analyze(position, maxDepth, millis, null);
    }

    /** millis <= 0 searches to the requested depth, or until interrupted. */
    public static Result analyze(Position position, int maxDepth, long millis, ProgressListener listener) {
        return analyze(position, maxDepth, millis, 1, listener);
    }

    public static Result analyze(Position position, int maxDepth, long millis, int lines) {
        return analyze(position, maxDepth, millis, lines, null);
    }

    /** MultiPV searches actual distinct root moves and their legal continuations. */
    public static Result analyze(Position position, int maxDepth, long millis, int lines, ProgressListener listener) {
        Engine search = new Engine(millis);
        int requestedLines = Math.max(1, Math.min(lines, 5));
        Position root = position.copy();
        List<String> moves = root.legalMoves();
        Result result = new Result();
        result.score = evaluate(root);
        if (moves.isEmpty()) { result.score = root.redToMove ? -MATE : MATE; return result; }
        search.order(root, moves, null);
        result.bestMove = moves.get(0);
        result.pv = Collections.singletonList(result.bestMove);
        result.lines = Collections.singletonList(new Line(1, result.score, 0, result.pv));
        for (int depth = 1; depth <= Math.max(1, Math.min(maxDepth, 64)); depth++) {
            try {
                search.checkTime();
                List<Line> completed = new ArrayList<>();
                if (requestedLines == 1) {
                    List<String> pv = new ArrayList<>();
                    int score = search.negamax(root, depth, -MATE - 1, MATE + 1, 0, pv, result.bestMove);
                    completed.add(new Line(1, root.redToMove ? score : -score, depth, pv));
                } else {
                    search.order(root, moves, result.bestMove);
                    for (String move : moves) {
                        search.checkTime();
                        List<String> continuation = new ArrayList<>();
                        // Each alternative receives a full window, so its score
                        // is exact rather than a root alpha-beta bound.
                        int score = -search.negamax(root.playUnchecked(move), depth - 1, -MATE - 1, MATE + 1, 1, continuation, null);
                        List<String> pv = new ArrayList<>(); pv.add(move); pv.addAll(continuation);
                        completed.add(new Line(0, root.redToMove ? score : -score, depth, pv));
                    }
                    completed.sort(Comparator.comparingInt((Line line) -> root.redToMove ? -line.score : line.score));
                    List<Line> ranked = new ArrayList<>();
                    for (int i = 0; i < Math.min(requestedLines, completed.size()); i++) {
                        Line line = completed.get(i); ranked.add(new Line(i + 1, line.score, depth, line.pv));
                    }
                    completed = ranked;
                }
                Line first = completed.get(0);
                result.depth = depth;
                result.score = first.score; result.pv = first.pv; result.bestMove = first.bestMove;
                result.lines = Collections.unmodifiableList(completed);
                result.nodes = search.nodes;
                if (listener != null) listener.onProgress(result.copy());
                if (Math.abs(first.score) >= MATE - 100) break;
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
