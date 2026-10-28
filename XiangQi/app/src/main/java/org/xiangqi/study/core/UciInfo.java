package org.xiangqi.study.core;

import java.util.ArrayList;
import java.util.List;

/** Parses standard single-PV UCI info without trusting an engine's move text. */
public final class UciInfo {
    private UciInfo() {}

    public static void update(Engine.Result result, Position root, String line) {
        if (line == null || !line.startsWith("info ") || line.startsWith("info string ")) return;
        String[] tokens = line.trim().split("\\s+");
        int depth = result.depth, score = result.score; long nodes = result.nodes;
        boolean scored = false, bound = false; int pvAt = -1;
        for (int i = 1; i < tokens.length; i++) {
            try {
                if (tokens[i].equals("multipv") && i + 1 < tokens.length && !tokens[++i].equals("1")) return;
                if (tokens[i].equals("depth") && i + 1 < tokens.length) depth = Math.max(0, Integer.parseInt(tokens[++i]));
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
        if (!scored || bound) return;
        List<String> pv = new ArrayList<>();
        Position cursor = root.copy();
        if (pvAt >= 0) {
            for (int i = pvAt; i < tokens.length && pv.size() < 256; i++) {
                try { String move = Position.normalizeMove(tokens[i]); cursor = cursor.play(move); pv.add(move); }
                catch (IllegalArgumentException invalid) { break; }
            }
            if (pv.isEmpty()) return;
        }
        result.depth = depth; result.nodes = nodes;
        result.score = root.redToMove ? score : -score;
        if (!pv.isEmpty()) { result.pv = pv; result.bestMove = pv.get(0); }
    }
}
