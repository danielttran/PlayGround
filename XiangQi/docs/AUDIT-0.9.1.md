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

## Moto delivery verified

- Application code commit: `44d22b2f44887f94d400f31228788fecc3b57284`. The clean native build and lint passed with **0 errors / 14 warnings**. Latest stable Pikafish **2026-09-06**, matching NNUE `7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`, payload hashes, notices and APK v2 signature passed verification.
- Delivered **0.9.1 / code 29**, **52,397,936 bytes**, SHA-256 `39103050ecca3415df20c0ad826edf6c02255e7a640ab65122ec891c1f5e8592`. The installed base APK and **SanDisk SD card / Download / XiangqiStudy-latest.apk** match the PC file. Dist contains only the latest delivery APK.
- Moto g stylus (2022), Android 12: actual ARM64 native continuous search/cancellation passed in **2.385 seconds**. The normal launcher opened successfully. No new crash-buffer entries followed deployment start; the phone's wake setting stayed at its original value of zero. Physical UI checks were deferred at the user's request; the emulator theme captures and covered UI assertions are the visual evidence for this update.
- Full logs and delivery proof are retained locally under `test-results/theme-0.9.1-*`. This delivery-record follow-up changes no application code or APK inputs.

## Limits

Contrast checks and covered lifecycle/UI scenarios do not prove every device or accessibility configuration. Playing levels remain uncalibrated difficulty settings; engine behavior and tournament-rule limits are unchanged from the 0.9.0 audit.
