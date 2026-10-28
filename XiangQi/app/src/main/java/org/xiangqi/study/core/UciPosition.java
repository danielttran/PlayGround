package org.xiangqi.study.core;

import java.util.Arrays;
import java.util.List;

/** Validates a complete replay before sending its history to a native UCI engine. */
public final class UciPosition {
    private UciPosition() {}
    public static String command(Position current, String startFen, List<String> history) {
        if (history == null || history.size() > 20000) throw new IllegalArgumentException("Missing or excessive position history");
        Position cursor = Position.fromFen(startFen);
        StringBuilder command = new StringBuilder("position ");
        command.append(cursor.toFen().equals(Position.START_FEN) ? "startpos" : "fen " + cursor.toFen());
        if (!history.isEmpty()) command.append(" moves");
        for (int i = 0; i < history.size(); i++) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("History validation cancelled");
            String move = Position.normalizeMove(history.get(i));
            try { cursor = cursor.play(move); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("History ply " + (i + 1) + ": " + e.getMessage()); }
            command.append(' ').append(move);
        }
        if (cursor.redToMove != current.redToMove || !Arrays.equals(cursor.board, current.board))
            throw new IllegalArgumentException("History does not reach the analysis position");
        return command.toString();
    }
}
