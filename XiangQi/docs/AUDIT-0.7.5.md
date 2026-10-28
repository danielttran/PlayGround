# Xiangqi Study 0.7.5 audit

Date: 2026-10-28. Version code 14. Baseline: `3490c3c` (0.7.4).

## Reproduced issues and fixes

Three focused checks against the previous portable APK reproduce three failures
in **19.196 seconds**, with no test errors:

1. Saving a rebuilt library while its search worker is blocked changes a saved
   row/pixel offset to the header's position zero. A new library view now keeps
   scroll restoration pending until the guarded selection has restored its rows.
2. Opening the legal-move chooser leaves autoplay running, so its list can refer
   to a position that has already advanced. Opening it now pauses replay; the
   callback requires the original Game, ply and Position identity. Its dialog is
   tracked and dismissed when the Activity is destroyed.
3. Opening the engine-side chooser also leaves autoplay running behind its
   "current position" prompt. Opening it now pauses replay, and both side choices
   require the same original position before starting an engine game.

The regressions verify actual saved offsets and eventual scroll restoration,
paused positions after a replay interval, normal legal-move selection, rejection
of stale move/side choices and legal-dialog cleanup on destruction. Separate
read-only reviews found no further concrete issue in the changed search and
modal paths, analysis/opponent generations or import completion/draft recovery.

## Collection audit

The five assets are unchanged from 0.7.4: **428 men's games / 36,227 half-moves**.
Every pack's count/hash, unique IDs/full mainlines, source-division preference and
existing player exclusion policy were checked again. All **392 retained earlier
records** preserve every prior field and move; all **132 removals** correspond to
women's source rounds. The 36-game Vietnamese generator reproduces exactly with
`--check`, including its three explicit holdouts.

An independent re-read of the acquired primary sources matches all 18 admitted
2009 games against the official cross-table: player identities, opponent ranks,
colors and results, plus dates against the Swiss calendar. The three official
2016 bracket images agree with the other 18 games' results and stages. The
regulations confirm **80+30** and **60+30** respectively. Individual 2016 dates
come from the federation's structured game records; the bracket does not
independently date each round. No dates were invented or changed.

The corpus, core rules and native adapter are unchanged; the full legal replay
and independent Windows Pikafish corpus results in the [0.7.4 audit](AUDIT-0.7.4.md)
remain applicable. This round focuses device checks on the changed state and
dialog paths. The old audit's fixture-count typo is corrected from 5,001 to
**5,000 games**, matching its actual test source.

## Verification scope

The portable build passes lint with **No issues found**, and its collection
payload/signature verify. **21 Android checks pass in 175.33 seconds**: four
search/concurrency cases, four variation/modal cases, six game-control/opponent
cases, three progression-slider cases, two library/background analysis cases and
two engine-failure recovery cases. This includes all three regressions that fail
against the previous APK. The emulator crash buffer is empty after the run.

Android interaction checks use **emulator-5554** only,
an Android 12 x86 environment running the built-in engine. Native ARM64 bytes
and matching NNUE are verified in the phone APK; Windows subprocess checks do
not substitute for executing that Android ABI. No test or automatic installation
targets the personal phone.

## Delivery

The clean phone build passes in **17 seconds**, with lint reporting **No issues
found**. The packaged manifest confirms **0.7.5 / version code 14**. The v2
signature, exact engine/network/license bytes and all bundled pack hashes pass.
Both the pre-build and final verification gates confirm latest official stable
**Pikafish 2026-09-06**, with matching NNUE SHA-256
`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.

The new APK atomically replaces `dist/XiangqiStudy-latest.apk`; only the latest
public APK remains. Size: **52,032,308 bytes**. SHA-256:
`b12f4c5711e49636943ec4c09363cf824a9169edfc934198ee9c828bbfbe8fdf`.
