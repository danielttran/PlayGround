# Xiangqi Study 0.7.1 — compact replay and import recovery audit

Date: 2026-10-28. Version code 10. Delivery: `dist/XiangqiStudy-latest.apk`.

## Confirmed defects and fixes

- Rotating during import setup discarded the entered collection name/type and
  any explicit shared-file URI. Rotating the paste editor discarded its text.
  Small dialog state now survives recreation; text is stored atomically in the
  private draft cache and restored after queued file operations finish.
- Pasted records disappeared when a move failed validation. The editor now
  retains the exact text and shows the error for correction. Editing and both
  buttons are disabled while checking, preventing duplicate submissions or
  losing edits made after the submitted snapshot. Successful imports dismiss
  the editor, and recreation does not reopen an already committed record.
- A legal PGN beginning with a brace comment was mistaken for JSON. Valid JSON
  roots are now recognized first; PGN leading comments are handled without
  falling back from JSON schema errors. Quoted closing braces inside JSON
  metadata remain JSON, and malformed/trailing JSON checks are retained.
- Android's numeric key listener stripped the comma from pasted `0,5`, saving
  five seconds. The replay field now offers a numeric keyboard while preserving
  the entered text for explicit decimal/digit normalization and range checking.

The draft cache uses strict UUID tokens and the existing 5 MB UTF-8 import
limit. A fresh token identifies each saved snapshot, so a failed write cannot
silently restore an older edit as the latest text. Cancellation deletes the
current draft; a failed snapshot produces an explicit restoration error.
Game text is never copied into Android's saved-state Binder parcel.

## Audit rounds

The two original dialog-loss cases failed on the prior production code in
22.966 seconds, before the fix. The follow-up round reproduced both the PGN
leading-comment error and the decimal-comma conversion, while the initial draft
recovery checks passed. The corrected focused gate passes **36 tests in
228.591 seconds**. Further final checks add quoted-brace JSON coverage and
validation-time editor locking.

Ten new import lifecycle cases cover ordinary/rapid repeated recreation,
explicit shared URI retention, a 600,000-character Unicode draft, compact
saved-state size, cancellation cleanup, cache token/byte boundaries, actual
cache-write failure, invalid-paste correction and a queued import completing
across recreation. Three navigation cases cover independent collection
query/sort/scroll state, replay cancellation/end-of-record and decimal input.

An initial failure-injection fixture used unsupported oversized text in the
actual editor and timed out during recreation. It was replaced with a small
draft and a real cache write-permission failure; the test restores the original
directory mode in `finally`. Another cleanup assertion raced Android's queued
dialog dismissal; it now waits for dismissal and drains both UI and file work.
These test failures are retained under ignored test-results. The emulator log
showed a normal instrumentation exit, without a captured AndroidRuntime crash.

The complete final Android 12 emulator run passes **89 tests in 533.323
seconds**, including the final quoted-JSON and validation-locking checks.

After that full run, the user requested thicker arrows at lower opacity and
the replay progression slider again. Analysis shafts are now 0.15 cell, played
shafts 0.11 cell, and both are 40% opaque. Text/rank badges stay opaque and the
single shared analysis layer remains. The focused graphics/perspective/
persistent-analysis/board gate passes **24 tests in 134.057 seconds**.

The restored slider is directly below the board. Its full current record or
variation range stays synchronized with buttons, autoplay and Go to move.
Keyboard steps are one half-move, and accessibility changes navigate directly.
Touch scrubbing updates the board while pausing engine work until release;
autoplay/opponent play are cancelled. Library/background/rebuild transitions
reset tracking, and detached slider callbacks cannot navigate another game.
The slider/navigation gate passes **46 tests in 407.492 seconds**, including
three slider-specific cases, rendering, persistent analysis and collection navigation.

