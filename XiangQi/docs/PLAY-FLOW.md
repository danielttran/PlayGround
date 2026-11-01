# Playing and reviewing games

Version 0.9.0 refines the native Play / Database / Options launcher. The replay viewer, bundled games, annotations, imported collections and appearance choices remain available.

## Play

New Game shows a live board preview. Red and Black orient it for the selected side; Random leaves the preview orientation alone until a side is assigned at game start. This survives activity recreation. Tap the compact Computer difficulty row, or Playing difficulty in Options, for a 1–100 slider and direct number entry. The level is copied into each new game; changing the default never changes a game already underway. Levels use increasing search depth and node/time budgets and a decreasing allowance for weaker candidate moves. Level 100 is named Elite. The scale has **not been calibrated against a human rating pool** and does not claim 2800 Elo.

Every move queues an ordered SQLite transaction. The database stores the initial FEN, complete move history, side, level, timestamps, result and hint usage. Process recreation loads the stored record. Multiple unfinished games remain independently resumable from My Games.

The playing screen uses the existing BoardView turn dot. It has no saved-status banner, turn text, live score, automatic advice, or move-quality labels. Undo removes the latest human move and any following reply; it cancels pending work, advances the record revision and invalidates analyses beyond the retained position. Surrender asks for confirmation and stores the result. Hint performs an unrestricted two-second search and shows one best-move arrow. A hint does not play the move; hint usage is recorded for later review.

## Database

My Games holds personal computer games. Human and Engine retain the shipped collections; Imports retains each separately named user batch. Search and sorting run off the UI thread. Played games can be deleted and restored through the Undo action. Source records are never edited by playing from a library position.

Only completed personal games can be materialized for review. The result screen links to My Games and New Game; it does not automatically reveal annotations.

Personal, human, engine and imported games share one review layout. Each half-move has a labelled Red/Black turn, the recorded move, and its saved Pikafish alternative when available. SQL analyses remain indexed by position; materialized review suggestions use the played move's one-based ply, matching library annotations. Missing advice displays a dash. Tap the advice to explore from the correct pre-move position, or long-press the recorded move for its full annotation. Background note refreshes preserve manual scrolling, including the result at the end of the record. Existing book markers remain available.

Options groups appearance, playback, spoiler and computer settings into compact rows. Board/piece/coordinate/Chinese-character choices still use arrows with a live preview, and custom colors retain their existing latest-change-wins behavior. Viewer settings return to the same game, position and analysis toggle without changing the launcher's remembered tab.

## Analysis

The opponent, Hint and hidden-review worker share one native search slot. Opponent replies and explicit hints preempt hidden review. Missing position analyses persist as a resumable queue, inferred from the saved game and cached analyses. Work pauses when the activity backgrounds; it resumes during human thinking time, in the database or when reviewing a completed game. A saved game can display Review preparing while work remains.

Analysis always uses the unrestricted Pikafish engine, maximum depth, available cores and automatically selected memory. Automatic review and hints use a two-second search budget per position. Live analysis is untimed. Difficulty applies only to the opponent. Cached scores and best continuations are converted into Best / Good / Inaccuracy / Mistake / Blunder labels; these are engine estimates, not guaranteed explanations of a move's strategic meaning.

## Rules and device coverage

Legal moves, checkmate and Xiangqi stalemate are enforced. Casual play ends repeated positions as a draw, except a detected one-sided perpetual check loses for the checking side. This is **not a complete WXF perpetual-chase or tournament-clock arbiter**. Games are untimed in this release.

The phone build includes the latest official stable ARM64 Pikafish and matching NNUE, verified by the existing build guard. The Android 12 x86 emulator uses the explicitly labelled built-in fallback; Windows adapter checks exercise real Pikafish and its NNUE but do not execute the Android ARM64 binary. User data stays on the device; export important games before uninstalling.
