# Xiangqi Study 0.7.11 collection review

Date: 2026-10-31, America/New_York. Version code 20. Baseline: ebab31f.

## Findings and corrections

The complete preload remains 558 games / 48,443 half-moves. Three player fields
in the 2025 World Championship source were wrong:

- Round 2, record 17: Black is Davide Nastasio, rather than a repeated Vladimir
  Medvedev.
- Round 8, record 9: Black is Quang Nhat Nguyen, rather than a repeated Winston Yu.
- Round 8, record 11: Red's full name is Liu Jiaqi (刘嘉琪), rather than 刘嘉.

The original game titles and the official results booklet independently agree
on the corrections. The 51 men's individual rows on PDF pages 5–8 establish
reciprocal opponents, Red/Black assignment and cumulative point differences.
All 215 admitted 2025 World Championship games now pass those checks. Dates
also agree with the round schedule. The downloader fails if an explicitly
reviewed source correction changes, and both the downloader and APK gate reject
identical players. Original tags and correction evidence remain in each affected
record. No IDs, moves, results or dates changed. The other five packs are
byte-for-byte unchanged. See [source evidence](SOURCES.md#full-library-review-31-october-2026).

## Full collection checks

- All six human packs reproduce exactly from their downloaded official source
  records and reviewed curation policies. Asset hashes, counts, dates and player
  fields pass, with zero duplicate starting-position/full-mainline combinations.
- Production Java replay accepts all 558 games / 48,443 half-moves.
- Official Windows Pikafish legal-move sets agree at every replay position:
  49,008 cases including final positions and seven custom audit fixtures,
  representing 43,087 distinct board/turn combinations. This geometry check
  resets clocks and does not independently certify repetition adjudication.
- A separate production-UCI-history check covers 2,255 positions across every
  game and fixtures. All legal-move sets agree; 2,237 nonterminal positions return
  legal depth-one best moves. One perft-only Rule60 clock normalization is
  required for the previously documented long historical score; search restores
  the actual history. No native error or crash occurs in these checks.
- The existing core suite passes: 170 core, 62 MultiPV, 27 date, 252 appearance,
  100 analysis-visual and 447 player-name assertions.
- The optional Ingenious collection also passes: 68 games / 5,552 half-moves,
  JSON/PGN moves and metadata agree, and no duplicate or matching excerpt occurs
  against the current 558-game preload or within that optional collection.

Source reproduction verifies agreement with reviewed source evidence. It does
not turn unsupported historical details into facts: 12 historical records keep
partial dates, 51 historical games have no exact recorded clock, and correction
or uncertainty notes remain visible in the data. The optional book collection's
unknown dates/clocks remain unknown. User-created/imported collections on the
personal phone were not accessible and are not included in this review.

Reproduce the additional checks with:

```powershell
python scripts/audit-collection-sources.py
python scripts/test-native-pack.py --engine PATH/TO/Pikafish-Windows-x86-64-universal.exe --all-plies --legal-only
python scripts/test-native-pack.py --engine PATH/TO/Pikafish-Windows-x86-64-universal.exe
python data/collections/ingenious/verify.py
```

## Engine archive research and book moves

Dongping's landing page loads, but actual keyword searches require login with
an explicit AI-crawler message. Pikafish Fishtest redirects to login. The
Fairy-Stockfish archive provides settings and aggregate test results, not a
verified full-game collection. No recent NNUE archive meeting the requested
provenance requirements was obtained, and no substitute engine games were added.
See [engine source review](ENGINE-SOURCES.md).

No shipped pack has verified engine book-exit metadata. Book badges and an
Engine play divider were discussed as a possible presentation for sources that
explicitly identify book moves. They are not implemented. An opening name or
database match does not prove book use, and noninitial starting FENs must not be
expanded with invented preceding moves.

## APK delivery

The clean ARM64 build completes in 22 seconds, lint reports `No issues found`,
and APK v2 signature verification passes. Online prebuild and delivery gates
verify latest official GitHub stable Pikafish 2026-09-06 and matching network
`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.
The current native checks run the corresponding official Windows executable;
they do not establish Android ARM64 runtime coverage. Android instrumentation
is compiled, not run again for these data-only app changes. No personal device
is installed or modified.

Only `dist/XiangqiStudy-latest.apk` remains as the public APK. Version 0.7.11 / 20,
52,088,748 bytes. SHA-256:
`1fd5ed48812593ec255c3074816696b531466117f010c239ce6954d0bb8eef52`.
