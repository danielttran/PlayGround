# Xiangqi Study 0.8.0 audit

Audited on 2026-10-31. Version code 26. Base: `1be60db` (Gemini's tactical collection and annotation work).

## Delivered behavior

- Native Play / Database / Options launcher; My Games / Human / Engine / Imports collections.
- Red, Black or Random; a compact 1–100 playing level selector and direct number entry. Levels are not claimed human Elo ratings.
- SQLite personal games, ordered autosaves, resume after activity recreation, delete with Undo, independently preserved source records.
- Existing board turn dot; no saved-status or turn-text banner while playing. Undo cancels pending searches and removes the latest human move and reply. Surrender saves the result. Hint uses unrestricted analysis with a two-second budget and shows one arrow.
- Hidden review while playing, preempted for replies and hints. Pending review resumes after new moves are appended and in completed-game review. Unfinished games cannot be materialized with annotations.
- Maximum-strength analysis, automatically managed resources, configurable displayed lines only. One shared native search slot prevents overlapping native workloads.
- Existing source-provided romanized labels remain readable even when their accompanying Chinese spelling is unfamiliar; this does not claim a newly verified player identity or rewrite the source metadata.

## Checks

1. Final Android 12 x86 emulator run: **8 tests passed** in 70.555 seconds. Seven ComputerGameFlowTest checks cover persistence and the unfinished-review gate, deletion/restore, revision guards, cancellation of a pending reply during Undo, a legal timed hint without live scores, surrender without automatically opening review, 100 levels with fixed analysis strength, review after a cached game grows, rendered screens and recreation/resume. The eighth check verifies the shipped repository and an atomic import round trip. Earlier focused rounds also passed.
2. Production Java native adapter against real Windows Pikafish and the verified NNUE: **64 assertions passed**, including replies at levels 1/25/50/75/100, a maximum-depth two-second hint, both sides, MultiPV, network integrity/reuse/repair, finite timeout handling and repeated search cancellation without leaked child processes.
3. Core checks passed: 170 main assertions, 62 MultiPV assertions, 27 date-sorting assertions, 252 preset assertions, 100 analysis-visual assertions and 728 name-display/search assertions. Independently replayed **796 games / 76,445 legal half-moves**.
4. Final native clean build, Android-test APK assembly and lint passed. Lint: **0 errors / 24 warnings**, primarily text-localization warnings. Package, collection hashes, official engine/network hashes, licenses and APK v2 signature verified.
5. Actual screenshots inspected: [Play](../screenshots/play-0.8.0.png), [Database](../screenshots/database-0.8.0.png), [Options](../screenshots/options-0.8.0.png). The first screenshot attempt was rejected because the emulator's System UI had an ANR during cold boot. Its Wait action cleared the interruption; final captures assert the target app is the foreground window and wait for rendered frames. The final emulator crash buffer was empty.

All nine bundled pack files and their manifests are unchanged: **676 human games and 120 engine games**. Source dates, rounds, moves and Gemini's annotations are preserved.

## APK

- `dist/XiangqiStudy-latest.apk` — 52,391,624 bytes; only delivered APK in dist.
- SHA-256: `3761eebe6dd9cac7225c867498223ec52c8b2ed6fc0459a847ddc373816b80ed`.
- Launcher: `org.xiangqi.study.HomeActivity`; ARM64; Android minimum API 26, native Pikafish minimum API 29.
- Latest official stable Pikafish checked online: **2026-09-06**.
- Matching NNUE SHA-256: `7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.

## Coverage limits

The emulator executes the labelled built-in fallback, and Windows adapter tests do not execute Android ARM64 machine code. A physical phone was not connected for this run. The phone APK contains the exact verified official ARM64 executable. Playing levels need human-pool calibration before any numerical Elo claim. Untimed casual repetition handling is not a full WXF perpetual-chase arbiter. Automatic review may still be preparing after a fast game; it resumes while the app is foregrounded.

Reproduce with scripts/test-core.ps1, scripts/test-native-adapter.py, ComputerGameFlowTest plus the repository smoke method on the portable Android 12 build, and scripts/build.ps1 -Check for the native delivery. Raw execution reports are in the local ignored test-results directory.
