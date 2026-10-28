package org.xiangqi.study.core;

import java.util.Arrays;
import java.util.List;

/** Validates a complete replay before sending its history to a native UCI engine. */
public final class UciPosition {
    private UciPosition() {}
    public static String command(Position current, String startFen, List<String> history) {
        if (history == null || history.size() > 20000) throw new IllegalArgumentException("Missing or excessive position history");
        // Game packs omit startFen for ordinary games, matching Game.initial().
        // Keep that default at this boundary too, rather than rejecting every
        // preloaded game when the native engine is selected.
        Position cursor = Position.fromFen(startFen == null || startFen.trim().isEmpty() ? Position.START_FEN : startFen);
        StringBuilder command = new StringBuilder("position ");
        String nativeFen=cursor.toFen();String[] fields=nativeFen.split(" ");
        // Standalone imported study FENs may carry clocks outside the native
        // engine's accepted range. Preserve the board/turn and reset only those
        // unsupported counters. Recorded move histories are sent in full.
        if(Integer.parseInt(fields[4])>119)fields[4]="0";
        if(Integer.parseInt(fields[5])>100000)fields[5]="1";
        nativeFen=String.join(" ",fields);
        command.append(nativeFen.equals(Position.START_FEN) ? "startpos" : "fen " + nativeFen);
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
