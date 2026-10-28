# Xiangqi Study 0.7.4 audit

Date: 2026-10-28. Version code 13.

## Library responsiveness

Library filtering previously folded every player's aliases and sorted the full
collection on the UI thread for every keystroke. `LibrarySearchIndex` now owns
folded metadata and cached date orders on a separate background executor.
Text changes debounce for 120 ms. Request generations, collection/query/sort,
source-list identity and view identity reject obsolete results. Search views,
focus and cursor remain in place. Imports replace the index source; backgrounding
cancels pending work, foregrounding resumes it and destruction closes the worker.
Replay game navigation consumes the same prepared order.

A separate read-only review caught a cached-sort race: newest → pending oldest
→ cached newest left the oldest request marked pending, suppressing the next
oldest request. Applying a current cached result now cancels obsolete work first.
The focused concurrency checks include that sequence.

## Collections

Source-division admission removes exactly **132 women's games**: 70 from 2025,
49 from 2023 and 13 from the 2022 Vietnamese supplement. All **392 retained IDs,
prior metadata fields and exact move sequences** compare unchanged against the
previous 524-game library. The historical pack adds explicit men's roster
evidence. Downloaders enforce the preference before writing the assets. Private
imports and the optional Ingenious collection are unaffected.

The new [Vietnamese masters pack](VIETNAMESE-CLASSICS.md) adds **36 complete games /
3,112 half-moves**: 18 from the 2009 Phuong Trang Swiss stage at **80+30**, and 18
from the 2016 main knockout/classification stages at **60+30**. Selection uses
strong direct opposition, tournament performance and later match stages. It is
not an objective engine ranking of the best games. Two ambiguous full notation
records and one conflicting player identity remain outside the preload.

The federation's source records, regulations, round calendar, cross-table and
knockout brackets support the selected metadata. Whole-mainline notation must
have exactly one complete legal interpretation; all source tokens and admitted
half-move counts are retained. Policy/source hashes pin the reviewed facts.
The new generator reproduces the asset and manifest with `--check`.

| Bundled collection | Games | Half-moves |
|---|---:|---:|
| World Championship 2025, men | 215 | 17,357 |
| World Championship 2023, men | 107 | 9,362 |
| Historical World Championship masters | 52 | 4,837 |
| Vietnamese international 2022–2023 | 18 | 1,559 |
| Vietnamese masters 2009/2016 | 36 | 3,112 |
| **Total** | **428** | **36,227** |

No duplicate IDs or complete mainlines occur. Every pack agrees with its source
manifest hash. Nine additional canonical player-name mappings and three source
variants provide readable names/search while exports retain the source fields.

## Core and independent engine checks

- Core passes 170 base assertions, 62 MultiPV, 27 date-sort, 244 appearance,
  100 analysis-visual and 408 player-name assertions.
- Production Java legally replays **428 games / 36,227 half-moves**.
- Official Windows Pikafish verifies **1,735 sampled positions** across every
  game, comparing the complete production history command, resulting board/turn
  and legal-move sets. It completes **1,717 legal depth-one searches**, with 18
  terminal positions, in **33.823 seconds**. One perft-only clock normalization
  preserves the original history for search.
- The portable APK builds, its assets/signature verify and lint reports
  **No issues found**.
- **14 Android 12 emulator cases pass in 126.871 seconds**: three search
  concurrency checks, three collection-navigation/lifecycle checks, four named
  import/collection/replay-speed checks, sorted game navigation, real-keyboard
  search focus, and two actual-touch variation cases with analysis off/on.
  The blocked-worker fixture uses 5,000 games and confirms that a UI frame,
  typing, focus and cursor remain responsive before releasing the worker.
  The emulator crash buffer is empty after these checks.

Android interaction checks use only **emulator-5554**, an Android 12 x86
environment with the built-in engine. The Windows engine checks exercise the
actual subprocess adapter; they do not execute Android ARM64 machine code.
No automatic test or installation targets the personal phone.

## Screenshot

[Vietnamese masters: 36 of 428 games, with readable player names](../screenshots/074-vietnamese-masters.png)

This is an Android 12 emulator capture from the tested portable build. The
unchanged replay layout is also shown in the [0.7.3 screenshots](AUDIT-0.7.3.md#screenshots).

## Delivery

The clean phone build passes in **16 seconds**. Both the pre-build and final
payload gates confirm latest official stable **Pikafish 2026-09-06** and matching
NNUE SHA-256
`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.
The exact executable/network/license bytes, APK v2 signature and lint checks
pass. Packaged version is **0.7.4 / version code 13**.

The APK atomically replaces `dist/XiangqiStudy-latest.apk`; it is the only public
build in that directory. Size: **52,031,660 bytes**. SHA-256:
`f3f36e42aa5af76bf06660c2c6426af6dd0c98194b112e0037f85466d2ca86b8`.
