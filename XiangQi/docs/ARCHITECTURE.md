# Architecture

Xiangqi Study 0.7.0 (version code 9) is a Java Android application targeting API 35, with minimum API 26. It uses Android framework widgets and a custom Canvas board; no web view or remote service is required. The Gradle wrapper is pinned to 8.11.1 and the Android Gradle Plugin to 8.9.3.

## UI and study state

`MainActivity` builds the searchable library and replay screen. Both screens have separate labelled Red/Black player rows. `PlayerNames` provides sourced romanized display/search aliases for every bundled Chinese source-name variant; original player metadata and exports remain unchanged. Search folds case/diacritics and includes Chinese names, aliases, event, round and collection.

`GameDateSort` implements newest, oldest and original collection order. Only complete valid calendar dates participate in date ordering. Partial, invalid and unknown dates stay at the bottom in either direction; stable sorting preserves collection order for equal or incomplete dates. Human and Engine tabs select bundled games of that type; My imports selects one named user collection. Search, sort and scroll are persisted per collection ID. Returning to a current game restores its own collection and navigation order.

Replay has exactly five navigation buttons, ordered Previous game, Next game,
Play, Previous move, Next move. Game navigation follows the current library search
and sort and restores each game's saved ply/variation; if the current game is
outside that search it uses the complete sorted selected collection. Play toggles autoplay.
Replay speed accepts 0.5–60 seconds between moves and defaults to one second.
It is stored independently of engine think time; changing it removes and
reschedules a pending tick. Pause, backgrounding, game changes and the final
move cancel playback. The top-right Game menu holds
Flip board, Start/Stop analysis, Go to move…, Play against engine, Export game and Settings.
Recorded replay has no standalone play-entry button below those controls. The
contextual action is hidden during ordinary replay, becomes Stop playing during
an engine game, and becomes Return to recorded game during a variation. Analysis
suggestions and a stopped engine game remain entry points for free variation
editing through the board and legal-move list.

The library uses a recycling ListView containing every matching game, without
page controls or a 40-game cutoff. Search and sorting use the entire selected collection;
only visible cards need native view inflation. Move records show at most 40
full-move rows around the current position. The complete source and variation
histories remain available through the move controls, numeric Go to move…
dialog and export. The horizontal position slider is removed. The dialog accepts
the current history's complete half-move range, including zero and its final ply.

`BoardView` draws the nine-file, ten-rank board, Chinese-text pieces, selection and legal targets. A small board-edge dot shows the side to move without a wrapping turn label. Played moves use a separate arrow. `AnalysisVisuals` validates the first two moves of each ranked continuation before drawing the candidate and its reply, with the appropriate side color and rank labels. One to five continuations can therefore show up to two arrows per line; a missing reply is not invented. New search snapshots replace the displayed continuations. Replay positions are reconstructed from a game's starting FEN and move sequence. Exploring a variation works on a separate sequence so the recorded game and its source result remain intact. Exporting a variation gives it the unfinished result `*`.

`EvaluationBarView` displays a slim vertical rail beside the board. Positive
scores favor Red and negative scores favor Black; flipping changes the rail's
orientation without changing that perspective. Analysis off makes the rail
GONE, releasing its width and margin so the board fills the row, and discards its score. Enabled analysis awaiting a result has a neutral
split. Navigation clears the old position's score before searching the new one.

Discs use a radius of 0.420 cell. Chinese glyph outlines are fitted inside each
finish's inner rim; measurements are cached by glyph and finish at the current
cell size. Glyphs and rank badges remain opaque. Analysis shafts are 0.13 cell
and composite in one shared 50% layer; the played arrow is 0.095 cell at 50%.

