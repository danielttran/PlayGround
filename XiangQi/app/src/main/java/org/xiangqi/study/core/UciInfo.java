package org.xiangqi.study.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Parses exact ranked UCI root lines without trusting an engine's move text. */
public final class UciInfo {
    private UciInfo() {}

    public static void update(Engine.Result result, Position root, String line) {
        update(result, root, line, 1);
    }

    public static void update(Engine.Result result, Position root, String line, int maxLines) {
        if (line == null || !line.startsWith("info ") || line.startsWith("info string ")) return;
        String[] tokens = line.trim().split("\\s+");
        int depth = result.depth, score = result.score; long nodes = result.nodes;
        boolean scored = false, bound = false; int pvAt = -1, rank = 1;
        for (int i = 1; i < tokens.length; i++) {
            try {
                if (tokens[i].equals("multipv") && i + 1 < tokens.length) rank = Integer.parseInt(tokens[++i]);
                else if (tokens[i].equals("depth") && i + 1 < tokens.length) depth = Math.max(0, Integer.parseInt(tokens[++i]));
                else if (tokens[i].equals("nodes") && i + 1 < tokens.length) nodes = Math.max(0, Long.parseLong(tokens[++i]));
                else if (tokens[i].equals("score") && i + 2 < tokens.length) {
                    String type = tokens[++i]; int value = Integer.parseInt(tokens[++i]);
                    if (type.equals("cp")) { score = Math.max(-29900, Math.min(29900, value)); scored = true; }
                    else if (type.equals("mate")) {
                        // UCI mate 0 is the side to move already checkmated.
                        score = (value > 0 ? 1 : -1) * (30000 - (int)Math.min(Math.abs((long)value), 99)); scored = true;
                    }
                } else if (tokens[i].equals("lowerbound") || tokens[i].equals("upperbound")) bound = true;
                else if (tokens[i].equals("pv")) { pvAt = i + 1; break; }
            } catch (NumberFormatException ignored) { return; }
        }
        // Bound-only scores do not describe a completed root iteration.
        if (!scored || bound || rank < 1 || rank > Math.max(1, Math.min(maxLines, 5))) return;
        List<String> pv = new ArrayList<>();
        Position cursor = root.copy();
        if (pvAt >= 0) {
            for (int i = pvAt; i < tokens.length && pv.size() < 256; i++) {
                try { String move = Position.normalizeMove(tokens[i]); cursor = cursor.play(move); pv.add(move); }
                catch (IllegalArgumentException invalid) { break; }
            }
            // An adjudicated root draw can have an explicit empty PV followed
            // by a legal bestmove. Keep its exact score/depth; reject nonempty
            // but illegal move text. The adapter validates the final bestmove.
            if (pv.isEmpty() && pvAt < tokens.length) return;
        }
        // Only the principal root adjudication may omit its move. An unnamed
        // secondary score cannot define a distinct legal alternative.
        if (rank > 1 && pv.isEmpty()) return;
        String rootKey = root.toFen();
        boolean sameRoot = rootKey.equals(result.uciRoot);
        if (sameRoot && depth < result.uciIterationDepth) return;
        List<Engine.Line> alternatives = new ArrayList<>();
        if (sameRoot && depth == result.uciIterationDepth) {
            for (Engine.Line existing : result.lines) {
                if (existing.rank == rank) continue;
                if (!pv.isEmpty() && pv.get(0).equals(existing.bestMove)) {
                    if (existing.rank < rank) return;
                    continue;
                }
                alternatives.add(existing);
            }
        }
        int redScore = root.redToMove ? score : -score;
        alternatives.add(new Engine.Line(rank, redScore, depth, pv));
        alternatives.sort(Comparator.comparingInt(existing -> existing.rank));
        result.lines = Collections.unmodifiableList(alternatives);
        result.uciIterationDepth = depth; result.uciRoot = rootKey;
        result.nodes = Math.max(result.nodes, nodes);
        if (rank == 1) {
            result.depth = depth; result.score = redScore;
            result.pv = Collections.unmodifiableList(new ArrayList<>(pv));
            result.bestMove = pv.isEmpty() ? null : pv.get(0);
        }
    }
}
