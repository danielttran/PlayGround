# Xiangqi Study 0.5.2 — persistent analysis and continuous library

Date: 2026-10-28. Delivery: `dist/XiangqiStudy-latest.apk`.

## Analysis and replay

Analysis now has a persisted user toggle, independent of the current search
task. Moving through recorded moves, playing a suggested variation, returning
to the record or opening another game starts analysis for that position while
the toggle is on. A time/depth limit finishing does not turn the toggle off.
The menu retains its checked **Stop analysis** action until explicitly stopped.

Entering the library or backgrounding the app cancels computation while keeping
the toggle on. Returning to replay resumes analysis. Recreation restores the
same on/off choice. Opponent play temporarily pauses study analysis; stopping
opponent play resumes it. Each search gets a new generation, and cancellation
invalidates pending progress and completion callbacks.

The existing evaluation convention was already correct: positive favors Red,
negative favors Black, regardless of turn or board orientation. Native UCI
scores are converted from side-to-move perspective exactly once. The UI now
states **+ favors Red · − favors Black** beside the score. These are engine
evaluation units, not a literal count of pawns or a direct comparison with
another chess engine's numbers.

The duplicate **Play from here** entry is removed. Engine play starts from the
game menu; Stop playing and Return to recorded game remain contextual actions.
Same-position replay redraws retain the current best-move arrow.

## Library

The complete filtered/sorted library is available in one continuous native
ListView. Recycled rows are bound as they enter view; there are no 40-game pages
or previous/next-page buttons. Search and sort operate on every record. The
library remembers its scroll position across replay return and recreation;
changing the query or sort returns to the top. Recycled hidden-result rows
clear their score text as well as hiding it and omitting it from accessibility.

## Verification

The Android tests cover persistent analysis at finite limits, both turns,
navigation, queued cancellation, stale callbacks, suggestions and source
restoration, library pauses, recreation, background/resume, opponent mode and
autoplay. Evaluation tests pass raw UCI scores through the production parser
and the actual displayed score, including bounds, forced wins and board flips.
The continuous-library regression reaches the last of 3,000 synthetic games
while checking that only visible rows are materialized.

The first complete Android 12 UI audit reports 55 passing tests in 299.268
seconds against the 337-game snapshot: 53 exercise UI/storage/rendering, while
two native-only cases explicitly log NOT_RUN on x86. This includes all seven
new persistent-analysis tests, three evaluation-perspective tests and the
continuous-library regression. Portable build, lint, payload and signature
checks pass.

## Expanded Vietnamese collection

The final preload contains **398 games / 33,629 half-moves**, including 61 new
games (27 WXF 2022, 30 WXF 2023, four Phuong Trang 2023). Lai Ly Huynh now has
23 games, Nguyen Thanh Bao 17, and Trenh A Sang seven. All new records have
verified 60+30 classical clocks and day-precision dates. Independent metadata
review checked every new game's date, round, colors and result against official
programs and sources. Three wrong source dates were corrected with retained
originals; one blank metadata record was recovered from its explicit official
title. A mislabeled rapid final was excluded. Unverified 2017/2019 clocks remain
outside the preload. Trenh's four new games agree move-for-move between the
official database and visually inspected bulletin pages.

All 398 games replay legally. Core checks pass: 170 rules/parser/engine
assertions, 27 date-sort, 244 appearance, and 452 name assertions. Every one of
the 141 Chinese source-name variants has sourced Romanized display/search names.
Deterministic offline reproduction produces the audited asset hash.
Official Windows Pikafish independently agrees with Java's legal-move sets at
1,623 positions across the complete corpus, including 1,597 searches and 26
terminal positions (29.27 seconds). This check preserves full history for
searches and does not represent ARM64 execution.

The [collection source audit](VIETNAMESE-SOURCES.md) records exact provenance,
date corrections, aliases, screening, holdouts and reproduction commands.

## Build delivery

Version code 7, version name 0.5.2. The final native APK is **51,982,161 bytes**,
SHA-256 `fe8305d06f5f5f51afadfd45bbde9c4af9451e1346732793027c508d53cc6781`.
Clean native/portable builds, lint (no issues), source-manifest/payload integrity
checks and APK v2 signature verification pass. The native build and final
verifier independently checked the current official stable release online:
Pikafish 2026-09-06 with matching NNUE SHA-256
`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.
The single `dist/XiangqiStudy-latest.apk` atomically replaces the previous
delivery; no older delivery APKs remain in that directory.

The final 56-case emulator attempt completed 55 cases, then ended at a test-only
menu timing assertion in the last smoke case. It is not recorded as a complete
passing run. On the Android 12 phone, both native cases passed, including 18
recorded/custom/check/variation positions and untimed cancellation. The extended
phone UI attempt encountered a popup selection failure and then an assertion
on the main thread, which aborted instrumentation. The captured fatal exceptions
originated in test code. Interactive phone tests were stopped because they were
interfering with the user's active session; the phone was subsequently
disconnected. The screen-awake setting was restored to its original value.

The next version fixes the test harness by confirming popup transitions and
rethrowing UI-thread assertions on JUnit's thread so cleanup can complete.
Interactive testing continues in the emulator. This build is historical;
later deliveries replace its APK.
Earlier appearance evidence remains
in [0.5](AUDIT-0.5.md), and the first contextual-action regression run remains
in [0.5.1](AUDIT-0.5.1.md).