`AppearancePresets` is an Android-free catalog shared by settings and the renderer:
24 boards and 22 Chinese-character disc finishes. The existing 20 board and 18
disc IDs remain in their original order, including the original six selections
and surface colors. Warm maple, Neon violet, Antique gold and Carved honey append
four matching board/disc pairs from the supplied visual references. Grain,
patina, decorative branches, glow, bevels, frames and shadows are original Canvas
drawings rather than edited source screenshots. The river draws only 楚河 / 漢界,
with no URL text. [Appearance sources](APPEARANCE-SOURCES.md) record those supplied
references, earlier Xiangqi.com/Android examples and contrast-adjusted adaptations.

`StudySettings` persists preset IDs, Traditional/Simplified characters,
coordinates, result visibility and independent opaque RGB overrides. The latest
user choice wins per affected surface: selecting a board preset removes only the
custom board color; selecting a disc preset removes both piece background
overrides; a custom picker changes only its selected surface. Explicit selection
of the same preset also restores its colors. The settings selector distinguishes
that choice from initialization callbacks, so opening settings or changing
unrelated controls does not clear custom colors. The live preview uses the same
renderer as replay. Glyph contrast adapts to the selected fill; custom board RGB
suppresses material tint to keep its exact color. ICCS/numeric coordinates share
the existing rim and retain grid, disc and touch geometry. Results remain hidden
by default, including accessibility descriptions, while engine evaluations and
exported source results are retained.

Shared preferences also retain the last game, replay offset, board orientation,
variation and the user's analysis toggle. Guarded horizontal swipes switch
between the library and current game without losing study state; an explicit
return button provides the same route. Swipe detection excludes sliders, text
fields, spinners and multitouch. A shared serial file-I/O executor outlives
Activity recreation; each Activity owns its analysis executor.

The persisted `analysisEnabled` toggle is independent of an individual search's
Future. Finite completion retains that toggle. Position changes cancel obsolete
work, clear its estimate/arrow and start analysis for the new board when enabled.
Replay navigation, autoplay, suggested moves, variations and game changes use
the same restart path. Library view, backgrounding and opponent play pause
search without disabling the toggle; returning to foreground study resumes it.
Activity recreation reloads the choice. Generation identifiers reject obsolete
progress and final results. Only explicit Stop analysis disables the toggle;
it clears continuation arrows and deactivates the evaluation rail, while retaining
the text estimate explicitly marked as analysis off until a position change. Engine-setting
changes restart enabled analysis even if its previous finite search completed.
Backgrounding cancels autoplay.

Play against engine begins at the current position with the chosen human color.
It trims only the working variation at the selected ply and sends its complete
starting FEN/history to the same native adapter used for analysis. A legal engine
reply is committed only if its generation, game state and root ply still match.
Human moves are disabled during the computer's turn. Opponent searches use the
configured finite time budget, or 3 seconds when analysis is untimed; native
depth/thread/hash settings otherwise remain unchanged. Stop playing preserves
the variation and resumes free editing. Position/game navigation, starting
analysis, backgrounding and destruction cancel the pending opponent search and
invalidate late replies. The original record and source result are unchanged.

## Records and import boundary

`GameRepository` loads the read-only packs listed by `assets/game-packs.json`, then adds private `imported-games.json`. Pack filenames and unique game IDs are validated; a pack label supplies collection metadata when the game omits it. `scripts/game_packs.py` shares the pack manifest with host audit tools. Imported records are validated in full before an `AtomicFile` write; failure leaves the existing library intact. A process-wide storage lock and reload-before-commit protect imports made by different Activity instances. Atomic-file reads recover interrupted writes. Fingerprints use starting FEN and canonical moves. Files and saved imports are capped at 5 MB, with metadata, game/move-count and parsing-depth bounds.

Schema 2 stores import collection descriptors (stable ID, user name, Human/Engine
classification) and games referencing their collection ID. Each explicit import
gets a new collection and game IDs scoped to that collection; fingerprints remove
duplicates only inside its own batch. Identical games can appear in multiple
imports and alongside bundled copies. Source Collection tags remain independent
of user names. Legacy flat arrays load into Imported games, preserving original
game IDs and saved study positions; the next successful import atomically writes
the upgraded schema. Imported content cannot supply a trusted collection ID.
Completed import routing survives Activity recreation through a durable marker.

