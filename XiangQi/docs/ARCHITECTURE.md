# Architecture

Xiangqi Study is a Java Android application targeting API 35, with minimum API 26. It uses Android framework widgets and a custom Canvas board; no web view or remote service is required. The Gradle wrapper is pinned to 8.11.1 and the Android Gradle Plugin to 8.9.3.

## UI and study state

`MainActivity` builds the searchable library and replay screen. `BoardView` draws the nine-file, ten-rank board, pieces, selection, and legal targets. Replay positions are reconstructed from a game's starting FEN and move sequence. Exploring a variation works on a separate sequence so the recorded game and its source result remain intact. Exporting a variation gives it the unfinished result `*`.

Shared preferences retain the last game, replay offset, orientation, and variation sequence. File I/O and analysis run on separate single-thread executors. Analysis has a generation identifier, so a late result cannot overwrite a newer board position. Leaving the app or changing the study position cancels the active search and autoplay.

## Records and import boundary

`GameRepository` combines the read-only bundled `games.json` with private `imported-games.json`. Imported records are validated in full before an `AtomicFile` write; failure leaves the existing library intact. Fingerprints use starting FEN and moves to skip duplicate records. Import limits cap individual files and saved import data at 5 MB, with additional game/move-count bounds.

`Pgn` imports tagged ICCS-coordinate PGN main lines, retaining player/event metadata, starting FEN, result, source, and time control. Comments, numeric annotations, and balanced side variations are ignored. Unsupported notation and illegal moves produce errors identifying the game and half-move. The complete batch is rejected on an error. JSON imports use the same position/move validation. XQF, CBR, CBL, WXF move notation, and Chinese move notation are not supported yet.

The starter asset contains 285 adult 2025 World Championship records and 23,340 half-moves, with official classical time control `3600+30`. Requested players and the men's final are prioritized. These are archived championship games, not a list of the world's top 285 ranked games. The downloader, source hashes, exclusions, and dated name-screening policy are documented in [SOURCES.md](SOURCES.md). Source-name matches are conservative filters, rather than proof that two people sharing a name are the same person.

## Position representation and rules

The Android-free `core` package uses a 90-cell character array, row-major from Black's top-left (`a9`, index 0) to Red's bottom-right (`i0`, index 89). Uppercase pieces are Red, lowercase pieces Black, and `\0` is empty. Piece letters are `RNBAKCP`, with horse/elephant FEN aliases accepted on input. ICCS move strings are four characters, such as `b2e2`.

`Position.play` returns a new position after checking piece movement, blockers, river/palace restrictions, king safety, and facing generals. No legal moves is a loss in Xiangqi, including stalemate. The replay board's legal-move layer does not adjudicate repetition, perpetual check/chase, clocks, resignations, or tournament conduct. A source result is retained as metadata and is not inferred from the final board.

The core harness checks all piece types, check and flying generals, PGN rejection behavior, score perspective, cancellation, and independent initial perft counts `44 / 1920 / 79666`. The full starter library was independently replayed through this Java rules implementation.

## Analysis engines

`Engine` is the built-in, bounded alpha-beta search with material/position evaluation. Iterative deepening retains the latest completed result and returns a legal fallback if interrupted before completing a depth. It is a lightweight study engine, with no grandmaster-strength claim and no tournament repetition adjudication.

`PikafishEngine` uses the official Pikafish 2026-09-06 Android ARM64 executable. Gradle packages the PIE binary as `jniLibs/arm64-v8a/libpikafish.so`; legacy native-library extraction makes it available in the app's native library directory. The NNUE asset is copied into a versioned private directory through a temporary file and rename. The native process receives its absolute network path, one search thread, a 32 MB hash, and one principal variation.

`UciPosition` checks that the supplied starting FEN and complete move history reach the requested board and turn, then emits `position startpos moves ...` or `position fen ... moves ...`. Retaining history lets Pikafish apply its own repetition rules. Those engine rules should not be presented as complete WXF match arbitership.

UCI initialization and search use bounded waits. A daemon reader drains child output into a bounded queue, and interruption cancels waiting without a blocking pipe read. A `finally` block stops and destroys the native process. `UciInfo` accepts completed single-PV scores, converts side-to-move scores to Red's perspective, and validates PV moves before displaying them. Native failures are reported visibly; they do not silently relabel a fallback result as Pikafish.

The ARM64 APK requires a compatible device. `-PwithoutNative` omits the native library for an x86 emulator, where the built-in engine exercises the same UI, imports, and Java rules. It does not validate ARM64 native execution.

## Privacy, licensing, and verification limits

The Android manifest requests no `INTERNET` permission and disables automatic app backup. Imported games, preferences, and copied engine data remain in private app storage. Android's document picker handles deliberate import/export; source links open an external browser. Uninstalling removes private data, so exports are the user's portable backup.

Pikafish's GPL-3.0 code and separately licensed NNUE weights retain their upstream notices and exact release source pointers under `third_party/pikafish`. WXF source material retains its own rights; app source licensing does not relicense game pages or engine assets.

Development verification includes 146 core assertions, legal replay of all 285 games/23,340 half-moves, and live Windows Pikafish UCI smoke tests using the production Java parser. The Windows test covers both sides to move, history commands, score signs, legal best moves, and PV replay. It is protocol evidence rather than Android runtime evidence. Installation and native engine operation on the intended Android 12 ARM64 phone remain unverified until a phone run is recorded.
