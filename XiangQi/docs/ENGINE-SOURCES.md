# Engine collection sources

Reviewed on **2026-10-31**. The [source policy](../data/engine-source-policy.json)
records inclusion rules and the state of each source. A source lead is not a
shipped game pack: complete moves and metadata must be inspected first.

The user now accepts moderately older strong-engine matches as well as recent
tournaments, and specifically requested Kaka's league. The working search range
is 2020 onward, with established strength on both sides. No verified Kaka game
file has been obtained yet. The forum registration request has reached email
verification; no account or download access is claimed. New local self-play and
unspecified training corpora are not substituted.

## Preferred recent source

[Kaka's engine time league](https://bbs.pikafish.org/forum.php?mod=viewthread&tid=179&page=1)
publishes recent engine test results. The inspected September 2026 text names
Pikafish, Cyclone NN and Xiaochong NN, and documents a reference setup of four
AMD EPYC 9654 cores at **60 seconds plus 0.6 seconds per move**, using unbalanced
starting positions. Those reference conditions are not automatically metadata
for every downloadable game. The forum requires login to view its attachments;
no attachment game content has yet been verified. The public category marks
image attachments, so the login gate is not proof that game files exist. These
are cumulative unbalanced-position tests, not an established 2026 championship.

## Other leads

- [Dongping's public search](https://www.dpxq.com/hldcg/search/) loads its landing
  page, but actual keyword requests on 31 October 2026 return
  `AI爬虫机器人太多了,请先登陆一下棋谱仓库` (too many AI crawlers; please log in).
  Queries for Pikafish, Cyclone, computer events, NNUE and software events all
  produced the same gate. No recent NNUE game file or export was obtained.
  The supplied Gemini claim of an unauthenticated archive is not verified.
- The official [Pikafish Fishtest](https://test.pikafish.org/) frontend redirects
  to its login page with “Permission Denied, Please Login.” No public full-game
  export was available in that session.
- The [Fairy-Stockfish testing archive](https://github.com/fairy-stockfish/varfishtest-archive)
  contains test settings and aggregate results from 2017–2022, according to its
  maintainer's README. It does not establish a collection of full game scores.
  The separate `variantfishtest` repository is a match runner, not a supplied
  tournament archive. No local matches were generated as substitutes.

- [Official Pikafish](https://github.com/official-pikafish/Pikafish) links its
  [Fishtest server](https://test.pikafish.org/) as development testing
  infrastructure. A complete-game download has not been verified.
- [Pika Xiangqi Zero](https://github.com/official-pikafish/px0) is a GPU neural
  network Xiangqi engine. Its [server](https://px0.org/) reports self-play
  activity; the [Kaggle data](https://www.kaggle.com/datasets/pikacat/px0data)
  is credited by Pikafish as training data. That does not establish that the
  published files contain complete, importable games.
- A [NanAo versus Xiaochong recording](https://www.bilibili.com/video/BV1B94czJEYQ/)
  reports **40 minutes plus 20 seconds per move**, with a draw after 128 rounds.
  A move-file download, exact engine versions and hardware were not found.
- [Xiangqi Clash 2010](https://home.hccnet.nl/h.g.muller/XQC2010.html) has an
  accessible [all-games ZIP](https://home.hccnet.nl/h.g.muller/XQC10.zip). The
  inspected archive contains **516 PGN records**. Its SAN-style notation needs
  conversion before app import. It is held out of the requested recent pack.

## Pack boundary and verification

Built-in engine records use the existing normalized game JSON schema, with
`engineGame: true` on each record and on its pack-manifest entry. Keep original
engine names/versions, dates, round data, time controls and source URLs; leave
missing metadata unknown. A noninitial game must retain its exact `startFen`.
An engine match begun from an opening position is not presented as a game
played from the standard initial setup.

Before adding a pack, replay every move from its recorded starting position
with the Java core and audit representative positions with official Pikafish.
Deduplicate using both starting FEN and the complete main line. Record source
file and output asset hashes in the pack's manifest. Keep engine games apart
from the bundled human collection and from each named user import.

Shorter documented engine time controls are eligible. Human classical-game
curation rules remain attached to the human collections. Jieqi, Banqi and other
variants do not enter this standard-Xiangqi collection.

## Opening-book provenance

No current pack carries a verified engine book-exit boundary, and the app does
not label moves as book moves. A source's explicit per-move book annotations
or documented forced opening prefix can establish such a boundary; matching
an opening database or the engine's best move cannot. A future UI can use a
small Book badge in the move list and an Engine play divider at the first
calculated move. This is a proposed presentation, not a shipped feature.
For a score starting at a noninitial FEN, describe the preset starting position
without inventing the omitted opening moves. Leave unknown book use unknown.
