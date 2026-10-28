package org.xiangqi.study.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Xiangqi movement and king safety. Index 0 is a9; index 89 is i0. */
public final class Position {
    public static final String START_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
    public final char[] board;
    public boolean redToMove;
    private int fullMove;
    private int halfMove;

    private Position(char[] board, boolean red, int halfMove, int fullMove) {
        this.board = board; this.redToMove = red; this.halfMove = halfMove; this.fullMove = fullMove;
    }

    public static Position initial() { return fromFen(START_FEN); }
    public Position copy() { return new Position(board.clone(), redToMove, halfMove, fullMove); }
    public static boolean isRed(char piece) { return piece >= 'A' && piece <= 'Z'; }

    public static Position fromFen(String fen) {
        if (fen == null) throw new IllegalArgumentException("Missing FEN");
        String[] fields = fen.trim().split("\\s+");
        String[] rows = fields[0].split("/", -1);
        if (rows.length != 10) throw new IllegalArgumentException("Xiangqi FEN needs 10 ranks");
        char[] board = new char[90];
        int redKing = 0, blackKing = 0;
        for (int y = 0; y < 10; y++) {
            int x = 0;
            for (char original : rows[y].toCharArray()) {
                if (original >= '1' && original <= '9') x += original - '0';
                else {
                    char p = original;
                    if (p == 'h') p = 'n'; if (p == 'H') p = 'N';
                    if (p == 'e') p = 'b'; if (p == 'E') p = 'B';
                    if ("rnbakcpRNBAKCP".indexOf(p) < 0 || x >= 9)
                        throw new IllegalArgumentException("Invalid FEN piece or rank width");
                    board[y * 9 + x++] = p;
                    if (p == 'K') redKing++; if (p == 'k') blackKing++;
                }
                if (x > 9) throw new IllegalArgumentException("FEN rank is too wide");
            }
            if (x != 9) throw new IllegalArgumentException("Each FEN rank needs 9 files");
        }
        if (redKing != 1 || blackKing != 1) throw new IllegalArgumentException("FEN needs exactly one general per side");
        String turn = fields.length > 1 ? fields[1] : "w";
        if (!turn.equals("w") && !turn.equals("r") && !turn.equals("b"))
            throw new IllegalArgumentException("FEN side must be w or b");
        int half = parseCounter(fields, 4, 0), full = parseCounter(fields, 5, 1);
        return new Position(board, !turn.equals("b"), half, Math.max(1, full));
    }

