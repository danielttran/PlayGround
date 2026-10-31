# Xiangqi Study 0.7.9 audit

Date: 2026-10-31. Version code 18. Baseline: a9aa753 (0.7.8).

## Changes and review

Removed the ten requested piece finishes: Xiangqi One Coral, Golden ivory,
Carved walnut, Copper, Brushed silver, Night jade, Cream, Wood, Jade and
Porcelain. All 24 board presets remain available. Retired saved piece IDs resolve
to the red/black Classic finish. Custom color overrides survive migration and
unrelated settings changes; selecting a piece preset explicitly restores its
own colors.

The 16-finish catalog adds Ruby and snow, Scarlet and cobalt, Sunset and navy,
and Garnet and mint. Their Chinese glyph contrast exceeds 4.5:1 on both faces,
and their face colors have RGB distance above 180. Obsidian now has a saturated
crimson Red face with white characters, exceeding 7:1 glyph contrast. The new
procedural palettes and inspected web references are documented in
[Appearance sources](APPEARANCE-SOURCES.md). No symbol pieces were added.

The Chinese character selector follows coordinates. Move slide duration is
independent of autoplay delay, defaults to 200 ms, and offers Off, 100, 200, 300,
500 and 800 ms. It persists immediately and updates the current board. The
logical position updates immediately; the slide only affects drawing. Forward
and reverse captures preserve the correct pieces. First binding and large
position jumps settle immediately. Flipping, disabling motion and detaching
cancel the current sprite; rapid moves replace it with the latest transition.

The library places Return to current game and Import games on one compact row
using the normal button color. Return appears when a game is available. Active
Human/Engine/My imports tabs retain their selected color. About & sources is
removed from the library header.

## Verification

Host checks pass: 170 core assertions, 62 MultiPV, 27 date sorting, 252 appearance,
100 analysis visuals and 408 player names. Independent replay validates all
428 games and 36,227 legal half-moves. The source collection is unchanged.

Two rendered-frame Android checks pass in 5.823 seconds across all 16 finishes.
They compare start/mid/end frames, exact final pixels against a nonanimated board,
forward/reverse captures, interrupted moves, board flipping and disabled motion.
A controlled monotonic clock keeps these comparisons deterministic. An earlier
wall-clock run encountered a stalled emulator System UI and was discarded; the
owned emulator was restarted, with no phone or PC restart.

All 15 remaining focused Android cases pass in 228.998 seconds: eight appearance
and lifecycle cases, three replay-slider cases and four variation-editing cases.
Together with the two rendering cases, all 17 targeted cases pass. After the
late library-header change, the existing collection return/search/sort/navigation
case also passes in 33.858 seconds, for 18 targeted cases overall. The emulator
crash buffer is empty after the run. All Android checks target emulator-5554, an Android 12
x86 instance using the built-in engine. Native ARM64 execution is not tested on
the personal phone; the production engine/network bytes are verified separately.

## Delivery

The clean ARM64 build succeeds in one minute. APK version 0.7.9 / code 18, lint
(`No issues found`) and APK v2 signature verification pass. Both online build
gates confirm latest official stable Pikafish 2026-09-06 and matching NNUE
`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.

The verified APK atomically replaces the prior public APK. Only the latest
delivery remains in `dist`: 52,034,492 bytes, SHA-256
`774b8bbce1fa28e65e8bdb30c8326f2f39167f80bf64fe0756bfeeb794cf833b`.

## Screenshots

- [Ruby and snow](../screenshots/079-ruby-snow.png)
- [Revised Obsidian](../screenshots/079-obsidian.png)
- [Coordinates, characters and slide duration](../screenshots/079-appearance-settings.png)
- [Library action row](../screenshots/079-library-actions.png)