The final replay layout removes the outer scrolling screen: a compact
Library/player-title/menu header precedes the board, and the complete move
record scrolls independently in a recycling ListView below the controls.
Ordinary Recorded game and After-move status rows are removed. Previous/Next
game use icons; accessibility labels retain the full actions. The analysis
panel is visible only while analysis is enabled outside engine play.
Verification of this final layout is deliberately focused, per the user's request:
**nine relevant cases pass**, covering three layout cases, three slider cases
and the finite-search, library/game-navigation and engine-play analysis cases.
The first nine-case run passed eight cases in 78.425 seconds; its new scroll
assertion read the position before Android's smooth-scroll animation started.
After waiting for actual scroll progress, only that case was repeated and passed
in 6.729 seconds. No production change was needed for that assertion.

The layout checks include a 2,000-half-move record with all 1,000 rows exposed
through a recycling adapter, independent record scrolling, five visible analysis
lines available through the panel's own scroll area, and portrait/narrow/landscape
measurements. Manual Android 12 swiping also keeps board bounds unchanged at
`[33,237]–[1047,1352]` while later record rows come into view. Game details preserve
full names and the hidden-result preference. Final lint cleanup only moves the
same displayed text into resources and removes the obsolete window/status strings.

## Rules, native engine and data

- Core: 170 rules/parser/engine assertions, 62 MultiPV, 27 date sorting,
  244 appearance, 100 analysis visuals and 452 player-name assertions pass.
- All 398 bundled human games / 33,629 half-moves replay legally.
- Java legal moves agree with official Pikafish at **259 positions**, including
  mate/stalemate and 256 deterministic reachable positions. Initial perft
  counts 44 / 1,920 / 79,666 agree independently.
- The production native adapter passes **58 assertions** with real Windows
  Pikafish, including MultiPV/history, network repair and abnormal output.
- A new real-engine stress test passes **222 assertions** across 20 continuous
  five-line search/cancel cycles, both turns/history positions, with no surviving
  task-owned child process or output reader, followed by four successful
  three-line restarts.

Host subprocess checks do not establish Android ARM64 runtime or low-memory
phone behavior. Interactive Android checks run only on emulator-5554; no tests
or automatic installation run on the personal phone. Engine game collection
work is deferred. No Yago records are bundled.

## Optional historical game download

The requested *100 Ingenious Xiangqi Games* was located as 象棋鬼手百局 by
Zhu Jianqiu. All 100 available records were inspected. The delivered optional
subset contains **68 full-record games / 5,552 half-moves**, all from the normal
initial board. Two full games were recovered by exact position/turn/suffix
comparison with independent public records. Partial positions, explicit
truncations, uncertain endings/results and the existing participant-filter
match remain held out: 32 chapters total. Source-reported completeness and
unresolved dates/clocks are documented without claiming a classical clock.

The [collection verifier](../data/collections/ingenious/verify.py) passes all
legal moves and JSON/PGN metadata agreement, with **zero exact duplicates,
whole excerpt/truncation matches or later shared-continuation candidates**
against the existing 398 bundled games, and no internal full/prefix duplicates.
These are optional import files, not additional app assets. The app's built-in
human library remains 398 games. See the [collection evidence and exclusions](../data/collections/ingenious/README.md).
The emulator's My imports picker confirms **Ingenious full games · Human · 68**
and its library displays **68 of 68 games**, independent of the bundled collection.

## Screenshots

Android 12 x86 emulator, 1080 × 2280. Its analysis screenshot uses the built-in
engine; it does not demonstrate Android ARM64 Pikafish execution.

- [Compact replay, analysis off](../screenshots/071-compact-replay.png)
- [Move record scrolled, board unchanged](../screenshots/071-record-scrolled.png)
- [Analysis enabled, fixed-height scrollable panel](../screenshots/071-compact-analysis.png)
- [Optional complete-game collection imported separately](../screenshots/071-ingenious-import.png)

## Build

The clean native build and signature/payload checks pass, with Android lint
reporting **no issues**. Upstream latest-release checks run before compilation
and during final APK verification. They confirm **Pikafish 2026-09-06** and its
matching NNUE SHA-256
`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.

APK: **52,007,901 bytes**, SHA-256
`c5431c8e894174c5d3ed3e7cd8dbb186488d166d36bf375c94d2af230f1e99c1`.
The previous public build was replaced atomically; only
`dist/XiangqiStudy-latest.apk` remains in the delivery folder.
