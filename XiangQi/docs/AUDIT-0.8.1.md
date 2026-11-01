# Xiangqi Study 0.8.1 audit

Audited on 2026-10-31. Version code 27. Base: `bacb7f2` (0.8.0 Play / Database / Options).

## Regressions fixed

- Human, Engine and My Games search/sort keys no longer depend on the last selected import. Each named import retains its own scope; older saved keys have a migration fallback.
- Leaving Database before the search debounce completes saves the last typed query. Pending searches and callbacks from detached widgets cannot overwrite another screen or collection. Scroll restoration retains a pending search reset.
- Returning from the importer reads its selected collection before rebuilding the database header. Source reloads preserve the current query and wait before populating the imports selector.
- Deleting the currently reviewed personal game immediately invalidates its Return action. Undo restores that action. A delayed delete completion no longer navigates away from the screen the user subsequently selected.
- Finished-game screens survive activity recreation. Rotating a game started from a source position resumes the game instead of reapplying the original setup intent.
- An invalid external starting FEN shows a recoverable error instead of crashing the launcher. Valid Xiangqi positions remain supported.
- Older pinned launcher shortcuts targeting MainActivity still work and now open HomeActivity. Internal viewer/import routes retain their existing behavior.

The scope/search, completed-screen, stale Return action, malformed-FEN crash and old-shortcut failure were reproduced before their fixes. Negative-run logs are retained separately from passing runs. Test assertions now report on the instrumentation thread rather than killing the app's UI thread.

## Verification

- Successive Android 12 emulator rounds passed 14 and 16 checks. The final current-code run passed **17 tests in 132.845 seconds**: all 16 ComputerGameFlowTest methods plus the repository/import smoke test. Its raw result is `test-results/audit-0.8.1-release-ui.txt`.
- Real Windows Pikafish with the production Java adapter and matching NNUE: **64 assertions passed**. Coverage includes levels 1/25/50/75/100, maximum-depth two-second hints, both sides, MultiPV, network reuse/repair, bounded timeouts and repeated cancellation without leaked engine children.
- Core checks passed: **170** main, **62** MultiPV, **27** date-sorting, **252** appearance-preset, **100** analysis-visual and **728** player-name/search assertions.
- All **796 games / 76,445 half-moves** independently replayed legally. The nine asset packs, their manifests and Gemini's game annotations remain unchanged.
- The external legacy MAIN/LAUNCHER intent opened HomeActivity successfully. No crash-buffer entries were recorded after the final test run began; earlier intentional failure records were preserved.

Passing-run logs, negative reproductions and build output are retained in the local ignored `test-results` folder. Existing screenshots and playing behavior are documented in [0.8.0](AUDIT-0.8.0.md) and [Play flow](PLAY-FLOW.md).

## Delivery gate and limits

At the user's request, the source is committed and pushed before the delivery build. The final native build is `scripts/build.ps1 -Check`: clean assembly, lint, test-APK assembly, latest official Pikafish release/network verification, game-pack hashes, licenses and APK signature checks. Only a successful verified build replaces `dist/XiangqiStudy-latest.apk`; older delivery APKs are removed. Final build output and APK hash are recorded locally after the push.

The emulator uses the labelled built-in fallback. Windows native tests do not execute Android ARM64 machine code, and a physical phone was not connected for this audit. Playing levels are not calibrated Elo ratings. These checks cover observed regressions and important playing flows; they do not establish the absence of every possible bug.
