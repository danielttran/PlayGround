# Selected classical master games

Reviewed 2026-10-31 for Xiangqi Study 0.7.12.

## Delivered collection

The new pack contains 100 complete master-versus-master games / 12,146 half-moves, spanning 1980–2018 and 26 event labels. Fifteen encounters involve Vietnamese players. The pack includes Hu Ronghua, Liu Dahua, Lu Qin, Xu Yinchuan, Li Laiqun, Zhao Guorong, Nguyen Thanh Bao, Trenh A Sang and Lai Ly Huynh. Original Chinese player identities remain in the data; the UI provides registered readable spellings or identified Mandarin transliterations.

## Acquisition and selection

Source: [chasoft/community-xiangqi-games-database](https://github.com/chasoft/community-xiangqi-games-database), pinned to commit `31c663f575c357bf96e878e2b8af0cd7b83f4906`. All six forks and their available branches were inspected: default branches share this commit, while the original/okeyba `v2` branch adds tooling with identical game data. No newer fork game collection was found.

A bulk Git-object download avoided individual viewer exports and Windows-incompatible source filenames. Source code was not executed. The complete tournament tree contains 104,950 score files. A name/event shortlist led to 219 qualified candidates after exact player exclusions, normal starting positions, complete legal replay, explicit 慢棋 markers, classical clock checks, full recorded dates/rounds and main-line deduplication. Records labelled classical but carrying short or conflicting clocks were held out. Instructional chapters, partial positions, exhibition/blindfold events and source annotations were not admitted.

Pikafish 2026-09-06 screened the complete recorded histories at plies 20, 40, 60, three quarters and the end, omitting duplicate sample indices. Each sample used 50,000 nodes, one thread and 64 MB hash. Early advantages greater than 250 normalized centipawns at ply 20 or 40 and endings needing further verification were held out. Selection preferred Vietnamese encounters, historic masters, recorded clocks and competitive early play, with at most 15 games per source event, six per pairing and 45 draws. The delivered 100 contain 55 decisive games and 45 draws. This supports study selection; it is not exhaustive accuracy grading or an objective best-game ranking.

The clock is explicitly recorded as 60+30 in 66 selected records and 60+20 in six. Twenty-six records explicitly mark classical play but omit the exact clock. Two contain the ambiguous raw clock `60 30`, retained without interpreting its fields. No missing clock is invented.

## Provenance and limits

Each selected original blob was checked against its downloaded SHA-256 before pinning its factual tags and full move list. Source paths, Git blob IDs and byte hashes remain in `data/master-classics-records.json`. `data/master-classics-policy.json` records the shortlist, selection rule, held-out cases and readable event names. `data/master-classics-engine-review.json` stores the bounded search samples, executable/network hashes and search settings. The asset and manifest reproduce from those pinned facts through `scripts/build_master_classics.py --check`.

Names are normalized to reviewed identities. Source names, title, event, round and dates remain available in the factual records. Dates are parsed and checked against the tournament-folder year; numbered rounds and recorded match-game numbers are retained. These community transcriptions are attributed, not independently certified historical records. Game information exposes the source note and a link to the exact pinned game. No comments, diagrams, instructional prose or viewer implementation are copied into the APK; no general license over the source collection is asserted.

## Existing-library cutoff

`data/collection-length-curation.json` records all 25 removals from the previous 558-game preload: 11 World Championship 2025 games, eight World Championship 2023 games and six Phuong Trang Masters 2025 games. A numbered move is a Red/Black pair: records with 38 or fewer half-moves end before move 20 and are omitted; 39 half-moves reaches Red's move 20 and remains. The rule applies regardless of result and is enforced by every source converter and APK/source checks. The other 533 game IDs and move lists remain unchanged. Private imports are untouched.
