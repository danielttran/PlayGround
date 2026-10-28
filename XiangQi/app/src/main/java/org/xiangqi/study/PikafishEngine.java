package org.xiangqi.study;

import android.content.Context;
import android.os.Build;

import org.xiangqi.study.core.Engine;
import org.xiangqi.study.core.Position;
import org.xiangqi.study.core.UciInfo;
import org.xiangqi.study.core.UciPosition;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/** Runs the bundled standalone Android engine using UCI. Background use only. */
public final class PikafishEngine {
    private static final String EOF = "\u0000EOF";
    private static final long NETWORK_BYTES = PikafishRelease.NNUE_BYTES;
    private static final String NETWORK_SHA256 = PikafishRelease.NNUE_SHA256;
    private static File verifiedNetwork;
    private static long verifiedModified;
    private PikafishEngine() {}

    /** A zero time limit searches until the requested depth or explicit cancellation. */
    public static final class Options {
        public final int maxDepth, threads, hashMb, lines;
        public final long millis;
        public Options(int maxDepth, long millis, int threads, int hashMb) {
            this(maxDepth, millis, threads, hashMb, 1);
        }
        public Options(int maxDepth, long millis, int threads, int hashMb, int lines) {
            this.maxDepth = Math.max(1, Math.min(maxDepth, 128));
            this.millis = millis <= 0 ? 0 : Math.max(50, Math.min(millis, 3600000));
            this.threads = Math.max(1, Math.min(threads, Math.max(1, Runtime.getRuntime().availableProcessors())));
            this.hashMb = Math.max(16, Math.min(hashMb, 512));
            this.lines = Math.max(1, Math.min(lines, 5));
        }
    }

    public static boolean available(Context context) {
        return Build.VERSION.SDK_INT >= PikafishRelease.MIN_ANDROID_API && binary(context).isFile();
    }

