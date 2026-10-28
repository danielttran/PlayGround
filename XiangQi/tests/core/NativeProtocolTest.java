package org.xiangqi.study.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Consumes real native output with the exact parser used by the Android adapter. */
public final class NativeProtocolTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: NativeProtocolTest transcript.txt 'history moves'");
        List<String> history = Arrays.asList(args[1].split(" "));
        Position root = Position.initial();
        for (String move : history) root = root.play(move);
        String expectedPosition = "> " + UciPosition.command(root, Position.START_FEN, history);
        List<String> lines = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
        if (!lines.contains(expectedPosition) || !lines.contains("uciok") || !lines.contains("readyok"))
            throw new AssertionError("Actual native UCI initialization/history sequence missing");
        Engine.Result result = new Engine.Result();
        String best = null; int scoredLines = 0;
        Pattern rawScore = Pattern.compile("\\bscore (cp|mate) (-?\\d+)\\b");
        for (String line : lines) {
            int previousDepth = result.depth;
            UciInfo.update(result, root, line);
            if (result.depth > previousDepth) {
                Matcher matcher = rawScore.matcher(line);
                if (!matcher.find()) throw new AssertionError("Parsed depth has no raw score");
                int raw = Integer.parseInt(matcher.group(2));
                int perspective = root.redToMove ? result.score : -result.score;
                if (matcher.group(1).equals("cp") && perspective != raw)
                    throw new AssertionError("Native centipawn perspective mismatch");
                if (matcher.group(1).equals("mate") && Integer.signum(perspective) != Integer.signum(raw))
                    throw new AssertionError("Native mate perspective mismatch");
                scoredLines++;
            }
            if (line.startsWith("bestmove ")) best = line.split("\\s+")[1];
        }
        if (best == null || !root.legalMoves().contains(best)) throw new AssertionError("Native best move is illegal");
        if (result.depth < 1 || result.nodes < 1 || scoredLines < 1 || result.pv.isEmpty())
            throw new AssertionError("No completed native analysis parsed");
        if (!result.bestMove.equals(best)) throw new AssertionError("Native PV and bestmove disagree");
        Position cursor = root;
        for (String move : result.pv) cursor = cursor.play(move);
        System.out.println("PASS: native " + (root.redToMove ? "Red" : "Black") + " opening history; depth " + result.depth
            + ", nodes " + result.nodes + ", best " + best + ", score " + result.score + "cp (Red), "
            + scoredLines + " completed UCI scores and " + result.pv.size() + " legal PV moves");
    }
}
