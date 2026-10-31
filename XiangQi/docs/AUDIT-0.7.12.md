# Xiangqi Study 0.7.12 collection curation audit

Date: 2026-10-31. Version code: 21.

## Result

The preload contains 633 men's classical games / 59,797 half-moves. Twenty-five records ending before numbered move 20 were removed from the preceding 558-game library; 100 selected master encounters were added. Existing retained IDs and move lists remain unchanged. The new pack includes 15 Vietnamese-player encounters. See [MASTER-CLASSICS.md](MASTER-CLASSICS.md) for source selection and historical-metadata limits.

The shared minimum-length rule counts a numbered Red/Black pair as one move: 38 or fewer half-moves is excluded, while a record reaching Red's move 20 (39 half-moves) stays. Each source converter records exclusions and reproduces the resulting asset. Source-policy hashes, admitted per-round counts and manifests were refreshed. The rule does not filter private imports.

## Checks performed

- Production Java replay accepted all 633 games / 59,797 half-moves.
- All seven human packs reproduced exactly from cached attributed records and pinned policies; no duplicate full main lines or IDs. No current excluded-name matches. Every bundled record passes the minimum-length guard.
- The 219 shortlisted new records replayed legally before bounded Pikafish screening. The selected 100 retain their search evidence, source hashes and selection policy.
- Official Windows Pikafish and production Java agreed on legal moves at 408 sampled positions from the new 100-game pack, including three terminal cases. This is a focused data/protocol check, not Android ABI execution.
- Core checks passed: 170 rules/import/engine/cancellation assertions, 62 MultiPV assertions, 27 date-sorting assertions, 252 appearance assertions, 100 analysis-visual assertions and 504 name-mapping assertions.
- Native `scripts/build.ps1 -Check` completed Android compilation, lint and Android-test APK compilation. Device instrumentation was not run for this data-focused release, and the user's phone was not changed.
- Production preBuild and payload verification resolved the latest official stable Pikafish release online: `Pikafish-2026-09-06`. Its matching NNUE SHA-256 is `7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.
- APK contents, all game asset hashes, native engine/network bytes and upstream notices passed verification. APK Signature Scheme v2 verification passed. The normal Android 12 ARM64 delivery was produced.

## Delivery

`dist/XiangqiStudy-latest.apk`, 52,141,605 bytes.

SHA-256: `2cb423a3e44a998285c0683ecb67d895b8f8dd149ef7c13093fecd375d96e69c`.

The verified build atomically replaced the previous public APK. Only the latest public Xiangqi Study APK remains in `dist`. The Engine tab still has no bundled engine-match archive; these additions are human master games.
