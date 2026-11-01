# Xiangqi Study 0.9.0 audit

Version code 28. Audited 2026-11-01, based on 0.8.1 (`647e92a`).

## Changes

- Approved board-first Play / Database / Options redesign, spaced controls, native icon buttons, compact navigation, and an inline difficulty row opening a bottom sheet.
- Red/Black orient the new-game preview immediately; Random preserves its current orientation. Orientation survives recreation and does not alter another saved game's side or difficulty.
- Grouped Options and a dedicated appearance page retain existing presets, Chinese glyphs, coordinates, custom colors, animation, replay timing, spoiler preferences and per-side analysis lines. Analysis strength remains maximum.
- One review layout for every collection: Turn / Played / Pikafish best. Personal-game advice now uses the same played-ply convention as source annotations. Cached SQL position analyses and saved games need no migration.
- Suggestions open legal alternatives from the pre-move position. Full annotations remain available by long-press. Automatic refresh preserves record scrolling; expanding note banners no longer alter the board stack.
- Settings opened from the viewer preserve its game, position and analysis toggle. Their screen state survives recreation independently of the launcher's remembered tab.

## Verification

Execution reports are retained locally in the ignored `test-results` directory. The initial targeted run checked orientation/recreation, review alignment/variation return, manual scrolling during note refresh, and screen/resume behavior. Three passed; the orientation test initially read a partially rebuilt view from the instrumentation thread and was corrected to inspect it on the UI thread.

Core verification passed: 170 main, 62 MultiPV, 27 date-sorting, 252 appearance, 100 analysis-visual and 728 name/search assertions. All **796 games / 76,445 half-moves** independently replayed legally. No game-pack assets or source manifests changed.

The broader UI round passed **21 tests in 296.333 seconds**: all 20 ComputerGameFlowTest methods and the bundled repository/atomic-import smoke check. After the final navigation and Hint visual adjustments, **4 focused tests passed in 57.297 seconds**, covering a legal two-second Hint without live scores, side/orientation recreation and spacing, saved advice/variation return, and viewer Options recreation/return with analysis preserved. No new crash-buffer entries followed the final round's start. Portable lint passed with **0 errors / 14 warnings**, primarily localization warnings.

Inspected native emulator captures: [New game](../screenshots/new-game-0.9.0.png), [Review](../screenshots/review-0.9.0.png), [Options](../screenshots/options-0.9.0.png). The emulator uses the labelled built-in fallback; it is not a native ARM64 execution claim. The final phone run executes the delivered ARM64 build separately.

The native delivery gate is `scripts/build.ps1 -Check`: clean assembly, lint, test-APK assembly, latest official engine/network verification, game-pack hashes, notices and APK signature. It replaces only the verified latest APK and removes older delivery APKs. Installation uses `adb install -r` on the connected Moto so personal games and settings remain installed. Native phone checks supplement the portable Android 12 emulator audit.

## Limits

The 1–100 levels remain uncalibrated difficulty settings. The actual packaged Pikafish 2026-09-06 UCI option list was queried; it exposes no Skill Level, UCI_Elo or UCI_LimitStrength setting. Automatic review remains a resumable two-second-per-position estimate; missing advice can still be preparing. Casual repetition rules are not a complete WXF tournament arbiter. Passing the covered checks does not prove the absence of every possible bug.
