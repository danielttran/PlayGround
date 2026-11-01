# Xiangqi Study 0.9.1 audit

Version code 29. Audited 2026-11-01, based on 0.9.0 (`17f2003`).

## Changes

- Approved Cobalt, Amber, Plum and Graphite app palettes, each supporting Light and Dark. System follows Android appearance; Cobalt Dark is the default. Options offers spaced icon controls and color swatches.
- Shared colors cover Play, Database, review, navigation, dialogs, sliders, switches, text inputs and system bars. Red/Black labels keep their semantic colors. Board/piece presets and custom RGB colors use independent settings.
- Theme recreation preserves ongoing games, selected side and difficulty, review variations, analysis toggle, collection state and manual move-record scrolling.
- RGB picker values initialize before display; preview/swatch text chooses black or white using luminance. Test cleanup now also restores the actual appearance preferences, preventing audit fixtures from changing user board settings.

## Verification

Core checks passed: 170 main, 62 MultiPV, 27 date-sorting, 252 appearance, 100 analysis-visual, 139 palette and 728 name/search assertions. The new palette checks cover readable primary/secondary/semantic text, accent and button contrast in all eight combinations. All **796 games / 76,445 half-moves** independently replayed legally. No game assets, source manifests or engine payloads changed.

The first build caught an API-27 navigation-bar XML attribute against the API-26 minimum. It was removed; runtime system-icon styling supplies the compatible behavior. The first UI round passed four of five tests and caught a deferred picker-initialization issue; the picker now populates before showing. Historical failure reports remain in the ignored `test-results` directory.

The broader Android 12 UI round passed **25 of 26 tests in 276.063 seconds**, including all five theme tests, the repository/atomic-import smoke check and 19 gameplay checks. The remaining older viewer-return test timed out while polling a UI-owned lifecycle field from the instrumentation thread. It passed unchanged in isolation in 17.953 seconds; its polling now reads on the UI thread. No production failure was reproduced from that timeout. Portable lint passed with **0 errors / 14 warnings**, primarily localization warnings.

The final focused round passed **4 tests in 57.694 seconds** on the updated code: picker initialization/light Play, variation/analysis/scroll preservation across theme return, undo during a pending reply and ordinary viewer Options return. The emulator crash buffer remained empty.

Inspected Android 12 emulator captures: [Cobalt Dark](../screenshots/theme-dark-0.9.1.png) and [Cobalt Light](../screenshots/theme-light-0.9.1.png). The emulator uses the labelled built-in fallback; native ARM64 delivery is verified separately.

The delivery gate is `scripts/build.ps1 -Check`: clean native assembly, lint, test-APK assembly, latest official Pikafish/network guard, game-pack hashes, notices and APK signature. Only a verified build replaces the latest APK. Phone installation uses `adb install -r`, retaining personal data.

The user chose installation with their own later visual check because the Moto is locked. Physical-phone UI tests are therefore deferred; a non-UI native search/cancellation check and installed/SD-file verification remain part of delivery. No security or phone wake settings need to change.

## Limits

Contrast checks and covered lifecycle/UI scenarios do not prove every device or accessibility configuration. Playing levels remain uncalibrated difficulty settings; engine behavior and tournament-rule limits are unchanged from the 0.9.0 audit.
