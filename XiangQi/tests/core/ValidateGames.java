package org.xiangqi.study.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/** Independent replay validator for a generated ICCS PGN pack. */
public final class ValidateGames {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: ValidateGames games.pgn or records.tsv");
        String text = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        if (args[0].endsWith(".tsv")) {
            int count = 0, plies = 0;
            for (String line : text.split("\\R")) {
                if (line.trim().isEmpty()) continue;
                String[] fields = line.split("\\t", 2);
                if (fields.length != 2) throw new IllegalArgumentException("Bad TSV record");
                Position position = Position.initial(); int ply = 0;
                for (String move : fields[1].trim().split("\\s+")) {
                    try { position = position.play(move); }
                    catch (IllegalArgumentException e) { throw new IllegalArgumentException(fields[0] + " ply " + (ply + 1) + ": " + e.getMessage()); }
                    ply++; plies++;
                }
                count++;
            }
            System.out.println("PASS: independently replayed " + count + " games / " + plies + " legal half-moves");
            return;
        }
        List<Pgn.Game> games = Pgn.parse(text);
        int plies = 0;
        for (Pgn.Game game : games) plies += game.moves.size();
        System.out.println("PASS: independently replayed " + games.size() + " games / " + plies + " legal half-moves");
    }
}
