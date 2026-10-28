# Xiangqi Study 0.7.2 audit

Date: 2026-10-28. Version code 11.

## Round 1: source, rules and engine protocol

Independent source reviews covered replay sizing/navigation, analysis and opponent
lifecycle, and import/storage boundaries. Two concrete issues were reproduced
before changing production code:

- A failed computer reply wrote its explanation only into the conditional analysis
  panel. With analysis off it was invisible; with analysis on the restarted search
  replaced it. The original code fails the visible-error case in **9.808 seconds**.
  Failures now show a separate accessible dialog, release opponent state and keep
  the working variation. The analysis toggle is preserved and enabled study
  analysis resumes. Error dialogs are tracked and dismissed on Activity destruction.
- A pasted import could finish after Android saved background state, then reopen
  its obsolete editor if that older state was restored after completion. The
  temporary import-routing marker had already been consumed. A controlled
  older-state restoration fails on the original code in **9.779 seconds**.
  A stable paste-session UUID now survives all editor snapshots; successful
  completion persists that UUID before UI routing. Restoration ignores an already
  completed session even after its cached draft and temporary routing marker are gone.

Export restoration retains its pending record through the existing
`onRestoreInstanceState` callback. Replay/layout source review found no additional
concrete regression.

Fresh host checks pass:

- 170 base rules/parser/engine assertions and 62 MultiPV assertions.
- 27 date-sort, 244 appearance, 100 analysis-visual and 452 player-name assertions.
- All **398 bundled games / 33,629 half-moves** replay legally.
- **58** production native-adapter assertions with real Windows Pikafish.
- **221** stress assertions across 20 real five-line continuous-search/cancel
  cycles, both turns/history positions, no surviving task-owned process/output
  reader, and four successful three-line restarts.

## Round 2: recovery regressions

The two engine-failure cases exercise the real worker catch by capturing the
native route while queued and restoring availability before release. The adapter
then rejects availability; no fake native binary is executed and no NNUE is copied.
They cover analysis off/on, a visible explanation, preserved source/variation,
released pending reply and resumed study analysis.

The import gate covers all eleven lifecycle/storage cases, including the new
older-background-snapshot reproducer. It uses Android's post-save lifecycle
callback to restore the actual earlier draft Bundle; no device process is killed.
The combined recovery gate passes **13 tests in 125.574 seconds**.

## Round 3: replay and navigation

The follow-up gate exercises the compact layout, independent full-history list,
slider touch/keyboard/accessibility paths, game menu and opponent play, collection
navigation, variation restoration, analysis cancellation and long player names.
The existing flow fixture now opens Legal moves through the compact variation
entry's accessibility description rather than expecting the removed standalone row.
This gate passes **24 tests in 259.934 seconds**: three layout, three slider,
six game-control, five end-user-flow, three collection-navigation, three persistent
analysis and one long-name layout case. Together with recovery, **37 Android
checks pass**. The final build also compiles all instrumentation sources.

## Delivery and coverage

The phone APK is built with the official latest-release guard, matching network
and license payload verification, signature verification and Android lint. Only
`dist/XiangqiStudy-latest.apk` is published; the prior delivery is atomically
replaced and older public Xiangqi APKs are removed.

The final clean build passes in **24 seconds**, and lint reports **no issues**.
The packaged manifest confirms **0.7.2 / version code 11**, and its v2 signature
verifies. Upstream checks before compilation and during payload verification
confirm latest stable **Pikafish 2026-09-06**, with matching NNUE SHA-256
`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.

APK: **52,008,325 bytes**, SHA-256
`1691301fdcd7fc1174afb89c8a93f6c39fb39bd1f1eed7e7ee9285d089a10b4a`.

Android interaction checks use the **Android 12 x86 emulator, emulator-5554**, with
the built-in engine. Windows engine checks exercise the production subprocess
adapter, not Android ARM64 execution. No tests or automatic installation run on
the personal phone. The [0.7.1 screenshots](AUDIT-0.7.1.md#screenshots) show the
retained replay layout. The optional historical collection and the 398-game
preload are unchanged; engine-game collection work remains deferred.
