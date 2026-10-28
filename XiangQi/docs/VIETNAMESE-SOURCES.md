# Vietnamese international classical supplement, 2022–2023

In version 0.7.3 this supplemental pack contains **31 games / 2,774 half-moves**:
27 games from the 2022 World Championship and four Trenh A Sang games from the
2023 Phuong Trang tournament. The thirty 2023 World Championship records moved
to the [broader 156-game collection](WORLD-2023-SOURCES.md), retaining their game
IDs and moves. The downloader deduplicates against that pack, so no game is lost
or displayed twice. The current combined preload is **524 games / 44,587 half-moves**.

## Original acquisition audit

The following records the original 61-game supplement and its earlier asset
hash. Those historical counts include the thirty records now held in the broader
2023 pack. Its source evidence and the 2022/FUTA metadata checks remain applicable.

The third collection adds **61 complete games / 5,452 half-moves** to the
existing 337 games. The combined preload is **398 games / 33,629 half-moves**.
All new games are international adult classical games, with a documented
**60-minute base time plus 30 seconds per move**. They are selected factual
championship and masters records, rather than an objective ranking of the
greatest games.

| Official collection | New games |
|---|---:|
| World Championship 2022, Kuching | 27 |
| World Championship 2023, Houston | 30 |
| Phuong Trang International Masters Cup 2023, Group A | 4 |

The new collection adds 14 Lai Ly Huynh games, 8 Nguyen Thanh Bao games, and
4 Trenh A Sang games. Including the earlier packs, those players appear in
**23, 17, and 7 games**, respectively. Counts overlap when players meet.
Nguyen Minh Nhat Quang, Nguyen Hoang Yen and Le Thi Kim Loan are also included.
The four Trenh games retain their actual recorded results; they are not presented
as a selection of his victories.

## Official records and clocks

