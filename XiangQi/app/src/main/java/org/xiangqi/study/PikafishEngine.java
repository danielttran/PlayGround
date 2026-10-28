package org.xiangqi.study;

import android.content.Context;

import org.xiangqi.study.core.Engine;
import org.xiangqi.study.core.Position;
import org.xiangqi.study.core.UciInfo;
import org.xiangqi.study.core.UciPosition;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/** Runs the bundled, extracted Android PIE engine using its UCI protocol. Background use only. */
public final class PikafishEngine {
    private static final String EOF = "\u0000EOF";
    private PikafishEngine() {}

    public static boolean available(Context context) {
        return binary(context).isFile();
    }

    private static File binary(Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir, "libpikafish.so");
    }

    private static synchronized File prepareNetwork(Context context) throws Exception {
        File directory = new File(context.getFilesDir(), "pikafish-2026-09-06");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create Pikafish data directory");
        File network = new File(directory, "pikafish.nnue");
        if (network.isFile() && network.length() > 0) return network;
        File temporary = new File(directory, "pikafish.nnue.tmp");
        boolean done = false;
        try {
            try (InputStream input = context.getAssets().open("pikafish.nnue"); FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[262144]; int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Pikafish initialization cancelled");
                    output.write(buffer, 0, read);
                }
                output.getFD().sync();
            }
            if (temporary.length() == 0 || !temporary.renameTo(network)) throw new IllegalStateException("Cannot install bundled Pikafish network");
            done = true;
            return network;
        } finally { if (!done) temporary.delete(); }
    }

    public static Engine.Result analyze(Context context, Position position, int maxDepth, long millis) throws Exception {
        return analyze(context, position, position.toFen(), Collections.emptyList(), maxDepth, millis);
    }

    public static Engine.Result analyze(Context context, Position position, String startFen, List<String> history, int maxDepth, long millis) throws Exception {
        if (!available(context)) throw new IllegalStateException("Pikafish is not bundled for this device ABI");
        Position root = position.copy(); List<String> legal = root.legalMoves();
        String positionCommand = UciPosition.command(root, startFen, history);
        if (legal.isEmpty()) {
            Engine.Result terminal = new Engine.Result(); terminal.score = root.redToMove ? -30000 : 30000; return terminal;
        }
        File network = prepareNetwork(context);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Pikafish analysis cancelled");
        Process process = new ProcessBuilder(binary(context).getAbsolutePath()).directory(network.getParentFile()).redirectErrorStream(true).start();
        BlockingQueue<String> lines = new ArrayBlockingQueue<>(2048);
        Thread reader = new Thread(() -> {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = input.readLine()) != null) {
                    if (line.length() > 32768) continue;
                    if (!lines.offer(line)) { lines.poll(); lines.offer(line); }
                }
            } catch (Exception ignored) {
                // The consuming thread reports EOF, exit status, or its timeout.
            } finally { if (!lines.offer(EOF)) { lines.poll(); lines.offer(EOF); } }
        }, "Pikafish-output");
        reader.setDaemon(true); reader.start();
        PrintWriter output = new PrintWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);
        try {
            command(output, "uci");
            await(lines, "uciok", 15000);
            command(output, "setoption name Threads value 1");
            command(output, "setoption name Hash value 32");
            command(output, "setoption name MultiPV value 1");
            command(output, "setoption name EvalFile value " + network.getAbsolutePath());
            command(output, "isready"); await(lines, "readyok", 20000);
            command(output, "ucinewgame");
            command(output, positionCommand);
            long budget = Math.max(50, Math.min(millis, 60000));
            command(output, "go depth " + Math.max(1, Math.min(maxDepth, 128)) + " movetime " + budget);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budget + 5000);
            Engine.Result result = new Engine.Result();
            while (true) {
                String line = next(lines, deadline);
                if (line.startsWith("info ")) UciInfo.update(result, root, line);
                if (line.startsWith("bestmove ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length < 2 || !legal.contains(parts[1])) throw new IllegalStateException("Pikafish returned an invalid best move");
                    result.bestMove = parts[1];
                    if (result.pv.isEmpty() || !result.pv.get(0).equals(result.bestMove)) result.pv = Collections.singletonList(result.bestMove);
                    if (result.depth == 0) throw new IllegalStateException("Pikafish returned no completed analysis");
                    return result;
                }
            }
        } finally {
            // Cancellation never waits on a blocking read from the child process.
            output.println("stop"); output.println("quit"); output.close();
            process.destroy();
            if (process.isAlive()) process.destroyForcibly();
            reader.interrupt();
        }
    }

    private static void command(PrintWriter output, String text) {
        output.println(text);
        if (output.checkError()) throw new IllegalStateException("Pikafish input pipe closed");
    }

    private static void await(BlockingQueue<String> lines, String expected, long millis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (!next(lines, deadline).equals(expected)) { /* consume introductory UCI information */ }
    }

    private static String next(BlockingQueue<String> lines, long deadline) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Pikafish analysis cancelled");
        long left = deadline - System.nanoTime();
        if (left <= 0) throw new IllegalStateException("Pikafish timed out");
        String line = lines.poll(left, TimeUnit.NANOSECONDS);
        if (line == null) throw new IllegalStateException("Pikafish timed out");
        if (line.equals(EOF)) throw new IllegalStateException("Pikafish exited before completing analysis");
        if (line.contains("CRITICAL ERROR") || line.startsWith("ERROR")) throw new IllegalStateException(line);
        return line;
    }
}
