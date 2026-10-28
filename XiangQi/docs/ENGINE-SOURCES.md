# Engine collection sources

Reviewed on **2026-10-28**. The [source policy](../data/engine-source-policy.json)
records inclusion rules and the state of each source. A source lead is not a
shipped game pack: complete moves and metadata must be inspected first.

The requested built-in collection is now specifically **2026 tournaments with
verified top dedicated-engine participants**. No qualifying complete-game pack
has been verified, so this build ships no built-in engine games. Older events,
new local self-play and unspecified training corpora are not substituted.

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