    private static int parseCounter(String[] fields, int index, int fallback) {
        if (fields.length <= index) return fallback;
        try { int value = Integer.parseInt(fields[index]); if (value < 0) throw new NumberFormatException(); return value; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid FEN move counter"); }
    }

    public String toFen() {
        StringBuilder s = new StringBuilder();
        for (int y = 0; y < 10; y++) {
            if (y > 0) s.append('/');
            int empty = 0;
            for (int x = 0; x < 9; x++) {
                char p = board[y * 9 + x];
                if (p == 0) empty++;
                else { if (empty > 0) s.append(empty); empty = 0; s.append(p); }
            }
            if (empty > 0) s.append(empty);
        }
        return s.append(redToMove ? " w - - " : " b - - ").append(halfMove).append(' ').append(fullMove).toString();
    }

    public static int index(String square) {
        if (square == null || square.length() != 2) throw new IllegalArgumentException("Invalid ICCS square: " + square);
        char file = Character.toLowerCase(square.charAt(0)), rank = square.charAt(1);
        if (file < 'a' || file > 'i' || rank < '0' || rank > '9') throw new IllegalArgumentException("Invalid ICCS square: " + square);
        return (9 - (rank - '0')) * 9 + file - 'a';
    }
    public static String square(int index) {
        if (index < 0 || index >= 90) throw new IllegalArgumentException("Invalid board index");
        return "" + (char)('a' + index % 9) + (char)('0' + 9 - index / 9);
    }

    public Position play(String move) {
        String normalized = normalizeMove(move);
        if (!legalMoves().contains(normalized)) throw new IllegalArgumentException("Illegal move " + move + " (" + (redToMove ? "Red" : "Black") + " to move)");
        return playUnchecked(normalized);
    }
    public static String normalizeMove(String move) {
        if (move == null) throw new IllegalArgumentException("Missing move");
        String result = move.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("–", "");
        if (!result.matches("[a-i][0-9][a-i][0-9]")) throw new IllegalArgumentException("Expected ICCS coordinate move, got " + (move.length()>80?move.substring(0,80)+"...":move));
        return result;
    }

    Position playUnchecked(String move) {
        int from = index(move.substring(0, 2)), to = index(move.substring(2));
        return playUnchecked(from, to);
    }

    private Position playUnchecked(int from, int to) {
        char p = board[from], captured = board[to];
        Position next = copy();
        next.board[to] = p; next.board[from] = 0;
        next.redToMove = !redToMove;
        next.halfMove = captured != 0 || Character.toLowerCase(p) == 'p' ? 0 : halfMove + 1;
        if (!redToMove) next.fullMove++;
        return next;
    }

    public List<String> legalMoves() {
        List<String> result = new ArrayList<>(48);
        for (int from = 0; from < 90; from++) {
            char p = board[from];
            if (p == 0 || isRed(p) != redToMove) continue;
            for (int to : targets(from, false)) {
                if (Character.toLowerCase(board[to]) == 'k') continue;
                if (!playUnchecked(from, to).isInCheck(redToMove)) result.add(square(from) + square(to));
            }
        }
        return result;
    }

    public boolean isInCheck(boolean red) {
        int king = -1;
        for (int i = 0; i < 90; i++) if (board[i] == (red ? 'K' : 'k')) { king = i; break; }
        if (king < 0) return true;
        for (int i = 0; i < 90; i++)
            if (board[i] != 0 && isRed(board[i]) != red && targets(i, true).contains(king)) return true;
        return false;
    }

    private List<Integer> targets(int from, boolean attacks) {
        List<Integer> out = new ArrayList<>(16);
        char piece = board[from]; boolean red = isRed(piece);
        int x = from % 9, y = from / 9;
        switch (Character.toLowerCase(piece)) {
            case 'r': case 'c':
                for (int[] d : ORTHOGONAL) {
                    boolean screen = false;
                    for (int nx = x + d[0], ny = y + d[1]; inside(nx, ny); nx += d[0], ny += d[1]) {
                        int to = ny * 9 + nx; char target = board[to];
                        if (Character.toLowerCase(piece) == 'r') {
                            if (target == 0 || isRed(target) != red) out.add(to);
                            if (target != 0) break;
                        } else if (!screen) {
                            if (target == 0) out.add(to); else screen = true;
                        } else if (target != 0) {
                            if (isRed(target) != red) out.add(to);
                            break;
                        }
                    }
                }
                break;
            case 'n':
                for (int[] d : HORSE) {
                    int legX = x + (Math.abs(d[0]) == 2 ? d[0] / 2 : 0);
                    int legY = y + (Math.abs(d[1]) == 2 ? d[1] / 2 : 0);
                    if (inside(legX, legY) && board[legY * 9 + legX] == 0) add(out, x + d[0], y + d[1], red);
                }
                break;
            case 'b':
                for (int dx : new int[]{-2, 2}) for (int dy : new int[]{-2, 2}) {
                    int ny = y + dy;
                    if (inside(x + dx, ny) && (red ? ny >= 5 : ny <= 4) && board[(y + dy / 2) * 9 + x + dx / 2] == 0)
                        add(out, x + dx, ny, red);
                }
                break;
            case 'a':
                for (int dx : new int[]{-1, 1}) for (int dy : new int[]{-1, 1})
                    if (palace(x + dx, y + dy, red)) add(out, x + dx, y + dy, red);
                break;
            case 'k':
                for (int[] d : ORTHOGONAL) if (palace(x + d[0], y + d[1], red)) add(out, x + d[0], y + d[1], red);
                // The opposing general attacks along the entire unblocked file.
                if (attacks) for (int dy : new int[]{-1, 1}) {
                    for (int ny = y + dy; inside(x, ny); ny += dy) {
                        char p = board[ny * 9 + x];
                        if (p != 0) { if (p == (red ? 'k' : 'K')) out.add(ny * 9 + x); break; }
                    }
                }
                break;
            case 'p':
                add(out, x, y + (red ? -1 : 1), red);
                if (red ? y <= 4 : y >= 5) { add(out, x - 1, y, red); add(out, x + 1, y, red); }
                break;
            default: break;
        }
        return out;
    }

    private void add(List<Integer> out, int x, int y, boolean red) {
        if (inside(x, y)) { int to = y * 9 + x; if (board[to] == 0 || isRed(board[to]) != red) out.add(to); }
    }
    private static boolean inside(int x, int y) { return x >= 0 && x < 9 && y >= 0 && y < 10; }
    private static boolean palace(int x, int y, boolean red) { return x >= 3 && x <= 5 && (red ? y >= 7 && y <= 9 : y >= 0 && y <= 2); }
    private static final int[][] ORTHOGONAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] HORSE = {{1, 2}, {-1, 2}, {1, -2}, {-1, -2}, {2, 1}, {2, -1}, {-2, 1}, {-2, -1}};
}