- [2022 WXF adult championship record index](https://www.wxf-xiangqi.org/index.php?id=1147&option=com_content&view=article&lang=cn)
  supplies the complete DhtmlXQ main lines. Only the 18 adult men's and 7 adult
  women's round pages are followed; youth and rapid pages are excluded.
- [2022 official regulations](https://www.wxf-xiangqi.org/images/event_announcement_folder_2022/17th_World_Xiangqi_Championship_Regulations.pdf),
  English competition-method section, specify 60 minutes plus 30 seconds per move.
  The individual records independently state `60分钟 + 30秒`.
- [2023 WXF record index](https://www.wxf-xiangqi.org/index.php?Itemid=313&id=1592&lang=en&option=com_content&view=article)
  supplies 9 men's and 7 women's adult round pages. The
  [2023 official regulations](https://www.wxf-xiangqi.org/images/2023_news_upload/2023_Entry_form_for_18th_WXC_.pdf),
  section VII(b), specify the same clock. Men's source records use `60+30`;
  women's records use `1h+30''`.
- The Vietnam federation's [2023 Phuong Trang regulations](https://vietnamchess.vn/index.php/regulations-futa6),
  article 4.1, explicitly give 60 minutes plus 30 seconds from move one for the
  group stage. Only Trenh's Group A rounds 1–4 are included. His fifth opponent,
  Jiang Chuan (蒋川), is excluded by the existing curation policy. Knockout rapid
  and blitz tiebreaks are excluded.

The four Trenh records come from official database IDs
[1463](https://vietnamchess.vn/databank/xqbase-vietnamchess?id=1463&view=game),
[1466](https://vietnamchess.vn/databank/xqbase-vietnamchess?id=1466&view=game),
[1470](https://vietnamchess.vn/databank/xqbase-vietnamchess?id=1470&view=game), and
[1471](https://vietnamchess.vn/databank/xqbase-vietnamchess?id=1471&view=game).
Their names, dates, round/board numbers, results and all **224 half-moves** were
independently cross-checked against the federation's
[complete tournament bulletin](https://vietnamchess.vn/document/2023/futa6-bulletin-full.pdf),
PDF pages 25–26. The original pages were visually inspected. Both Vietnamese
notation in the bulletin and Vietnamese/WXF notation in the database were
converted by uniquely matching the production Xiangqi rules' legal moves at
every position. The two independent conversions agree exactly.

## Metadata audit and documented source corrections

Every new game has a day-precision date. All dates and round labels were checked
against the official schedules, not inferred from download dates.

The [2022 official schedule](https://www.wxf-xiangqi.org/images/2022_Sarawak_17wxc_pairing_result/2022_17th_wxc_competition_schedule_20221019.pdf)
places men's Round 9 and women's Round 7 on **28 October 2022**. Three source
records give a different day: article 1208 game 2 says 27 October, and article
1362 games 2 and 4 say 29 October. The preload uses the verified schedule date,
retains each original `sourceDate`, and records the correction and evidence URL
in `dateNote` and `data/vietnamese-policy.json`. The separate rapid tournament
was on 29 October.

Article 1216 game 5 has blank player fields and an unknown result tag. Its
explicit official title identifies NI KADEK SUGIANINGSIH–Nguyen Hoang Yen,
Round 1 Board 5, with a Black win; its final move comment also records Black's
win. Those fields are recovered through one explicit policy override. The
original blank source player fields and unknown `sourceResult` remain in the
asset, alongside `metadataNote`.

The [2023 official program](https://www.wxf-xiangqi.org/images/2023_houston_wxc/Brochure_Content_11-231.pdf)
confirms the round dates and bilingual players. A 2023 men's rapid final record
incorrectly repeats `60+30` in its clock tag; its title explicitly says 快棋.
The downloader therefore rejects rapid/blitz markers in both the game-type
field and title, even when the clock tag appears classical.

For Phuong Trang record 1471, embedded PGN headers are unknown while the
database's structured record supplies the metadata. The original bulletin
independently confirms that structured record's 17 July 2023 date, Round 4
Board 1 and 1–0 result. Both forms are retained in the source manifest.

The 2017 and 2019 WXF archives were downloaded and inspected but **are not
preloaded**. Their individual records omit both clock and slow-game markers,
and the inspected original regulations do not specify a clock. Their exclusion
is deliberate; no classical clock was guessed. The 2019 public index also
omits men's Round 6. Historical domestic collections were not expanded in this
pack.

## Names and identity screening

The display/search mapping adds 28 sourced Chinese-name entries. The combined
preload contains **142 distinct source-name values**, including **141 values
with Chinese characters**. All 141 have readable Latin names and source links;
the one Latin-only source name already displays directly.

The 2023 program supplies the Vietnamese spellings and country-specific opponent
names. The 2022 official
[men's ranking](https://www.wxf-xiangqi.org/images/2022_Sarawak_17wxc_pairing_result_1028/2022_17wxf__m_ranking2.pdf)
and [women's ranking](https://www.wxf-xiangqi.org/images/2022_Sarawak_17wxc_pairing_result_1028/2022_17wxf__w_ranking.pdf)
resolve truncated names and additional opponents. The federation's
[bilingual title list](https://vietnamchess.org.vn/index.php/experience-news/xq-masters)
also confirms established spellings. Examples include:

| Source name | Readable display |
|---|---|
| 郑亚生 | Trenh A Sang; accented search alias Trềnh A Sáng |
| 阮明日光 | Nguyen Minh Nhat Quang |
| 阮黄燕 | Nguyen Hoang Yen |
| 黎氏金鸾 / 黎氏金銮 | Le Thi Kim Loan |
| 汪洋北 | Uong Duong Bac |
| 阮黄林 | Nguyen Hoang Lam |
| 何文进 | Ha Van Tien |
| 宗室日新 / 孙室日新 | Ton That Nhat Tan |
| 郭泽辉 | Tawee Danwirunhawanich |
| 布莱恩乔 / 布莱恩乔治 | Brian George A/L A S Anthony Dass |

Both canonical participants are screened against `data/curation-policy.json`
using exact identities. Vietnamese Uong Duong Bac (汪洋北) is distinct from
Chinese Wang Yang (汪洋); a shared substring is not an identity match. Known
source spelling variants are normalized explicitly, while original source
fields and titles remain available. Screening excludes known notice-name
matches; it does not certify the absence of every possible misconduct history.

## Reproduction and verification

```powershell
python scripts/download_vietnamese.py
python scripts/download_vietnamese.py --offline
# Review a candidate without changing bundled assets:
python scripts/download_vietnamese.py --offline --fetch-only
& scripts/test-core.ps1
```

The downloader uses bounded public WXF pages, retries, one-second request
spacing and a local ignored `.cache/vietnamese` directory. Every round page's
SHA-256 and byte count are in `data/vietnamese-manifest.json`. It rejects
nonstandard starts, partial records, unresolved results, conflicting clocks,
rapid/blitz markers and curation matches, and deduplicates against both earlier
packs and the new collection.

The four attributed factual Trenh records are stored in
`data/futa-trenh-2023.json`; `data/futa-trenh-manifest.json` contains original
viewer/bulletin/regulations hashes and the snapshot hash. The independent
source-notation conversion can be reproduced with
`python scripts/extract_futa_trenh.py` after acquiring the source files recorded
in that manifest into its documented local cache. It requires the listed JDK
and extracted bulletin text. No source PDF, HTML, commentary, board code or
photography is shipped in the APK.

The final new asset SHA-256 is
`accaccb5dcaf91e5b14208417b4a6b5008fd9b361584108d787c5ac981a20e51`.
An offline candidate reproduces the asset bytes exactly. The full core audit
passed 170 rules/import/engine assertions, 27 date-sort assertions, 244 appearance
assertions, 452 name assertions, and replay of all **398 games / 33,629 legal
half-moves**. The official Windows Pikafish audit independently matched Java
legal moves at **1,623 positions**, with **1,597 successful searches** and
26 terminal positions. Its scope is desktop protocol/rules validation, not
Android ARM64 execution.

Only attributed factual moves and basic metadata are bundled. Source-site
rights in annotations, page design and other materials are not transferred to
the app, and no general redistribution license for those materials is asserted.
