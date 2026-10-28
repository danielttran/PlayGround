# Xiangqi Study 0.7.3 audit

Date: 2026-10-28. Version code 12.

## Direct board variations

Recorded replay previously passed `editing=false` to the board until a variation
was entered through suggested moves or engine play. A real touchscreen check on
0.7.2 reproduces the user's bug in **8.072 seconds**: tapping a legal source and
destination leaves the app in recorded mode.

Recorded study positions now accept direct legal moves, with analysis on or off.
The existing move handler forks only the working history; it keeps the source
record intact. Return to recorded game restores its complete history at the
current half-move. Enabled analysis follows each branch move and the restored
record, and explicit Stop analysis remains effective. Opponent play still locks
the board during the computer's turn or pending reply.

Touching the board pauses autoplay before source-piece selection, keeping the
position stable while choosing a destination. It does not cancel study analysis
until a move is committed. The compact header, independent move list and slider
remain in place.

## 2023 World Championship expansion

The earlier thirty games were selected by Vietnamese-player participation, not
ranked as the best games. The broader pack contains **156 games / 13,636 half-moves**:
107 men's and 49 women's records, at **60 minutes + 30 seconds per move**.
It adds **126 games** while retaining the thirty existing game IDs and exact moves.

All sixteen adult round pages were fetched from WXF again. The source audit
examines 176 slots; twenty are excluded as byes, an unrecorded game, existing
player exclusions or an explicit rapid tiebreak. Seven source metadata errors
are repaired with retained originals and official evidence: four player fields,
one year typo and two unknown results. All 49 women's pairings/colors/outcomes
agree with the official final cross-table. See [the source audit](WORLD-2023-SOURCES.md).

The four-pack library now contains **524 games / 44,587 half-moves**. A separate
before/after comparison preserves **all 398 previous IDs and move histories**,
with zero duplicate IDs or complete main lines. The Vietnamese supplement now
contains 31 games; its thirty World Championship records are owned by the
broader 2023 pack and are not duplicated. Both downloaders reproduce from cache.

Five official Latin player labels and two source-spelling aliases were added.
All bundled Chinese source names have readable display/search mappings; source
player metadata remains available for export.

## Verification

- Core: 170 base assertions, 62 MultiPV, 27 date-sort, 244 appearance,
  100 analysis-visual and **480 player-name assertions** pass.
- Production Java legally replays **all 524 games / 44,587 half-moves**.
- Independent real Windows Pikafish checks **2,131 positions** across every
  game, comparing the production history command, resulting board/turn and
  legal-move sets. It completes **2,101 legal depth-one searches**, with 30
  terminal positions. One historical perft-only clock normalization preserves
  the original full history for search. The run passes in **39.926 seconds**.
- **14 Android 12 emulator cases pass in 158.308 seconds**: two actual-touch
  variation cases, six game-control/opponent cases, three slider cases, two
  stop/recreation analysis cases and the real-keyboard full-library search case.
  The search fixture derives the bundled count instead of hard-coding 398.
- The variation checks exercise both sides, analysis off/on, pause before the
  destination tap, unchanged source moves, restored full history and editing
  again after Stop analysis. A read-only review found no further concrete issue.

Android interaction checks run only on **emulator-5554**, an Android 12 x86
environment using the built-in engine. Host engine checks exercise the real
Windows subprocess adapter, not Android ARM64 machine code. No tests or automatic
installation run on the personal phone.

## Screenshots

- [2023 library filter: 156 of 524 games](../screenshots/073-world-2023-library.png)
- [Direct board variation with Return to recorded game](../screenshots/073-direct-variation.png)

These are Android 12 emulator captures. The variation screenshot uses its
built-in engine; the phone APK contains verified native Pikafish. A manual rook
move in a newly added 2023 game shows the return action and restarts enabled
analysis; returning restores the recorded continuation.

## Delivery

The native build checks the latest official stable Pikafish before compilation
and during final payload verification, including its matching network and
licenses. Signature verification and Android lint are part of the delivery gate.
The verified APK atomically replaces `dist/XiangqiStudy-latest.apk`; only the
latest public build remains.

The final clean build passes in **19 seconds**, lint reports **no issues**, and
the packaged manifest confirms **0.7.3 / version code 12**. The v2 signature
verifies. Both upstream checks confirm latest stable **Pikafish 2026-09-06**,
with matching NNUE SHA-256
`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.

APK: **52,058,503 bytes**, SHA-256
`f1812cfc1dd37dc736675eaa5882a553ca5fdf74b7ce2933c95e4166393620fd`.
