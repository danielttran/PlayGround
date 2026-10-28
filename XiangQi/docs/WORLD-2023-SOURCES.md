# World Championship 2023 adult classical collection

This pack contains **107 published men's full-game records / 9,362 half-moves**
from the 18th World Xiangqi Championship in Houston. It examines every published
adult round, rather than ranking or choosing the "best" games. The user's current
collection preference omits the 49 women's records by their official source-page
division. All 107 remaining IDs, moves and prior metadata are unchanged.

The earlier Vietnamese-focused pack contributed 16 of the retained men's records.
Those IDs and exact move fingerprints remain mandatory in the policy; the 14
women's legacy IDs are retained separately as `retiredLegacyRecords`, with their
original move fingerprints and the reason for removal. The broader men's pack
therefore adds **91 records** beyond those 16 preserved games.

## Official sources and scope

- The [official game-record index](https://www.wxf-xiangqi.org/index.php?Itemid=313&id=1592&lang=en&option=com_content&view=article)
  links nine adult men's rounds and seven adult women's rounds. All sixteen
  pages were fetched again for this expansion. Youth links are outside the pack.
- The [official regulations](https://www.wxf-xiangqi.org/images/2023_news_upload/2023_Entry_form_for_18th_WXC_.pdf),
  section VII(b), English PDF page 6, establish **60 minutes plus 30 seconds per
  move** for the adult main championship. Most records repeat `60+30` or
  `1h+30''`. Two full records omit their individual clock; they use the documented
  main-event clock and retain the empty `sourceTimeControl` with an explicit note.
- The [official program](https://www.wxf-xiangqi.org/images/2023_houston_wxc/Brochure_Content_11-231.pdf),
  schedule page 13, confirms men's round dates of November 20–24 and women's
  round dates of November 20–23. Its bilingual roster supplies the player identities.
- Seven official pairing/result images independently support metadata repairs
  and the complete women's result audit. Their URLs and freshly downloaded byte
  hashes are recorded in `data/world-2023-manifest.json`; no source images or PDFs
  are bundled in the app.

The adult archive has **176 source slots**. The current pack excludes the 49
women's source slots plus twenty other slots: nine byes,
one unrecorded slot, nine classical games involving a name on the existing
curation exclusion list, and one explicitly rapid final tiebreak. The rapid record
also matches that list. It incorrectly repeats `60+30`, so filtering checks the
explicit rapid marker in its title as well as the clock. This pack is the eligible
published subset, not every result or game played at the tournament.

## Metadata checked and repaired

Source text is retained in `sourceRed`, `sourceBlack`, `sourceResult`, `sourceDate`
and `sourceTitle`. Corrections have explicit notes and evidence URLs:

| Source record | Correction and independent evidence |
|---|---|
| 1590 game 13 | Duplicate Japan player fields replaced by **Low Yi Hao–Shoshi Kazuharu**, as explicitly named in the game title and official Round 1 result image. |
| 1636 game 1 | Blank players restored as **Wu Zonghan–Wong Hok Him**, confirmed by the title and official Round 4 result image. |
| 1637 game 7 | Duplicate Japan player fields replaced by **Olivier Tessier–Sone Toshihiko**, confirmed by the title and official Round 5 result image. |
| 1709 game 8 | Duplicate Chinese Taipei player fields replaced by **Mian Wu–Hsieh Wenyu**, confirmed by the title and official Round 8 result image. |
| 1594 game 1 | `2013/11/20` corrected to **2023-11-20**, independently confirmed by the official program and women's Round 1 result image. |
| 1710 game 6 | Unknown result tag resolved as a **Black win** from the same record's final-move result and the official Round 9 Board 5 result. |
| 1710 game 12 | Unknown result tag resolved as a **Red win** from the same record's final-move result and the official Round 9 Board 11 result. |

Two men's titles put 台 (board) in the round position. The round comes from the
official round page and the actual board comes from the last explicit board
ordinal, preserving the malformed title and a note. The rapid final inserted in
the Round 9 page therefore does not shift later board numbers.

Before the user's men-only preference, all **49 women's pairings and outcomes**
were compared independently with the
[final official seven-round cross-table](https://www.wxf-xiangqi.org/images/2023_houston_wxc_pair_results/18wxc02womenr7result.jpg).
The numeric opponent/cumulative-point transcription is in the policy; the
earlier downloader checked every admitted pairing and reconstructed each
per-game result from those cumulative points. All agreed with the game records.
That historical transcription remains source evidence, while these records are
now excluded from the bundled pack.

The roster supplies five additional readable identities: Radtai Lokutarapol,
Mian Wu, Olivier Tessier, Robin Hii Lu Bin and Suratsada Promsirinimit. The display
mapping recognizes the source spelling variants 谢汶瀚 / 谢汶谕 (Hsieh Wenyu) and
麻生太郎 / 麻生达郎 (Aso Tatsuro). Original source spellings remain available;
these aliases do not silently rewrite source metadata.

## Reproduce and verify

```powershell
python scripts/download_world_2023.py
python scripts/download_world_2023.py --offline
python scripts/download_world_2023.py --refresh
```

Requests use a bounded cache, retry/size limits and one-second pacing. The new
policy stores the sixteen retained men's legacy source-record IDs and exact move
fingerprints, plus fourteen retired women's legacy entries, so reproduction does
not depend on those games remaining in the old pack.
Changing a previously bundled move sequence fails the build step for review.

The previous 156-game pack was independently replayed using the app's production
Java `Position.play`: all 13,636 half-moves were legal. The current 107-game pack
is an exact subset: comparison with that prior asset confirms all retained IDs,
moves and metadata are unchanged. Its 9,362 half-moves have unique main-line
fingerprints, and offline reproduction preserves the exact asset bytes.

Only factual main-line moves and event metadata are included. Source annotations,
board code, photographs and PDFs are excluded. WXF retains rights in its original
pages; no blanket redistribution license is asserted.
