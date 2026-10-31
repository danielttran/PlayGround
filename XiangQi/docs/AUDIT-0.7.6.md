# Xiangqi Study 0.7.6

Date: 2026-10-31. Version code 15.

Built-in library cards now show the actual event, round and recorded date without
an additional multi-year collection label. The 1991–2003 and 2009/2016 strings
describe packs, not individual games. Those searchable source collection fields
remain unchanged; private import labels are explicitly prefixed Import.
The corpus remains 428 games / 36,227 half-moves.

The lower replay area has a recycling record column on the left and a separately
scrolling engine column on the right when analysis is enabled. With analysis off,
the record fills the available width. The old separate 92 dp analysis block is
removed. Move rows shrink from 40 to 28 dp, with 12 sp move text, 10 sp single-line
numbers and 2 dp leading cell padding. Four-digit move numbers retain room.
The board, slider and replay controls remain above the independently scrolling
columns.

A distinct final adapter row shows the recorded result after all moves, even
with spoiler hiding enabled. Library cards and game information retain their
spoiler behavior. A variation shows an unfinished label instead of exposing the
source game's result. The footer has its own recycling type and cannot navigate
to an invalid half-move.

Focused checks cover the two library label examples, dense rows and side-by-side
geometry, result visibility only at the end, a 2,000-ply recycling record, analysis
lines, slider endpoints/scrubbing and editable variations. Android verification
uses only emulator-5554 (Android 12 x86, built-in analysis); the phone APK bundles
the separately verified official ARM64 Pikafish and matching NNUE. No automatic
installation targets the personal phone.

Verification: 11 focused Android cases pass in the initial run; the old engine
overflow fixture expected scrolling even though its short lines now fit in the
larger column. Expanding it to eight-ply legal continuations verifies actual
overflow and passes in 9.224 seconds. All 12 targeted cases are verified across
those runs. Lint reports No issues found. Manual captures confirm that scrolling
the record to its result leaves both the board and engine column in place; the
emulator crash buffer is empty.

Screenshots: [library](../screenshots/076-library.png),
[compact columns](../screenshots/076-columns.png),
[end-of-record result (contains a spoiler)](../screenshots/076-result-footer.png).
These show the portable emulator engine; the phone build contains Pikafish.

The clean phone build succeeds in 23 seconds. Both online release gates confirm
latest stable Pikafish 2026-09-06 and matching NNUE
`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.
APK payload and v2 signature verification pass. Only the latest public APK is
retained: 52,033,128 bytes; SHA-256
`97b32233a13e7b08f6894044ff6d4296aaec24ed7d180e1409bfc7201c5ea0f0`.
