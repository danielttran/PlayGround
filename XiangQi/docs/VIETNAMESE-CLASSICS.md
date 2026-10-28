# Vietnamese masters classical games, 2009 and 2016

This pack adds **36 complete men's games / 3,112 half-moves**, with no repeated
complete mainlines from the other shipped packs. It contains 18 selected games
from the 2009 Phuong Trang Vietnam Open Swiss stage and 18 main classical games
from the 2016 Phuong Trang Masters knockout/classification stages.

These older events supply games by Nguyen Vu Quan, Nguyen Thanh Bao, Nguyen
Hoang Lam, Dao Cao Khoa, Trenh A Sang and the young Lai Ly Huynh. The selection
uses event strength, direct opposition and tournament performance. It does not
claim an engine ranking of the "best" games. For example, it includes Dao Cao
Khoa's win against Liu Dahua, Nguyen Hoang Lam's wins against Liu Dahua and Zhang
Qiang, and Dao Cao Khoa's win against Wu Guilin.

## Sources and clocks

The [official 2009 event article](https://vietnamchess.vn/index.php/chessnews/tournament-news/tournament-international/open-inter-events/puta3)
names the leading men's entrants, gives the final standings and every Swiss
round's opponents, colors and results, and publishes the round calendar.
Regulations section 3 specifies **80 minutes plus 30 seconds per move** for the
nine-round Swiss. All 2009 records in this pack are from that Swiss stage and use
`4800+30`. Its later knockout and rapid/blitz games are outside the selection.

The [official 2016 event regulations](https://vietnamchess.vn/news/phuongtrang2016)
section 4.2 specifies **60 minutes plus 30 seconds per move from move one** for
the two main games of each knockout/classification match. All 2016 records in
this pack use `3600+30`. Group games at 40+20 and Armageddon games at 6/5 minutes
are excluded. The federation's published
[quarterfinal/classification results](https://vietnamchess.vn/images/stories/saigon/2016/vongtuket.jpg),
[semifinal/final results](https://vietnamchess.vn/images/stories/saigon/2016/vongbanket.jpg)
and [knockout bracket](https://vietnamchess.vn/images/stories/saigon/2016/vongchungket.jpg)
independently match the admitted players, match stages and results. Six main
games involving Zheng Yihong / 郑一泓 / Trịnh Nhất Hoằng are excluded by the
shared player policy.

Full recorded moves and structured metadata come from the federation's
[public XQBase](https://vietnamchess.vn/databank/xqbase-vietnamchess). Every asset
record links directly to its public game viewer. The source PGN headers often
contain unknown values; the app uses the structured viewer metadata, checked
against the event's results and calendar, instead of inventing PGN headers.
The source's `board_num` is an event-wide record sequence, so it is retained as
an audit field and is not presented as the physical board number.

## Reproduction and validation

`data/vietnamese-classics-policy.json` pins all 39 reviewed candidates, their
normalized source-fact hashes, viewer/listing metadata, sections and clock
evidence. `scripts/download_vietnamese_classics.py` fetches only public game
viewer pages, with at most three requests in parallel, and keeps them in the
ignored `.cache/vietnamese-classics` folder.

Run `python -X utf8 scripts/download_vietnamese_classics.py --check` to reproduce
and compare both shipped outputs. Add `--refresh` to re-fetch all 39 pinned
records. A source-fact change fails validation for manual review. A JDK is
required; `--java-bin` can select its directory.

The script uses the existing Vietnamese/WXF notation matcher with the app's
production `Position.legalMoves()` and `Position.play()`. It retains every source
notation token and verifies each complete source half-move count. When an
individual token permits more than one legal move, subsequent recorded moves
must resolve it to exactly one **whole mainline**. Two complete legal mainlines
prove ambiguity and are rejected; an exploration limit or zero legal mainlines
fails the run. No guessed moves, branch truncation or notation repair is used
to admit a game.

Three candidates remain outside the shipped pack. Source games **2376** (Nguyen
Vu Quan–Nguyen Tran Do Ninh) and **2386** (Lai Ly Huynh–Liu Dahua) each permit
two complete legal interpretations of its recorded WXF notation. Their source
names and reasons remain in the policy and generated manifest. Source game
**2325** has a player-identity conflict: the structured viewer calls the Hong
Kong entrant Triệu Nhữ Quyền / 赵汝权 / Chiu Yu Kuen, while the official final
standings and Swiss cross-table call him Chan Keung On / 陳強安. The same article
guest roster uses Triệu Nhữ Quyền, so no player-name correction is guessed.

The generated manifest records every admitted mainline's SHA-256, final FEN and
half-move count, together with the pack hash and current shared curation hash.
Exact full mainlines are compared against every other shipped collection and
against this pack. Every included player's exact Chinese source name is checked
against `data/curation-policy.json`.

Chinese source spellings and original Vietnamese/bilingual names are retained.
For example, 蒙世行 is called **Mong Nhi** in both the official 2009 final
standings and the structured viewer; the app preserves that supported name.
Current player-profile team affiliations are omitted because they can differ
from historical affiliations.
