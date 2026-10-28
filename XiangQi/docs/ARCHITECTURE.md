# Architecture

Xiangqi Study is a Java Android application targeting API 35, with minimum API 26. It uses Android framework widgets and a custom Canvas board; no web view or remote service is required. The Gradle wrapper is pinned to 8.11.1 and the Android Gradle Plugin to 8.9.3.

## UI and study state

`MainActivity` builds the searchable library and replay screen. `BoardView` draws the nine-file, ten-rank board, pieces, selection, and legal targets. Replay positions are reconstructed from a game's starting FEN and move sequence. Exploring a variation works on a separate sequence so the recorded game and its source result remain intact. Exporting a variation gives it the unfinished result `*`.

Shared preferences retain the last game, replay offset, board orientation, variation, appearance, coordinates and spoiler controls. Library and replay have explicit Red/Black player labels and guarded horizontal navigation. Swipe detection excludes sliders, text fields, spinners and multitouch. A shared serial file-I/O executor outlives Activity recreation; each Activity owns its analysis executor. Analysis generation identifiers reject stale progress and final results. Leaving the app or changing the position cancels search and autoplay; explicit Stop retains the latest estimate and arrow.

## Records and import boundary

`GameRepository` combines the read-only bundled `games.json` with private `imported-games.json`. Imported records are validated in full before an `AtomicFile` write; failure leaves the existing library intact. A process-wide storage lock and reload-before-commit protect imports made by different Activity instances. Atomic-file reads recover interrupted writes. Fingerprints use starting FEN and canonical moves. Files and saved imports are capped at 5 MB, with metadata, game/move-count and parsing-depth bounds.

`Pgn` imports tagged ICCS-coordinate PGN main lines, retaining player/event metadata, starting FEN, result, source, and time control. Comments, numeric annotations, and balanced side variations are ignored. Unsupported notation and illegal moves produce errors identifying the game and half-move. The complete batch is rejected on an error. JSON imports use the same position/move validation. XQF, CBR, CBL, WXF move notation, and Chinese move notation are not supported yet.

The starter asset contains 285 adult 2025 World Championship records and 23,340 half-moves, with official classical time control `3600+30`. Requested players and the men's final are prioritized. These are archived championship games, not a list of the world's top 285 ranked games. The downloader, source hashes, exclusions, and dated name-screening policy are documented in [SOURCES.md](SOURCES.md). Source-name matches are conservative filters, rather than proof that two people sharing a name are the same person.

## Position representation and rules

The Android-free `core` package uses a 90-cell character array, row-major from Black's top-left (`a9`, index 0) to Red's bottom-right (`i0`, index 89). Uppercase pieces are Red, lowercase pieces Black, and `\0` is empty. Piece letters are `RNBAKCP`, with horse/elephant FEN aliases accepted on input. ICCS move strings are four characters, such as `b2e2`.

`Position.play` returns a new position after checking piece movement, blockers, river/palace restrictions, king safety, and facing generals. No legal moves is a loss in Xiangqi, including stalemate. The replay board's legal-move layer does not adjudicate repetition, perpetual check/chase, clocks, resignations, or tournament conduct. A source result is retained as metadata and is not inferred from the final board.

The core harness checks all piece types, check and flying generals, PGN rejection behavior, score perspective, cancellation, and independent initial perft counts `44 / 1920 / 79666`. The full starter library was independently replayed through this Java rules implementation.

## Analysis engines

`Engine` is the built-in, bounded alpha-beta search with material/position evaluation. Iterative deepening retains the latest completed result and returns a legal fallback if interrupted before completing a depth. It is a lightweight study engine, with no grandmaster-strength claim and no tournament repetition adjudication.

`PikafishEngine` uses the official Pikafish 2026-09-06 static AArch64 executable (ELF ET_EXEC; Android API 29 metadata), packaged as `jniLibs/arm64-v8a/libpikafish.so`. Legacy extraction makes it available in the executable native library directory. The matching NNUE is verified by exact size and SHA-256, copied atomically, and repaired if corrupt. Native availability requires API 29+. Engine settings default to all available cores, adaptive 64/128/256 MiB hash, one principal variation, depth 128 and untimed search. No strength handicap is applied; the official engine exposes no skill/Elo limit options.

`UciPosition` treats missing/blank starting FEN as the normal starting board, validates the complete move history, and emits `position startpos moves ...` or `position fen ... moves ...`. Retaining history lets Pikafish apply its own repetition rules. Those engine rules should not be presented as complete WXF match arbitership.

UCI initialization and timed searches use bounded waits; untimed searches poll interruptibly without an arbitrary deadline. A daemon reader drains output into a bounded queue. A `finally` block stops and destroys the child. Exact single-PV scores with legal continuations produce immutable progress snapshots, converted to Red's perspective; these update text and arrows without waiting for bestmove. Bound-only output is ignored. Mate zero correctly indicates the side to move has already lost. Native failures are visible and never relabel a fallback result as Pikafish.

The ARM64 APK requires a compatible device. `-PwithoutNative` omits the native library for an x86 emulator, where the built-in engine exercises the same UI, imports, and Java rules. It does not validate ARM64 native execution.

## Privacy, licensing, and verification limits

The Android manifest requests no `INTERNET` permission and disables automatic app backup. Imported games, preferences, and copied engine data remain in private app storage. Android's document picker handles deliberate import/export; source links open an external browser. Uninstalling removes private data, so exports are the user's portable backup.

Pikafish's GPL-3.0 code and separately licensed NNUE weights retain their upstream notices and exact release source pointers under `third_party/pikafish`. WXF source material retains its own rights; app source licensing does not relicense game pages or engine assets.

See [AUDIT.md](AUDIT.md) for current checks. Production-adapter tests execute the actual Java adapter with test-only Android stubs and real Windows Pikafish, including the formerly broken blank-FEN input, corruption recovery, live updates, deadlines and cancellation. The full pack is sampled against native Pikafish legal moves/search. Android emulator tests exercise the Java engine and UI. These do not execute ARM64 machine code; the current Windows emulator rejects ARM64 guests. Physical-phone engine execution remains separately identified.
