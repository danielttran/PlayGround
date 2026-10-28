# Ingenious Xiangqi games: complete-record subset

The requested **100 Ingenious Xiangqi Games** is **象棋鬼手百局**, compiled by
**Zhu Jianqiu (朱剑秋)**. The English identity is confirmed by the publisher's
[Microsoft Store description](https://apps.microsoft.com/detail/9nrkdm5tjhkl?gl=US&hl=en-US);
the public [numbered collection index](https://www.xiangqiqipu.com/Category/list-58.html)
identifies the Chinese title and author.

This optional import contains **68 real-game records / 5,552 half-moves**, all
from the ordinary initial position. Chapter numbers and gaps preserve their
relationship to the original 100. It is not registered in the app's preloaded
library. Import either `Ingenious-Xiangqi-full-games.json` or
`Ingenious-Xiangqi-full-games.pgn` into a separately named Human collection.
The searchable **Book game N** label refers to the original chapter, rather than
a tournament round. Original source round/event metadata remains in the JSON.

The initial download supplied 80 records starting from the normal board and
20 study excerpts. Two excerpts were matched to separately published complete
games: chapter **57** matches the last 32 half-moves of a 53-ply game ending in actual
checkmate; chapter **95** matches the last 25 half-moves of an 87-ply full game.
The precise board, side to move and every remaining main-line move agree.

The delivered set keeps records whose source reports resignation or a game end
at the final recorded move, or whose final board is actual checkmate. This is
source-reported completeness, not a claim that every game has an independent
tournament bulletin. **32 chapters remain held out:** unrecovered study
excerpts, explicitly omitted later moves, uncertain completion, conflicting
results, and one participant matching the existing curation exclusion policy.
The complete per-chapter decisions and source hashes are in `manifest.json`.

Chapter **89** had its third and fourth half-moves reversed. An independent
public full-game record corroborates the entire 90-ply sequence after swapping
only those two moves. The two sources disagree about the event; the usable
event field remains unknown. Chapter **95** retains only September 1988 because
public sources disagree about the exact day. Missing dates and rounds are left
unknown. **No game has a verified clock here**, so none is certified as classical
solely because it appears in this collection.

Names have Latin labels first. Existing sourced aliases are reused where they
match; other readings explicitly say `[Pinyin]`. The Chinese source labels are
retained in JSON and PGN metadata. A Pinyin reading does not establish a player's
preferred Latin spelling or silently repair a corrupt source name.

The files contain factual main-line moves and metadata, without the book's
annotations, side variations, diagrams or translated commentary. This is not a
copy of the paid Windows application or a redistributed book.

Run `python data/collections/ingenious/verify.py` from the XiangQi project to
repeat the production Java legal replay, JSON/PGN agreement and canonical
comparison against the current bundled human library. The original 100-record
audit found no exact duplicates, complete excerpt matches or later matching
continuations against the existing 398 games. The delivery audit repeats that
comparison after recovering the two complete games. Its detailed report is
written to the ignored `test-results/ingenious-delivery-audit.json`.
