# Xiangqi Study 0.7.10 audit

Date: 2026-10-31. Version code 19. Baseline: abd9b59 (0.7.9).

## Collection admission

The sixth human pack adds 130 complete Phuong Trang Masters 2025 games and
12,216 half-moves. The total is 558 games / 48,443 half-moves. All five previous
assets compare byte-for-byte unchanged against the baseline. Imports and saved
game IDs are untouched. New Chinese source-name identities have sourced readable
Latin names, retaining the existing display spellings and search aliases.

The source inventory has 138 records. Three rapid tiebreaks and four records
with two complete legal notation traces are excluded. An eighth record, 1379,
explicitly says the scoresheet is lost and contains only two half-moves. The
exploratory 131-game PGN mistakenly included that placeholder; the delivered
pack and refreshed standalone PGN both contain 130 complete games. Legal replay
is necessary but does not establish completeness by itself.

All admitted dates and phases agree with the official schedule at 60+30. Swiss
opponents, colors and results agree with the official pairing tables. Aggregate
knockout match results are not used as individual classical-game outcomes. The
classical final is correctly drawn, independently confirmed by the official
39-move report. Event-wide record counters are retained as source audit data,
not displayed as physical board numbers. See [source evidence](FUTA2025-SOURCES.md).

The reproduction script pins source-game fact hashes and pairing facts, fails
on changed evidence, and rejects ambiguous/unparsed notation. The initial FEN
and complete mainline are compared within the new pack and against all others.
The production APK gate now also enforces this uniqueness across every pack.

## Verification

- Online refresh/reproduction passes for 130 games / 12,216 plies, with eight
  explicit exclusions. A second reproduction confirms the final metadata.
- Core checks pass: 170 core assertions, 62 MultiPV, 27 dates, 252 appearance,
  100 analysis visuals and 450 player names. Independent replay validates all
  558 games / 48,443 legal half-moves.
- The refreshed standalone PGN passes the production PGN importer with all
  130 games / 12,216 half-moves and preserves source URLs and clocks.
- Two existing Android collection checks pass in 28.297 seconds: built-in versus
  named-import isolation, and per-import search/sort/navigation plus return to
  the saved current game. Manual UI verification confirms 558 loaded games,
  readable new names, the new collection's dates/rounds, and its search results.
- Lint reports `No issues found`; APK v2 signature verification passes.

Android interaction checks use emulator-5554 only, an Android 12 x86 instance
with the built-in engine. No APK is installed or tested on the personal phone.
Native ARM64 engine/network bytes are verified by the production build gates.

## Engine collection access

The user relaxed the engine collection's year restriction and requested Kaka's
league. Registration reached a successful CAPTCHA/email-request response, but
the user reports no verification email. The forum offers username/password
sign-in and email-based registration; no alternative sign-in method was shown.
Its public post, printable view and organizer links did not yield a verified
game archive. No Kaka games or unrelated substitute engine games are bundled.
No email-provider ban is established, and no account/login success is claimed.

## Delivery

The clean ARM64 build succeeds in 21 seconds. Packaged version is 0.7.10 / code
19. Both online gates confirm latest official stable Pikafish 2026-09-06 and
matching NNUE
`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.

Only the latest public APK remains in `dist`. Size: 52,087,716 bytes. SHA-256:
`fe086bdc1a65957b4b9c857e74ef9663b671abae7a8e9a0a8149d75e3361750f`.

Screenshot: [Phuong Trang collection](../screenshots/0710-phuong-trang-library.png).
