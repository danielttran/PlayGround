# Xiangqi Study 0.6.0 — evaluation rail and continuation arrows

Date: 2026-10-28. Version code 8. Delivery: `dist/XiangqiStudy-latest.apk`.

## Requested behavior

The horizontal replay-position slider is removed. A 16 dp evaluation rail and
4 dp gap sit beside the board, matching its height without changing its rim or
coordinate padding. Positive evaluations favor Red; negative values favor
Black. The rail is invisible while analysis is off, retaining its layout space so the board keeps the same size. It clears stale scores while
waiting for a new position, and follows board orientation. Its visual scale is
an advantage indicator, without a claimed winning probability.

Analysis settings offer 1–5 lines. Each completed principal variation supplies
its first legal move and legal reply, producing one arrow for each side when
both moves exist. The same count applies to each requested line. Side colors,
rank badges and small parallel lanes keep shared reply paths readable. Missing
or terminal replies are not fabricated. The played-move arrow remains separate.
Switching analysis off clears its arrows and deactivates the rail; any retained
text is marked as the last estimate. Position changes and off/pending states
clear stale overlays.

Discs are 3.7% larger, and Chinese glyphs are slightly smaller and fitted inside
each finish's innermost rim with a visible gap. Outline measurements are cached
in a bounded per-view cache. Analysis arrows are wider than the previous phone
build and composite at 50% opacity; the played arrow is also wider and 50% opaque.
Chinese text and numbered badges stay opaque. A single shared analysis-arrow
layer avoids allocating a full-board buffer separately for every line.

The Game menu's **Go to move…** dialog replaces slider navigation. Position 0
is the starting position; each half-move advances one position. It validates
the range, pauses autoplay while choosing, and preserves the analysis toggle.

Analysis continues following positions until explicitly switched off. Library
and background entry pause computation and retain the toggle; returning to
replay resumes. The continuous lazy-loaded library, all 398 games, 24 board
styles and 22 Chinese piece finishes are retained.

## Engine and rendering verification

Pikafish receives the requested `MultiPV` count. The Java fallback also produces
ranked alternatives with legal replies. Immutable per-line snapshots retain
Red-perspective scores and reject bounded, stale, duplicate or illegal secondary
updates. Existing first-line result fields remain compatible. Opponent play
uses one principal line and retains full-strength search settings.

Verified before Android runtime testing:

- 170 existing rules/parser/engine assertions and 62 new MultiPV assertions pass.
- Date sorting: 27; appearance: 244; analysis projection/scale: 100; names: 452
  assertions pass.
- All 398 games / 33,629 half-moves replay legally.
- The production Java native adapter passes 58 assertions with official Windows
  Pikafish, including three ranked lines for both turns, secondary live updates,
  legal replies, snapshot isolation and repeated five-line cancellation.
- Seven Canvas tests cover side colors, two/three lines, both turns and
  orientations, live replacement, invalid/stale clearing, rail states and
  exact board/rail alignment at 220, 280 and 393 dp widths, plus glyph clearance
  across all finishes and effective arrow opacity/stroke width.

The [Vietnamese collection audit](VIETNAMESE-SOURCES.md) records all 61 added
games, source-date corrections and cross-source metadata verification. The
unchanged data corpus also passed an independent official Pikafish comparison
at 1,623 positions, documented in [0.5.2](AUDIT-0.5.2.md).

## Test-induced phone interruption

The captured phone exceptions in the preceding audit originated from JUnit
assertions on Android's main thread. Interactive instrumentation was also
driving controls and recreating screens while the user was using the phone.
Those tests ended; further interactive testing is confined to the emulator.
The phone was disconnected before its test package could be removed. An
installed test package does not run those actions by itself.

The test harness now waits for stable popup bounds and a confirmed window
transition instead of treating accepted touch input as a completed menu action.
UI-thread assertions are rethrown on JUnit's thread so failures can be reported
and preference cleanup can complete. Smoke tests back up and restore their
preferences and use finite search limits. These changes are in the test APK.

The prior ARM64 native binary passed 18 recorded/custom/check/variation positions
and continuous cancellation on the Android 12 phone. The new MultiPV adapter
is verified with actual Windows Pikafish; Android ARM64 execution of this new
adapter is not inferred from that host check.

## Final results and delivery

All **64 emulator-capable Android tests pass in 401.551 seconds** on Android 12.
Native-only cases are excluded from this run; no no-op native cases are counted.
The focused graphics/settings/navigation gate also passes all nine tests in
72.064 seconds. The earlier audit exposed false failures in popup confirmation;
diagnostics showed the requested actions had completed. The helper now accepts
confirmed final transitions, treats root refresh as best-effort, and avoids
tapping through newly opened windows. The complete run above passes afterward.

Clean portable and native builds pass. Android lint reports **no issues**.
APK payload/source-manifest hashes, latest-release checks and the APK v2
signature verify. Production builds checked the official latest stable release
before compilation and again at final verification: **Pikafish 2026-09-06** with
matching NNUE SHA-256
`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.

After that full run, the final refinements hide the off-state rail without
changing board geometry and preserve real search typing. Both targeted tests
pass in 17.61 seconds, including character-by-character Trenh search, backspace,
clearing, all seven results, and exact board dimensions across the toggle.
The interactive library header remains available for keyboard navigation.

Final APK: **51,991,825 bytes**, SHA-256
`0466d0ffd66feb41442665669042a251ebce07a1ca3877ab3a4fee5c3c7537a6`.
The delivery path is `dist/XiangqiStudy-latest.apk`; older delivery APKs were
replaced and only that APK remains in `dist`. No interactive phone testing or
automatic phone installation was performed for this final build. The phone was
disconnected, so the secondary SD-card transfer could not be verified.