No built-in engine pack is shipped until a qualifying 2026 top-engine tournament
and its complete game files are verified. Ranking tests, older championships and
newly generated self-play are not silently substituted. See [ENGINE-SOURCES.md](ENGINE-SOURCES.md).

`Pgn` imports tagged ICCS-coordinate PGN main lines, retaining player/event metadata, starting FEN, result, source, time control and the optional `Collection` tag. Export preserves the collection and original source player names. Comments, numeric annotations, and balanced side variations are ignored. Unsupported notation and illegal moves produce errors identifying the game and half-move. The complete batch is rejected on an error. JSON imports use the same position/move validation. XQF, CBR, CBL, WXF move notation, and Chinese move notation are not supported yet.

The three packs contain **398 games and 33,629 half-moves**: 285 adult 2025 World Championship records (23,340 plies), 52 selected 1991–2003 World Championship master games (4,837 plies), and 61 Vietnamese international classical games from 2022–2023 (5,452 plies). Recent games use `3600+30`; historical records explicitly mark slow/classical play and preserve exact clocks only when supplied. Partial dates are retained without fabricated components. Three new source-date errors are corrected against official schedules with originals and evidence retained. Requested players and the 2025 men's final are prioritized within collection order. Downloaders, source hashes, exclusions and participant screening are documented in [SOURCES.md](SOURCES.md), [HISTORICAL-SOURCES.md](HISTORICAL-SOURCES.md) and [VIETNAMESE-SOURCES.md](VIETNAMESE-SOURCES.md).

## Position representation and rules

The Android-free `core` package uses a 90-cell character array, row-major from Black's top-left (`a9`, index 0) to Red's bottom-right (`i0`, index 89). Uppercase pieces are Red, lowercase pieces Black, and `\0` is empty. Piece letters are `RNBAKCP`, with horse/elephant FEN aliases accepted on input. ICCS move strings are four characters, such as `b2e2`.

`Position.play` returns a new position after checking piece movement, blockers, river/palace restrictions, king safety, and facing generals. No legal moves is a loss in Xiangqi, including stalemate. The replay board's legal-move layer does not adjudicate repetition, perpetual check/chase, clocks, resignations, or tournament conduct. A source result is retained as metadata and is not inferred from the final board.

The core harness checks all piece types, check and flying generals, PGN rejection behavior, collection round trips, date/name behavior, score perspective, cancellation and independent initial perft counts `44 / 1920 / 79666`. Every bundled move is replayed through this Java rules implementation. Native corpus checks independently compare legal moves and execute searches at sampled positions; their exact coverage/results belong to the versioned audit.

## Analysis engines

`Engine` is the built-in, bounded alpha-beta search with material/position evaluation. Iterative deepening retains the latest completed result and returns a legal fallback if interrupted before completing a depth. Multiple requested lines search distinct root moves and retain their ranked legal continuations. `Engine.Result` carries immutable ranked `Engine.Line` snapshots as well as the primary score and PV. It is a lightweight study engine, with no grandmaster-strength claim and no tournament repetition adjudication.

`PikafishEngine` uses the official Pikafish 2026-09-06 static AArch64 executable (ELF ET_EXEC; Android API 29 metadata), packaged as `jniLibs/arm64-v8a/libpikafish.so`. Legacy extraction makes it available in the executable native library directory. The matching NNUE is verified by exact size and SHA-256, copied atomically, and repaired if corrupt. Native availability requires API 29+. Engine settings default to all available cores, adaptive 64/128/256 MiB hash, one principal variation, depth 128 and untimed search. The persisted Analysis lines selector supports 1–5 MultiPV lines and is passed to the native engine. No strength handicap is applied; the official engine exposes no skill/Elo limit options.

