# Preloaded international classical games

The starter collection contains **215 real adult men's games, 17,357 half-moves**, from
the 19th World Xiangqi Championship in Shanghai, 22–27 September 2025. It includes
9 games each by Lai Ly Huynh (赖理兄), Nguyen Thanh Bao (阮成保), Wu Zonghan (吴宗翰),
Yin Sheng (尹昇), and Meng Fanrui (孟繁睿). Counts overlap when these players meet. Daniel confirmed that
the Singapore player he calls “Andy Wo” is 吴宗翰; that search alias is user supplied.

The men's final, Yin Sheng–Lai Ly Huynh, is the first game. The original 108
half-moves and 0–1 result are retained. The requested players follow it in the
library, with the remaining games available through browsing and search.

## Primary sources and classical time control

- [Official WXF tournament record index](https://www.wxf-xiangqi.org/index.php?Itemid=101&id=1788&lang=en&option=com_content&view=article).
- [Official championship regulations](https://www.wxf-xiangqi.org/images/2025wxf_shanghai/19th_WXF__World_Xiangqi_Championship_Regulations.pdf),
  English page 5, section VII(b): **60 minutes base time plus 30 seconds per move**.
- Each game carries its individual round-page URL and original Chinese source
  title. The data manifest records the SHA-256 and size of every fetched page.

Adult men's rounds 1–9 and women's rounds 1–7 are examined, but only men's
records are admitted. The 70 women's records are listed in the manifest as
omitted by the user's collection preference. Division comes from the official
round page, rather than guessing from a player's name. The separate
World Open Rapid and youth links on the same index are never followed. Every
included record's own `DhtmlXQ_timerule` agrees with 60+30; unknown or different
time controls stop the downloader instead of silently entering the asset.
The normalized JSON/PGN `timeControl` is `3600+30` (seconds, following PGN
conventions); `sourceTimeControl` retains the source's original minute notation.

## Curation requested by Daniel

The WXF pages contain 295 adult record slots. Two contain no moves (forfeits) and
are omitted. The remaining 293 records were independently replayed with the
application's Xiangqi rules validator: **23,874 legal half-moves**.

Eight records then matched the exclusion name 李小龙 and were conservatively
omitted. This is **a source-name match**, not an assertion that the participant in
those records is the person named in a disciplinary notice. The 70 women's records
are also omitted by the user's collection preference. The final pack contains
215 games and 17,357 half-moves. Excluded record IDs and source URLs
are retained in `data/wxf-manifest.json`.

`data/curation-policy.json` contains the repeatable exclusion list, including
expired penalties and criticism notices, based on:

- [2024 official sports authority notice](https://www.sport.gov.cn/n20001280/n20067662/n20067613/c28114979/content.html).
- [2025 association notice reproduced in full by The Paper](https://www.thepaper.cn/newsDetail_forward_29908354).
- [2026 official sports authority notice](https://www.sport.gov.cn/n20001280/n20067662/n20067613/c29534486/content.html).

Screening is dated **27 October 2026**. Two anonymized minors in the 2025 notice
cannot be matched to identities. This process does not establish that every
included player has no misconduct history; it excludes known notice-name matches
from the checked sources. It also does not assert that every participant holds a
grandmaster title. The collection is an international championship archive with
the requested players prioritized.

## Reproduce or refresh the library

Requires Python 3.10 or later, standard library only:

```powershell
python scripts/download_wxf.py
python scripts/download_wxf.py --offline
python scripts/download_wxf.py --refresh
```

The first command requests the index and 16 round pages, using bounded retries,
one-second request spacing, and a local `.cache/wxf` cache. `--offline` reproduces
the asset from that cache; `--refresh` rechecks all pages. The output asset and
manifest are written only after the entire download and conversion succeeds.

The script extracts factual player/event metadata and the unannotated main-line
move sequences embedded in WXF's DhtmlXQ records. Its coordinate conversion is
`x,y -> file(x), rank(9-y)`: `a0` is Red's bottom-left intersection and `i9` the
top-right intersection from Red's view. It refuses nonstandard starting boards,
nonzero starting offsets, malformed coordinates, or unknown results.

Leading source bib numbers are removed from player names. Other source spelling
differences are retained. Selected player names have English search aliases;
their original Chinese names remain visible.

## Attribution and rights

The WXF website states “All Rights Reserved.” This project does not claim a
general redistribution license from WXF. Only factual moves and basic event
metadata are bundled with attribution; page layout, photography, annotations,
and the DhtmlXQ board code are not bundled. Cached source HTML is local diagnostic
material and is excluded from Git. App source licensing does not relicense WXF
materials or external engine assets.

The downloader is specific to this verified event. Additional tournaments need
their own source, time-control checks, and replay validation before preloading.
Users can import additional supported records through Android's document picker.

## Full-library review, 31 October 2026

The 0.7.11 review found three incorrect source player tags in this pack:
`wxf-2025-men-r2-g17` repeated Vladimir Medvedev as Black instead of Davide
Nastasio; `wxf-2025-men-r8-g9` repeated Winston Yu as Black instead of Quang
Nhat Nguyen; and `wxf-2025-men-r8-g11` truncated Liu Jiaqi's name to 刘嘉.
The game titles and the [official results booklet](https://www.wxf-xiangqi.org/images/2025wxf_shanghai2/2025_19th_World_Xiangqi_Championship19.pdf),
men's individual tables on PDF pages 5–8, establish the corrections. Original
tags, explanatory notes and evidence links remain on the three corrected records.
No game IDs, moves, results or dates changed.

`data/wxf2025-metadata-policy.json` pins the booklet hash and its 51 men's rows.
The downloader now checks every admitted game's reciprocal opponent numbers,
Red asterisk and cumulative-score differences against those rows. All 215
games agree. It also checks round dates and rejects identical players.
New source changes affecting a reviewed correction fail pending manual review.

`python scripts/audit-collection-sources.py` reproduces all six human packs
against their downloaded sources without changing shipped files, checks
manifest hashes/counts, dates, player fields and full-mainline uniqueness, and
lists retained uncertainty notes. Reproduction confirms agreement with reviewed
sources; it does not independently prove every historical source claim.
