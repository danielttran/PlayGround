# Historical master-game preload

The separate `app/src/main/assets/historical-games.json` pack contains **52 classical games / 4,837 half-moves**, curated from the [World Xiangqi Federation's official historical championship archive](https://www.wxf-xiangqi.org/index.php?Itemid=313&id=218&lang=en&option=com_content&view=article). The collection features Hu Ronghua, Li Laiqun, Zhao Guorong, Lü Qin, Xu Yinchuan and Wu Kui-lin against international opposition. It adds earlier generations to the existing 2025 pack.

| Event | Games |
|---|---:|
| World Championship 1991 | 5 |
| World Championship 1993 | 3 |
| World Championship 1995 | 2 |
| World Championship 1997 | 20 |
| World Championship 1999 | 5 |
| World Championship 2003 | 17 |

These are selected historical championship games, not a claim that every score is a universally ranked “best game.” The selection follows named former champions and the user's preferred players, rather than importing the entire archive.

## Inclusion and source evidence

- Every included source record explicitly has `DhtmlXQ_gametype=慢棋` (slow/classical). Rapid, blitz, exhibition and unclassified records are excluded. Exact minutes and increment are stated only when the individual source supplies them; otherwise the app says “Classical · exact clock unrecorded.”
- The source's standard 32-piece starting arrangement and zero initial move number are required. Only the factual played main line, players, event, result, dates and source attribution are bundled. Source annotations, variations, photographs, PDFs and third-party board code are excluded.
- Both participant fields are checked against `data/curation-policy.json`. This conservative source-name screening records known sanctions; it does not certify an individual's history.
- Each game has a clickable official source URL and stable article/record ID. `data/historical-manifest.json` records every downloaded page's SHA-256, the output hash, policy hashes, and all excluded record IDs/reasons.
- Exact duplicate main lines are removed. Two distinct Lei Kam Fun versus Lü Qin scores in the 1997 archive have conflicting outcomes and no round/day to resolve their event attribution. Both are held out in the policy.

The 1991 [article 444](https://www.wxf-xiangqi.org/index.php?id=444&option=com_content&view=article&lang=en), 1993 [article 406](https://www.wxf-xiangqi.org/index.php?id=406&option=com_content&view=article&lang=en), 1995 [article 407](https://www.wxf-xiangqi.org/index.php?id=407&option=com_content&view=article&lang=en), [1997 archive index](https://www.wxf-xiangqi.org/index.php?id=408&option=com_content&view=article&lang=en), [1999 archive index](https://www.wxf-xiangqi.org/index.php?id=440&option=com_content&view=article&lang=en), and [2003 archive index](https://www.wxf-xiangqi.org/index.php?id=502&option=com_content&view=article&lang=en) supply the records. The exact constituent article URLs are in the manifest.

## Dates

There are 40 day-precision dates, 11 month-precision dates and one year-precision date. Zero-filled dates such as `1997-11-00` become `1997-11`; no artificial first day is inserted. The original value is retained as `sourceDate`.

The 1991 Ma Chung Wei–Hu Ronghua record says August 1, while its archive identifies a September championship. It is retained with the source's event attribution but only the year displayed, and an explicit date note; its exact event/day attribution remains source-limited. The repeated 1995 Lü Qin–Wu Kui-lin score has conflicting September 5/7 dates, so only the month is retained. A 2003 December 3 date predating the championship opening is likewise reduced to month precision. These uncertainties are recorded, not silently repaired.

## Reproduction and audit

```powershell
C:\Python314\python.exe scripts\download_historical.py
# After downloading once, reproduce from the cache without network access:
C:\Python314\python.exe scripts\download_historical.py --offline
```

`data/historical-policy.json` defines the bounded source pages, target masters and explicit exclusions/date overrides. Downloads use the Python standard library, retries, local caching and one-second pacing. The cache is intentionally ignored by Git; a fresh checkout uses the online command first.

The final asset's SHA-256 is `3f88ee5ae280c219d09371983b717cef8bc43493ef88b051e9af37626786b958`. Rebuilding offline produced exactly the same bytes. Every one of the 4,837 half-moves in all 52 games was replayed with the app's production `Position.play` rules; no illegal moves were found. Independent native Pikafish validation is performed by the app's combined corpus audit, whose current result is recorded with the build evidence.

One 333-half-move 1999 score reaches the native engine's rule-60 counter limit. The official executable accepts the complete history and returns a legal best move in actual search. Its `go perft` utility reparses the engine's generated FEN and rejects a counter of 120; this is a diagnostic utility limitation, not an illegal score. The native audit uses a counter-normalized copy for legal-move enumeration, then restores the full history for the real search. The original moves are retained unchanged.

## Player spellings

The app preserves original source names for export, while its display/search mapping uses established player spellings. Useful primary references are WXF's paired [English men's winners](https://www.wxf-xiangqi.org/images/past_wxc_champions/2022_2-Mens_Individual.pdf) and [Chinese men's winners](https://www.wxf-xiangqi.org/images/past_wxc_champions/2022_2-Mens_Individual_cn_.pdf), plus the [English non-Chinese/non-Vietnamese winners](https://www.wxf-xiangqi.org/images/past_wxc_champions/2022_3-Non_CHNVIE_Individual.pdf) and [Chinese table](https://www.wxf-xiangqi.org/images/past_wxc_champions/2022_3-Non_CHNVIE_Individual_cn_.pdf). These resolve country-specific forms such as Hor Weng Yeow, Dang Hung Viet, Quach Anh Tu, Tan Cheak Joo, Hua Say Ty, Lei Kam Fun and Omata Mitsuo.

The [Vietnam Chess Federation's 2007 standings](https://vietnamchess.com.vn/news/world-xq2007) explicitly pair 吴裕成 with Ung Yu Dau. The Italian Xiangqi Federation's [February 1999 newsletter](https://www.pi.infn.it/~carosi/chess/xqmtb50.pdf), page 2, lists Yu Jianguo in its Italian top ten, consistent with the 1999 game record's Italy team field for 余建国. Mandarin readings should not be substituted for these established international names.

## Other researched collections

The user also permits domestic historical classics. Two strong named collections are Jim Png's [Hu Ronghua Sandwiched Horse Defense Treatise](https://www.xqinenglish.com/index.php?Itemid=511&catid=235&id=903%3Agm-hu-ronghua-s-sandwiched-horse-defense-treatise-chapter-00-introduction&lang=en&option=com_content&view=article), whose final chapter contains 13 annotated played games, and his Hu Ronghua Elephant Opening game collection, described in the [2016 activity archive](https://www.xqinenglish.com/index.php?Itemid=627&catid=292&id=954%3Aactivity-archive-2016&lang=en&option=com_content&view=article). Both are useful study sources. Their endpoints repeatedly timed out during this run, so their game data and commentary are not bundled.

The famous 1960 national championship Yang Guanlin–Hu Ronghua game has a public [01xq record](https://www.01xq.com/e_game_view.asp?id=001053F68F062F). Its main line was downloadable, but the inspected page did not supply an explicit classical game marker or original clock evidence, so it was held out under the pack's admission rule. Likewise, the 1990 WXF archive has early Hu Ronghua/Lü Qin games but no explicit slow-game markers in its inspected records. The result is a smaller verified pack rather than uncertain games labeled classical.