    private static File binary(Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir, "libpikafish.so");
    }

    private static synchronized File prepareNetwork(Context context) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Pikafish initialization cancelled");
        File directory = new File(context.getFilesDir(), "pikafish-"+PikafishRelease.VERSION);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create Pikafish data directory");
        File network = new File(directory, "pikafish.nnue");
        if (network.isFile() && network.length() == NETWORK_BYTES) {
            if (network.equals(verifiedNetwork) && network.lastModified() == verifiedModified) return network;
            try (InputStream input = Files.newInputStream(network.toPath())) {
                if (copyAndHash(input, null).equals(NETWORK_SHA256)) {
                    markVerified(network); return network;
                }
            }
        }
        File temporary = new File(directory, "pikafish.nnue.tmp");
        boolean done = false;
        try {
            try (InputStream input = context.getAssets().open("pikafish.nnue"); FileOutputStream output = new FileOutputStream(temporary)) {
                String digest = copyAndHash(input, output);
                output.getFD().sync();
                if (temporary.length() != NETWORK_BYTES || !digest.equals(NETWORK_SHA256))
                    throw new IOException("Bundled Pikafish network failed its integrity check");
            }
            try { Files.move(temporary.toPath(), network.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary.toPath(), network.toPath(), StandardCopyOption.REPLACE_EXISTING); }
            markVerified(network);
            done = true;
            return network;
        } finally { if (!done) temporary.delete(); }
    }

    private static String copyAndHash(InputStream input, FileOutputStream output) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[262144]; int read;
        while ((read = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Pikafish initialization cancelled");
            digest.update(buffer, 0, read);
            if (output != null) output.write(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
        return result.toString();
    }

    private static void markVerified(File network) {
        verifiedNetwork = network;
        verifiedModified = network.lastModified();
    }

    public static Engine.Result analyze(Context context, Position position, int maxDepth, long millis) throws Exception {
        return analyze(context, position, position.toFen(), Collections.emptyList(), maxDepth, millis);
    }

    public static Engine.Result analyze(Context context, Position position, String startFen, List<String> history, int maxDepth, long millis) throws Exception {
        return analyze(context, position, startFen, history, new Options(maxDepth, millis, 1, 32), null);
    }

    public static Engine.Result analyze(Context context, Position position, String startFen, List<String> history, Options options, Engine.ProgressListener listener) throws Exception {
        if (options == null) throw new IllegalArgumentException("Analysis options are required");
        if (!available(context)) throw new IllegalStateException("Pikafish requires Android 10 or later and a supported bundled device ABI");
        Position root = position.copy(); List<String> legal = root.legalMoves();
        String positionCommand = UciPosition.command(root, startFen, history);
        if (legal.isEmpty()) {
            Engine.Result terminal = new Engine.Result(); terminal.score = root.redToMove ? -30000 : 30000;
            if (listener != null) listener.onProgress(terminal.copy());
            return terminal;
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
            command(output, "setoption name Threads value " + options.threads);
            command(output, "setoption name Hash value " + options.hashMb);
            command(output, "setoption name MultiPV value " + options.lines);
            command(output, "setoption name EvalFile value " + network.getAbsolutePath());
            command(output, "isready"); await(lines, "readyok", 20000);
            command(output, "ucinewgame");
            command(output, positionCommand);
            command(output, "go depth " + options.maxDepth + (options.millis > 0 ? " movetime " + options.millis : ""));
            long deadline = options.millis <= 0 ? Long.MAX_VALUE : System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(options.millis + 5000);
            Engine.Result result = new Engine.Result();
            int publishedDepth = 0;
            while (true) {
                String line = next(lines, deadline);
                if (line.startsWith("info ")) {
                    List<Engine.Line> previousLines = result.lines;
                    UciInfo.update(result, root, line, options.lines);
                    // Publish secondary-line updates too; each accepted line has
                    // its own exact score and legally replayed continuation.
                    if (listener != null && result.lines != previousLines && result.depth >= publishedDepth) {
                        listener.onProgress(result.copy()); publishedDepth = result.depth;
                    }
                }
                if (line.startsWith("bestmove ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length < 2 || !legal.contains(parts[1])) throw new IllegalStateException("Pikafish returned an invalid best move");
                    result.bestMove = parts[1];
                    if (result.pv.isEmpty() || !result.pv.get(0).equals(result.bestMove)) result.pv = Collections.singletonList(result.bestMove);
                    if (result.depth == 0) throw new IllegalStateException("Pikafish returned no completed analysis");
                    // Empty-PV root adjudications acquire their validated move
                    // only at bestmove; keep the first-line compatibility API
                    // and the ranked result consistent at completion.
                    List<Engine.Line> completed = new java.util.ArrayList<>();
                    completed.add(new Engine.Line(1, result.score, result.depth, result.pv));
                    for (Engine.Line alternative : result.lines)
                        if (alternative.rank > 1 && !result.bestMove.equals(alternative.bestMove)) completed.add(alternative);
                    result.lines = Collections.unmodifiableList(completed);
                    return result;
                }
            }
        } finally {
            // Cancellation never waits on a blocking read from the child process.
            output.println("stop"); output.println("quit"); output.close();
            process.destroy();
            if (process.isAlive()) process.destroyForcibly();
            // Destruction is asynchronous on some platforms. Bound the reap so
            // a following search cannot overlap an exiting worker or its files.
            boolean interrupted=Thread.interrupted();
            try { process.waitFor(500,TimeUnit.MILLISECONDS); }
            catch(InterruptedException ignored) { interrupted=true; }
            finally { if(interrupted)Thread.currentThread().interrupt(); }
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
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Pikafish analysis cancelled");
            long left = deadline == Long.MAX_VALUE ? TimeUnit.MILLISECONDS.toNanos(250) : deadline - System.nanoTime();
            if (left <= 0) throw new IllegalStateException("Pikafish timed out");
            String line = lines.poll(Math.min(left, TimeUnit.MILLISECONDS.toNanos(250)), TimeUnit.NANOSECONDS);
            if (line == null) continue;
            if (line.equals(EOF)) throw new IllegalStateException("Pikafish exited before completing analysis");
            if (line.contains("CRITICAL ERROR") || line.startsWith("ERROR")) throw new IllegalStateException(line);
            return line;
        }
    }
}
