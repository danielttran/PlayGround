# Architecture

Xiangqi Study 0.3.0 is a Java Android application targeting API 35, with minimum API 26. It uses Android framework widgets and a custom Canvas board; no web view or remote service is required. The Gradle wrapper is pinned to 8.11.1 and the Android Gradle Plugin to 8.9.3.

## UI and study state

`MainActivity` builds the searchable library and replay screen. Both screens have separate labelled Red/Black player rows. `PlayerNames` provides readable romanized display/search aliases for all 110 bundled player names, with source references; original player metadata and exports remain unchanged. Search folds case/diacritics and includes Chinese names, aliases, event, round and collection.

`GameDateSort` implements newest, oldest and original collection order. Only complete valid calendar dates participate in date ordering. Partial, invalid and unknown dates stay at the bottom in either direction; stable sorting preserves collection order for equal or incomplete dates. The selected sort order is persisted.

Library view inflation is bounded to 40 games per page; search and sorting still
use the entire library. Page controls retain their position on library return and
recreation; changing the query or order returns to the first page. Move records
show at most 40 full-move rows around the current position. The complete source
and variation histories remain available through the position slider and export.

`BoardView` draws the nine-file, ten-rank board, Chinese-text pieces, selection and legal targets. A small board-edge dot shows the side to move without a wrapping turn label. Played moves and best-move hints use distinct arrows. The best-move arrow changes with completed analysis updates. Replay positions are reconstructed from a game's starting FEN and move sequence. Exploring a variation works on a separate sequence so the recorded game and its source result remain intact. Exporting a variation gives it the unfinished result `*`.

`StudySettings` persists six board presets, six disc presets, Traditional/Simplified Chinese characters, coordinates and result visibility. Independent opaque RGB overrides control the board and each side's piece backgrounds; contrast-aware inks remain readable. The settings preview uses the real board renderer. ICCS/numeric coordinate labels share the existing rim: switching them on preserves grid size, piece size and touch intersections. Results are hidden by default, including accessibility descriptions; engine evaluations and exported source results are retained.

Shared preferences also retain the last game, replay offset, board orientation and variation. Guarded horizontal swipes switch between the library and current game without losing study state; an explicit return button provides the same route. Swipe detection excludes sliders, text fields, spinners and multitouch. A shared serial file-I/O executor outlives Activity recreation; each Activity owns its analysis executor. Analysis generation identifiers reject stale progress and final results. Leaving the app or changing the position cancels search and autoplay; explicit Stop retains the latest estimate and arrow.

## Records and import boundary

`GameRepository` loads the read-only packs listed by `assets/game-packs.json`, then adds private `imported-games.json`. Pack filenames and unique game IDs are validated; a pack label supplies collection metadata when the game omits it. `scripts/game_packs.py` shares the pack manifest with host audit tools. Imported records are validated in full before an `AtomicFile` write; failure leaves the existing library intact. A process-wide storage lock and reload-before-commit protect imports made by different Activity instances. Atomic-file reads recover interrupted writes. Fingerprints use starting FEN and canonical moves. Files and saved imports are capped at 5 MB, with metadata, game/move-count and parsing-depth bounds.

`Pgn` imports tagged ICCS-coordinate PGN main lines, retaining player/event metadata, starting FEN, result, source, time control and the optional `Collection` tag. Export preserves the collection and original source player names. Comments, numeric annotations, and balanced side variations are ignored. Unsupported notation and illegal moves produce errors identifying the game and half-move. The complete batch is rejected on an error. JSON imports use the same position/move validation. XQF, CBR, CBL, WXF move notation, and Chinese move notation are not supported yet.

The two packs contain **337 games and 28,177 half-moves**: 285 adult 2025 World Championship records (23,340 plies, classical `3600+30`) and 52 selected 1991–2003 World Championship master games (4,837 plies). Every historical record explicitly marks slow/classical play; exact clock values are preserved only when supplied. Partial dates are retained without fabricated components. Requested players and the 2025 men's final are prioritized within collection order. Downloaders, source hashes, exclusions and the dated participant-screening policy are documented in [SOURCES.md](SOURCES.md) and [HISTORICAL-SOURCES.md](HISTORICAL-SOURCES.md). Source-name matches are conservative filters, not a certification of an individual's history.

## Position representation and rules

The Android-free `core` package uses a 90-cell character array, row-major from Black's top-left (`a9`, index 0) to Red's bottom-right (`i0`, index 89). Uppercase pieces are Red, lowercase pieces Black, and `\0` is empty. Piece letters are `RNBAKCP`, with horse/elephant FEN aliases accepted on input. ICCS move strings are four characters, such as `b2e2`.