Every native Gradle `preBuild` runs `scripts/sync-pikafish.py`. It queries the latest official stable release, verifies the published archive digest, extracts the matching ARM64 executable, NNUE and license files together, and updates/verifies metadata, hashes and notices. An unavailable check or changed unsupported release layout fails the build. `verify-apk.py` repeats the latest-release check and compares packaged bytes against the verified source assets. The build-time network requirement does not grant the installed app network access.

`UciPosition` treats missing/blank starting FEN as the normal starting board, validates the complete move history, and emits `position startpos moves ...` or `position fen ... moves ...`. Retaining history lets Pikafish apply its own repetition rules. Those engine rules should not be presented as complete WXF match arbitership.

UCI initialization and timed searches use bounded waits; untimed searches poll interruptibly without an arbitrary deadline. A daemon reader drains output into a bounded queue. A `finally` block stops and destroys the child. Exact MultiPV scores with legal continuations produce immutable ranked progress snapshots, each converted to Red's perspective. The primary line drives the evaluation; the ranked lines update the board arrows without waiting for bestmove. Parser state belongs to one root/search, and a newer depth replaces obsolete alternatives. Exact scores with an explicitly empty PV are also accepted when the engine has adjudicated a drawn history; the final best move is still checked for legality. Bound-only output is ignored. Mate zero correctly indicates the side to move has already lost. Native failures are visible and never relabel a fallback result as Pikafish.

The ARM64 APK requires a compatible device. `-PwithoutNative` omits the native executable and NNUE for an internal x86 emulator audit, where the built-in engine exercises the same UI, imports and Java rules. It does not validate ARM64 execution. `scripts/build.ps1 -Portable -Check` keeps this APK under `test-results/apk-audit`; it never replaces the phone delivery.

## APK delivery

`scripts/build.ps1 -Check` cleans and builds, verifies the APK payload and signature, then stages a hash-checked replacement at the exact project `dist` directory. The final destination is always `dist/XiangqiStudy-latest.apk`. Atomic replacement preserves the prior delivery until the verified replacement is ready; successful replacement then removes older Xiangqi Study APKs from that directory. Filesystem checks reject linked delivery directories/destinations and cleanup outside that directory. Internal portable APKs remain under `test-results/apk-audit`.

## Privacy, licensing, and verification limits

The Android manifest requests no `INTERNET` permission and disables automatic app backup. Imported games, preferences, and copied engine data remain in private app storage. Android's document picker handles deliberate import/export; source links open an external browser. Uninstalling removes private data, so exports are the user's portable backup.

Pikafish's GPL-3.0 code and separately licensed NNUE weights retain their upstream notices and exact release source pointers under `third_party/pikafish`. WXF source material retains its own rights; app source licensing does not relicense game pages or engine assets.

See [AUDIT-0.6.md](AUDIT-0.6.md) for current checks and device-specific results. Production-adapter tests execute the actual Java adapter with test-only Android stubs and real Windows Pikafish, including blank-FEN input, corruption recovery, live updates, drawn histories, deadlines and cancellation. Combined-corpus native tests cover all three bundled packs. Long histories reaching native rule-60 count 120 are preserved for real search; native `perft` reparses its generated FEN and rejects that counter, so the audit normalizes a separate enumeration copy before restoring full history for search. Emulator UI tests use the Java engine; physical Android ARM64 checks are recorded separately and must not be inferred from host/emulator results. [AUDIT-0.5.2.md](AUDIT-0.5.2.md), [AUDIT-0.5.1.md](AUDIT-0.5.1.md), [AUDIT-0.5.md](AUDIT-0.5.md), [AUDIT-0.4.md](AUDIT-0.4.md), [AUDIT-0.3.md](AUDIT-0.3.md), [AUDIT.md](AUDIT.md) and [VALIDATION.md](VALIDATION.md) retain earlier-version evidence.
