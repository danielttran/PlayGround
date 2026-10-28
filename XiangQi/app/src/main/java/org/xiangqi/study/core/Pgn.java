package org.xiangqi.study.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict, main-line ICCS PGN import. Chinese/WXF notation is intentionally rejected. */
public final class Pgn {
    private Pgn() {}
    public static final class Game {
        public String id = "", event = "Imported game", round = "", date = "", red = "Red", black = "Black", result = "*", sourceUrl = "", timeControl = "", startFen = Position.START_FEN;
        public List<String> moves = new ArrayList<>();
        public String collection = "";
    }
    // Possessive repetition keeps long quoted values iterative rather than
    // recursively backtracking into a StackOverflowError on imported text.
    private static final Pattern TAG = Pattern.compile("\\[([A-Za-z][A-Za-z0-9_]*)\\s+\"((?:\\\\.|[^\"\\\\])*+)\"\\s*\\]");
    private static final Pattern COORDINATE = Pattern.compile("[a-iA-I][0-9][-–]?[a-iA-I][0-9][!?]*");

    public static List<Game> parse(String text) {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("The file is empty");
        if (text.length() > 8000000) throw new IllegalArgumentException("PGN is too large (8 MB text limit)");
        // Old game files can use CR-only lines; comments must stop at every
        // logical newline rather than silently swallowing the rest of a game.
        text = text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
        List<Game> games = new ArrayList<>();
        Map<String, String> tags = new LinkedHashMap<>();
        List<String> moves = new ArrayList<>();
        String bodyResult = null;
        int i = 0;
        while (i < text.length()) {
            if (Thread.currentThread().isInterrupted()) throw error(games, "PGN import cancelled");
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '{') { int end = text.indexOf('}', i + 1); if (end < 0) throw error(games, "Unclosed PGN comment"); i = end + 1; continue; }
            if (c == ';' || c == '%') { int end = text.indexOf('\n', i + 1); i = end < 0 ? text.length() : end + 1; continue; }
            if (c == '(') { i = skipVariation(text, i, games); continue; }
            if (c == ')' || c == '}') throw error(games, "Unexpected PGN closing delimiter");
            if (c == '[') {
                if (!moves.isEmpty() || bodyResult != null) {
                    games.add(finish(tags, moves, bodyResult, games.size() + 1));
                    tags = new LinkedHashMap<>(); moves = new ArrayList<>(); bodyResult = null;
                }
                Matcher matcher = TAG.matcher(text); matcher.region(i, text.length());
                if (!matcher.lookingAt()) throw error(games, "Malformed PGN tag near character " + i);
                String key = matcher.group(1), value = matcher.group(2).replace("\\\"", "\"").replace("\\\\", "\\");
                if (tags.containsKey(key)) throw error(games, "Repeated PGN tag: " + preview(key));
                tags.put(key, value); i = matcher.end(); continue;
            }
            int end = i;
            while (end < text.length() && !Character.isWhitespace(text.charAt(end)) && "{}();[]".indexOf(text.charAt(end)) < 0) end++;
            if (end == i) throw error(games, "Unexpected character near " + i);
            String token = text.substring(i, end); i = end;
            token = token.replaceFirst("^\\d+\\.{1,3}", "");
            if (token.isEmpty() || token.matches("\\.{1,3}") || token.matches("\\$\\d+") || token.matches("[!?]+")) continue;
            if (isResult(token)) {
                if (bodyResult != null) throw error(games, "Repeated game result");
                bodyResult = token; continue;
            }
            if (bodyResult != null) throw error(games, "Moves found after game result");
            if (!COORDINATE.matcher(token).matches())
                throw error(games, "Unsupported move at ply " + (moves.size() + 1) + ": " + preview(token) + ". Import ICCS coordinates such as b2e2.");
            moves.add(Position.normalizeMove(token.replaceAll("[!?]+$", "")));
            if (moves.size() > 20000) throw error(games, "Game exceeds 20,000 half-moves");
        }
        if (!tags.isEmpty() || !moves.isEmpty() || bodyResult != null) games.add(finish(tags, moves, bodyResult, games.size() + 1));
        if (games.isEmpty()) throw new IllegalArgumentException("No games found in the file");
        return games;
    }

    private static int skipVariation(String text, int i, List<Game> games) {
        int depth = 1; i++;
        while (i < text.length()) {
            if (Thread.currentThread().isInterrupted()) throw error(games, "PGN import cancelled");
            char c = text.charAt(i++);
            if (c == '{') { int end = text.indexOf('}', i); if (end < 0) throw error(games, "Unclosed comment in variation"); i = end + 1; }
            else if (c == ';' || c == '%') { int end = text.indexOf('\n', i); i = end < 0 ? text.length() : end + 1; }
            else if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        throw error(games, "Unclosed PGN variation");
    }

    private static Game finish(Map<String, String> tags, List<String> moves, String bodyResult, int number) {
        Game g = new Game();
        g.event = tags.getOrDefault("Event", "Imported game");
        g.round = tags.getOrDefault("Round", ""); g.date = tags.getOrDefault("Date", "");
        g.red = tags.getOrDefault("Red", tags.getOrDefault("White", "Red"));
        g.black = tags.getOrDefault("Black", "Black");
        g.sourceUrl = tags.getOrDefault("Source", tags.getOrDefault("Site", ""));
        g.timeControl = tags.getOrDefault("TimeControl", "");
        g.collection = tags.getOrDefault("Collection", "");
        String variant = tags.getOrDefault("Variant", "Xiangqi");
        if (!variant.equalsIgnoreCase("Xiangqi") && !variant.equalsIgnoreCase("Chinese Chess"))
            throw new IllegalArgumentException("Game " + number + ": unsupported variant " + preview(variant));
        g.result = bodyResult != null ? bodyResult : tags.getOrDefault("Result", "*");
        if (!isResult(g.result)) throw new IllegalArgumentException("Game " + number + ": invalid result " + preview(g.result));
        String headerResult = tags.get("Result");
        if (bodyResult != null && headerResult != null && !bodyResult.equals(headerResult))
            throw new IllegalArgumentException("Game " + number + ": header and move-text results disagree");
        if ("1".equals(tags.get("SetUp")) && !tags.containsKey("FEN"))
            throw new IllegalArgumentException("Game " + number + ": SetUp requires a FEN starting position");
        g.startFen = tags.getOrDefault("FEN", Position.START_FEN);
        Position position;
        try { position = Position.fromFen(g.startFen); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Game " + number + ": " + e.getMessage()); }
        if (moves.isEmpty()) throw new IllegalArgumentException("Game " + number + " has no moves");
        for (int ply = 0; ply < moves.size(); ply++) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalArgumentException("Game " + number + ": PGN import cancelled");
            try { position = position.play(moves.get(ply)); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Game " + number + ", ply " + (ply + 1) + ": " + e.getMessage()); }
        }
        g.moves = new ArrayList<>(moves);
        String identity = g.startFen + "|" + String.join(" ", moves);
        g.id = "import-" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        return g;
    }
    private static boolean isResult(String text) { return text.equals("1-0") || text.equals("0-1") || text.equals("1/2-1/2") || text.equals("*"); }
    private static String preview(String text) { return text.length()>80 ? text.substring(0,80)+"..." : text; }
    private static IllegalArgumentException error(List<Game> games, String message) { return new IllegalArgumentException("Game " + (games.size() + 1) + ": " + message); }
}