`Position.play` returns a new position after checking piece movement, blockers, river/palace restrictions, king safety, and facing generals. No legal moves is a loss in Xiangqi, including stalemate. The replay board's legal-move layer does not adjudicate repetition, perpetual check/chase, clocks, resignations, or tournament conduct. A source result is retained as metadata and is not inferred from the final board.

The core harness checks all piece types, check and flying generals, PGN rejection behavior, collection round trips, date/name behavior, score perspective, cancellation and independent initial perft counts `44 / 1920 / 79666`. Every bundled move is replayed through this Java rules implementation. Native corpus checks independently compare legal moves and execute searches at sampled positions; their exact coverage/results belong to the versioned audit.

## Analysis engines

`Engine` is the built-in, bounded alpha-beta search with material/position evaluation. Iterative deepening retains the latest completed result and returns a legal fallback if interrupted before completing a depth. It is a lightweight study engine, with no grandmaster-strength claim and no tournament repetition adjudication.

`PikafishEngine` uses the official Pikafish 2026-09-06 static AArch64 executable (ELF ET_EXEC; Android API 29 metadata), packaged as `jniLibs/arm64-v8a/libpikafish.so`. Legacy extraction makes it available in the executable native library directory. The matching NNUE is verified by exact size and SHA-256, copied atomically, and repaired if corrupt. Native availability requires API 29+. Engine settings default to all available cores, adaptive 64/128/256 MiB hash, one principal variation, depth 128 and untimed search. No strength handicap is applied; the official engine exposes no skill/Elo limit options.

Every native Gradle `preBuild` runs `scripts/sync-pikafish.py`. It queries the latest official stable release, verifies the published archive digest, extracts the matching ARM64 executable, NNUE and license files together, and updates/verifies metadata, hashes and notices. An unavailable check or changed unsupported release layout fails the build. `verify-apk.py` repeats the latest-release check and compares packaged bytes against the verified source assets. The build-time network requirement does not grant the installed app network access.

`UciPosition` treats missing/blank starting FEN as the normal starting board, validates the complete move history, and emits `position startpos moves ...` or `position fen ... moves ...`. Retaining history lets Pikafish apply its own repetition rules. Those engine rules should not be presented as complete WXF match arbitership.

UCI initialization and timed searches use bounded waits; untimed searches poll interruptibly without an arbitrary deadline. A daemon reader drains output into a bounded queue. A `finally` block stops and destroys the child. Exact single-PV scores with legal continuations produce immutable progress snapshots, converted to Red's perspective; these update text and arrows without waiting for bestmove. Exact scores with an explicitly empty PV are also accepted when the engine has adjudicated a drawn history; the final best move is still checked for legality. Bound-only output is ignored. Mate zero correctly indicates the side to move has already lost. Native failures are visible and never relabel a fallback result as Pikafish.

The ARM64 APK requires a compatible device. `-PwithoutNative` omits the native executable and NNUE for an internal x86 emulator audit, where the built-in engine exercises the same UI, imports and Java rules. It does not validate ARM64 execution. `scripts/build.ps1 -Portable -Check` keeps this APK under `test-results/apk-audit`; it never replaces the phone delivery.

## APK delivery

`scripts/build.ps1 -Check` cleans and builds, verifies the APK payload and signature, then stages a hash-checked replacement at the exact project `dist` directory. The final destination is always `dist/XiangqiStudy-latest.apk`. Atomic replacement preserves the prior delivery until the verified replacement is ready; successful replacement then removes older Xiangqi Study APKs from that directory. Filesystem checks reject linked delivery directories/destinations and cleanup outside that directory. Internal portable APKs remain under `test-results/apk-audit`.

## Privacy, licensing, and verification limits

The Android manifest requests no `INTERNET` permission and disables automatic app backup. Imported games, preferences, and copied engine data remain in private app storage. Android's document picker handles deliberate import/export; source links open an external browser. Uninstalling removes private data, so exports are the user's portable backup.

Pikafish's GPL-3.0 code and separately licensed NNUE weights retain their upstream notices and exact release source pointers under `third_party/pikafish`. WXF source material retains its own rights; app source licensing does not relicense game pages or engine assets.

See [AUDIT-0.3.md](AUDIT-0.3.md) for current checks and device-specific results. Production-adapter tests execute the actual Java adapter with test-only Android stubs and real Windows Pikafish, including blank-FEN input, corruption recovery, live updates, drawn histories, deadlines and cancellation. Combined-corpus native tests cover both bundled packs. Long histories reaching native rule-60 count 120 are preserved for real search; native `perft` reparses its generated FEN and rejects that counter, so the audit normalizes a separate enumeration copy before restoring full history for search. Emulator UI tests use the Java engine; physical Android ARM64 checks are recorded separately and must not be inferred from host/emulator results. [AUDIT.md](AUDIT.md) and [VALIDATION.md](VALIDATION.md) retain earlier-version evidence.
