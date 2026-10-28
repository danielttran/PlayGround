# Xiangqi Study 0.5.1 — contextual game actions

Date: 2026-10-28. Delivery: `dist/XiangqiStudy-latest.apk`.

## Change

Recorded replay no longer shows a standalone **Play from here** button. The
top-right **Game menu → Play against engine** is the entry point for computer
play. A contextual **Stop playing** action appears during a computer game;
after stopping, **Return to recorded game** lets the user leave the variation.
Returning to the source record hides this action completely (`View.GONE`).
The board, position label, slider and five replay buttons remain above it.

Version code is 6, version name is 0.5.1. The 24 board presets, 22 Chinese-only
piece finishes, four URL-free reference looks, custom-color precedence and
337-game library from [0.5](AUDIT-0.5.md) are retained.

## Verification

The new Android regression checks actual visibility during recorded replay,
move navigation, menu-based engine entry, stopping, and returning to the
unchanged recorded moves. The variation persistence test now uses the actual
menu to enter engine play and stop before editing a variation.

Independent source review checked the conditional action, source preservation,
menu entry and test migration. Portable build, lint, payload and APK signature
checks pass. The complete Android 12 emulator suite reports 45 passing tests in
281.706 seconds: 43 exercise UI, rendering and storage, while two native-only
cases explicitly log NOT_RUN on x86. Both native tests pass separately on the
connected Android 12 ARM64 phone in 31.309 seconds.

The verified APK was 51,954,251 bytes, SHA-256
`b83e29b870c81797084359b2883988f2c5c43f2d4ff8d0ce4d4d90ccfe138f6f`.
It is historical evidence; the single delivery path is replaced by later builds.

The production build queries the official latest stable Pikafish release and
checks the matching engine, NNUE and licenses. Its final APK verifier repeats
the latest-release check. A verified replacement atomically replaces the one
public delivery APK; older delivery builds are removed.

Earlier core rules/parser/search/name/date/catalog and complete library replay
checks are documented in the [0.5 audit](AUDIT-0.5.md); this patch changes the
replay action's presentation and its regression tests.
