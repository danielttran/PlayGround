# Xiangqi Study 0.7.7

Date: 2026-10-31. Version code 16.

Appearance settings now use left/right arrows around the current choice for
board style, Chinese character style, piece finish and coordinates. Both
directions wrap. Each explicit press updates the label, saved setting and live
preview. Construction fires no change callbacks, preserving custom colors when
opening settings. Board and piece selections clear custom overrides only on
their respective surfaces; character and coordinate changes preserve them.

The preview is pinned above a separately scrolling controls area. Its size is
bounded by the display, and the controls use the remaining height. Selecting
styles and scrolling down to the color controls leave the preview visible.

Two focused Android 12 emulator checks pass in 14.881 seconds, covering every
board and piece option, forward/backward wrapping, live preview colors,
coordinates/characters, custom-color precedence, pinned preview geometry and
saved settings after reopening. The reopening check exposed an asynchronous
old-dialog dismissal clearing the newly opened dialog reference; dismissal now
clears only its own current instance. Both checks pass after that correction.

The phone build passes in 16 seconds; lint reports No issues found. Packaged
version is 0.7.7 / code 16. Both online engine gates confirm latest official stable
Pikafish 2026-09-06 with matching NNUE
`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.
The exact payload and APK v2 signature verify. The corpus is unchanged at
428 games / 36,227 half-moves.

APK: 52,033,288 bytes; SHA-256
`31bf2670a4b2f2c066dc6da542f6e1992dd3ecdf24fbadf834ef2719d8cf3ae6`.
Only the latest public APK remains in dist. Interaction checks use emulator-5554
with its built-in engine; no automatic installation targets the personal phone.

[Appearance arrow screenshot](../screenshots/077-appearance-arrows.png)
