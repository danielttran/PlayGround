# Phuong Trang Masters 2025 classical collection

0.7.12 curation: this pack now contains 124 games / 12,012 half-moves after six records ending before numbered move 20 were removed. Earlier acquisition counts below remain historical evidence.

Reviewed 2026-10-31. The sixth human pack adds **130 complete men's games /
12,216 half-moves**, taking the preload to **558 games / 48,443 half-moves**.
The earlier five packs retain their IDs, metadata and moves. No starting-FEN
and complete-mainline duplicates occur within or across the six packs.

The official public [VietnamChess XQBase](https://vietnamchess.vn/databank/xqbase-vietnamchess)
provides 138 records for the seventh Phuong Trang Masters Cup in Da Nang,
22–28 December 2025. The [regulations](https://vietnamchess.vn/index.php/regulations-futa7)
specify **60 minutes + 30 seconds per move** for the classical games. Seven Swiss
rounds lead to semifinals/finals for the top four; other players continue Swiss
rounds eight and nine. Only the first main game of a knockout match is classical.
The three recorded `8.r1` / `9.r1` rapid tiebreaks are excluded.

The retained games include nine each for Lai Ly Huynh, Nguyen Thanh Bao,
Ha Van Tien and Alvin Woo, and eight for Nguyen Minh Nhat Quang. This is a
documented strong international event selection, not an engine ranking of
the objectively best games. All participants pass the existing exact-identity
curation policy. Vietnamese Uong Duong Bac remains distinct from Wang Yang.

## Source checks and omissions

The [official pairing tables](https://vietnamchess.vn/index.php/futa7-en)
independently agree with all admitted Swiss opponents, colors and results.
Knockout table entries can represent the whole match; they are not substituted
for the result of its first classical game. The [official final report](https://vietnamchess.vn/index.php/news-futa7-en?catid=237&id=2380:futa7-day6-en&view=article)
confirms the 39-move Nguyen Thanh Bao–Xu Wen Zhang classical final was drawn
before its rapid decider. Its informal prose about some Swiss results conflicts
with the actual pairing tables; the structured game records agree with those
tables, which are used for result verification.

The source `board_num` is an event-wide counter (1–135), not a physical board
number. It is retained as `sourceBoardNumber`, never appended to the displayed
round. Dates match the official per-round calendar. Canonical Chinese IDs retain
the original Vietnamese/bilingual names alongside explicit normalization of
traditional characters, a stray closing parenthesis and a player suffix.
Established display names remain; new names have sourced Latin spellings.

Eight source records remain outside the asset:

| Records | Reason |
|---|---|
| 1276, 1320, 1326, 1369 | Two complete legal interpretations of the notation |
| 1384, 1385, 1386 | Rapid tiebreaks |
| 1379 | Explicitly marked `thất lạc biên bản` (scoresheet lost); only two half-moves |

The exploratory 131-game PGN included record 1379 because a legal replay alone
does not prove completeness. The final preload and refreshed standalone PGN
exclude it. Diagram-only `{[D]}` comments are ignored, while unknown comments
or unparsed notation fail reproduction. No missing moves are invented.

## Reproduction

`python -X utf8 scripts/download_futa2025.py --check` reproduces the asset and
manifest from the ignored cache. Add `--refresh` to fetch the public tournament
JSON and official pairing page again. The checked-in policy pins all 138 source
fact hashes, participant identities, schedule, excluded IDs and normalized
pairing facts. A source change fails for review.

The shared converter enumerates the production `Position` rules and accepts
exactly one complete legal mainline. Full source tokens and supplied PlyCount
headers must agree. Generated manifests record final FENs, complete-mainline
hashes, asset/policy hashes and all exclusions. The production APK verifier also
checks complete-mainline/start-FEN uniqueness across the entire preload.
