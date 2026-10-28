# Xiangqi Study

A native Android app for replaying Xiangqi games, exploring alternatives, and analyzing positions offline. Version **0.6.0** adds a slim vertical evaluation bar and configurable analysis continuations, with arrows for each candidate move and its reply. Analysis stays enabled as you move through games. The app includes 398 classical games, 24 board styles and 22 Chinese-text piece finishes.

The starter library contains **398 recorded classical games and 33,629 half-moves**: 285 adult 2025 World Championship games, 52 selected master games from the 1991–2003 World Championships, and 61 additional Vietnamese international games from 2022–2023. The recent collections use **60 minutes plus 30 seconds per move**. Every historical record explicitly marks slow/classical play; its exact clock is shown only when recorded. See [2025 sources](docs/SOURCES.md), [historical sources](docs/HISTORICAL-SOURCES.md) and the [new Vietnamese collection audit](docs/VIETNAMESE-SOURCES.md).

The library includes **23 Lai Ly Huynh games, 17 Nguyen Thanh Bao games and 7 Trenh A Sang games**, alongside the requested Singapore players and other masters. The new collection adds 14 Huynh games, 8 Bao games and 4 Trenh games. Counts overlap when these players meet. The historical collection includes Hu Ronghua, Li Laiqun, Zhao Guorong, Lü Qin, Xu Yinchuan and Wu Kui-lin. Both participants are screened against the dated curation policy; source manifests document exclusions and evidence. Three incorrect source dates were corrected against the official schedule, with originals retained for review. Unverified 2017/2019 clocks remain excluded.

## Use the app

- Search by player, event, round or collection. All bundled Chinese name variants have sourced romanized display names; search also accepts original Chinese names and aliases. Source names remain intact for export.
- Choose **Newest first**, **Oldest first** or **Collection order**. Partial and unknown dates stay below complete dates in either date order; no missing month/day is invented.
- All matching games appear in one scrollable library, with search and sorting
  covering every game. Long move records show a window around the current
  position, with the move controls and numeric position dialog available for the complete game.
- Red and Black have separate labelled player rows in the library and game. A small dot on the board edge marks the side to move. Swipe right from the game to the library; swipe left to return to the current game, or use **Return to current game**. Your position and variation are restored.
- The five replay buttons are **Previous game, Next game, Play, Previous move, Next move**, in that order. Previous/Next game follow the library's search and sort order and restore each game's saved position or variation. Play toggles autoplay. The horizontal move-progress slider has been removed.
- Open the top-right **Game menu** for **Flip board**, **Start analysis / Stop analysis**, **Go to move…**, **Play against engine**, **Export game** and **Settings**. **Go to move…** accepts a half-move number from **0** (the starting position) to the end of the current record or variation.
- Choose **Start analysis** for an evaluation, suggested move, depth, and continuation. **Positive values favor Red; negative values favor Black**, regardless of whose turn it is.
- Analysis stays enabled until you choose **Stop analysis**. Every new position restarts analysis, including replay, autoplay, suggested moves, variations and switching games. Reaching a depth/time limit keeps the toggle enabled. Search pauses in the library, during engine play or while the app is in the background, then resumes when you return to study. This choice survives app recreation.
- The slim evaluation bar beside the board follows the same Red/Black perspective and flips with the board. It is invisible when analysis is off, with its space reserved so the board never resizes. While waiting for a new evaluation it is neutral; an old position's score is cleared.
- Choose **1–5 analysis lines** in Engine settings. Each continuation draws its candidate move and the opponent's reply when available, using the respective side's arrow color and line number. The arrows update during search and show only the first two legal moves of each continuation. Played moves use a separate arrow instead of highlighted squares.
- Pieces are slightly larger, with extra clearance between each Chinese character and its inner rim. Move and analysis arrows are thicker and 50% transparent; their numbered labels stay opaque.
- **Stop analysis** clears the continuation arrows and hides the evaluation bar without resizing the board. The text keeps the latest estimate, marked **Analysis off · last estimate**; changing position clears it.
- Enter engine play through the **Game menu**. For free variation editing, use **Explore suggested move** after analysis, or **Stop playing** during an engine game; the board and legal-move list then accept your alternatives. **Return to recorded game** appears only while exploring a variation. There is no separate play-entry button below the replay controls.
- Choose **Game menu → Play against engine**, then Red or Black, to play from the current position. The computer replies with legal moves. It uses the time-per-position setting, or 3 seconds per move when analysis is untimed. **Stop playing** keeps your variation; moving through the game or leaving the app cancels a pending reply and restores normal variation editing. The recorded source game is preserved.
- Use **Import games** to choose a file or paste text. Use **Game menu → Export game** to save the recorded game or current variation as ICCS PGN.
- **Settings** hides recorded results by default, including accessibility announcements. Engine evaluations stay visible, and PGN exports retain the actual result.
- Choose **24 board presets** and **22 Chinese-text piece finishes**, with Traditional/Simplified characters. New matched pairs are **Warm maple**, **Neon violet**, **Antique gold** and **Carved honey**. Their wood grain, glow, patina, rims and raised-disc effects are drawn in Canvas; no website URL is drawn in the river. Existing paper, maple, lacquer, ivory/coral, golden wood, jade, porcelain and dark palettes remain. The [appearance sources](docs/APPEARANCE-SOURCES.md) distinguish the supplied/inspected references from our original drawings. No international-symbol pieces are offered.
- Custom RGB colors set the board, Red piece backgrounds and Black piece backgrounds independently. **The latest selection takes effect:** a board preset replaces the custom board color; a piece preset replaces both custom piece backgrounds; a custom color replaces only its selected surface. Selecting the same preset again also restores its colors. Opening settings or changing coordinates, characters or result visibility preserves unrelated colors.
- Toggle **Off**, **ICCS** or **Numeric** coordinates. Labels fit the existing board rim, so enabling coordinates does not shrink the grid or pieces.
- **Engine settings** controls search depth, time, analysis lines, threads, and hash memory. Native defaults are depth 128, untimed until stopped, one analysis line, all available CPU cores, and RAM-aware hash allocation. Saving settings restarts analysis when it is enabled. The built-in engine is limited to depth 64 and is substantially weaker.

The delivered ARM64 APK bundles **Pikafish 2026-09-06** and its matching NNUE, the latest official stable release verified for this version. Every native build checks the official release online and verifies the executable, network and upstream license files; failure stops the build. The app itself runs analysis offline. The internal portable emulator build uses the smaller built-in engine. The displayed engine name identifies the engine actually used. Pikafish receives the complete position history and applies its engine rules; neither engine mode is a complete WXF match arbiter.

There is no `INTERNET` permission, analytics, account, or cloud analysis. Imports and saved study positions are stored in the app's private directory. Export records you want to keep before uninstalling; uninstalling removes private app data. Opening a source link deliberately hands that link to an external browser.

## Install on an Android 12 ARM64 phone

The delivery file is **`dist/XiangqiStudy-latest.apk`**. Copy it to the phone, open it, and allow installation from the selected file manager when Android prompts. Install over the existing app to retain imported games and saved positions. Alternatively, with USB debugging and Android platform tools configured:

```powershell
adb install -r dist/XiangqiStudy-latest.apk
```

The app's minimum Android version is 8.0/API 26; native Pikafish requires ARM64 and Android 10/API 29 or newer. Android 12/API 31 meets that requirement. Each verified delivery atomically replaces the latest APK, then removes older Xiangqi Study APKs from `dist`. Portable audit APKs stay under `test-results/apk-audit` and are not delivered as phone builds. See [the 0.6 audit](docs/AUDIT-0.6.md) for the exact checks performed and their device coverage.

## Build and check

Run commands from `C:\Dev\PlayGround\XiangQi`. Install a JDK 17 or newer, Android SDK platform/build tools 35, Python 3.11+ and 7-Zip. Set `JAVA_HOME` to the JDK and configure the SDK with `ANDROID_HOME` or an untracked `local.properties` file containing `sdk.dir=...`. The wrapper uses **Gradle 8.11.1**, with **Android Gradle Plugin 8.9.3**. Native builds require network access for the official Pikafish release check; first builds also download Gradle dependencies.

```powershell
.\scripts\test-core.ps1
.\scripts\build.ps1 -Check
```

The build script cleans, builds, verifies APK contents and signature, and publishes the single `dist/XiangqiStudy-latest.apk`. Raw Gradle output remains at `app/build/outputs/apk/debug/app-debug.apk`. Direct Gradle native builds also run the latest-release guard. To prepare an internal x86 emulator audit with the built-in engine:

```powershell
.\scripts\build.ps1 -Portable -Check
```

Native ARM64 runtime testing requires a compatible phone or ARM64 Android environment. The Windows protocol smoke is a separate check:

```powershell
python scripts/test-pikafish.py --engine C:\path\to\Pikafish-Windows-x86-64-universal.exe
```

Place the matching official `pikafish.nnue` beside that executable, or pass `--network`. This check sends the same UCI initialization/history commands and parses the actual engine output with the production Java parser; it does not execute Android code.

See [the 0.6 audit](docs/AUDIT-0.6.md) for current validation, known limits and screenshots. The [0.5.2 audit](docs/AUDIT-0.5.2.md), [0.5.1 audit](docs/AUDIT-0.5.1.md), [0.5 audit](docs/AUDIT-0.5.md), [0.4 audit](docs/AUDIT-0.4.md), [0.3 audit](docs/AUDIT-0.3.md), [0.2 audit](docs/AUDIT.md) and [initial 0.1 validation](docs/VALIDATION.md) remain historical evidence and do not describe the current APK.

## Import formats

Supported formats are **ICCS-coordinate Xiangqi PGN** and the normalized JSON format used by `app/src/main/assets/games.json`. ICCS uses `a0` at Red's lower-left corner. For example:

```pgn
[Event "Personal study"]
[Red "Player"]
[Black "Opponent"]
[Collection "Personal games"]
[Variant "Xiangqi"]
[Result "*"]

1. b2e2 b9c7 *
```

PGN comments and side variations are skipped; the main line is imported. A `FEN` tag can supply a custom starting position. The optional `Collection` tag is retained on import and export. Every imported move is checked before anything is saved. An invalid record rejects the complete import; duplicate move records are skipped. The file and saved import library limits are 5 MB.

A JSON pack is an array of game objects, or an object with a `games` array. Each game needs `moves`, a list of ICCS strings; supported metadata includes `event`, `round`, `date`, `red`, `black`, `result`, `sourceUrl`, `startFen`, `timeControl` and `collection`. Results are `1-0`, `0-1`, `1/2-1/2` or `*`; a known numeric `timeControl` uses seconds, such as `3600+30`.

Binary XQF/CBR/CBL files, WXF move notation, and Chinese move notation require conversion before import. They are not supported import formats in this version.

## Refresh the starter library

The downloader uses Python 3.10+ and the standard library:

```powershell
python scripts/download_wxf.py
python scripts/download_wxf.py --offline
python scripts/download_wxf.py --refresh
python scripts/download_historical.py
python scripts/download_historical.py --offline
```

The 2025 downloader fetches the official index and 16 adult round pages. The historical downloader uses the bounded page selection and exclusions in `data/historical-policy.json`. Both cache requests, use bounded retries and request spacing, and apply the curation policy. `app/src/main/assets/game-packs.json` lists the separate bundled packs and their source manifests. Review [SOURCES.md](docs/SOURCES.md) and [HISTORICAL-SOURCES.md](docs/HISTORICAL-SOURCES.md), then revalidate every regenerated move sequence before shipping a replacement library.

## Credits and code layout

Game records: [World Xiangqi Federation](https://www.wxf-xiangqi.org/index.php?Itemid=101&id=1788&lang=en&option=com_content&view=article). Each bundled game retains its source URL. Attribution does not establish a blanket WXF redistribution license.

Native engine: [official Pikafish](https://github.com/official-pikafish/Pikafish/releases/tag/Pikafish-2026-09-06), derived from Stockfish. Engine code is GPL-3.0; the NNUE weights carry a separate upstream license that requires permission for commercial use. Exact release source pointers, hashes, and notices are under `third_party/pikafish` and in the app's engine notices. The parent repository license does not replace third-party licenses.

[ARCHITECTURE.md](docs/ARCHITECTURE.md) describes the app, storage, import boundary, engine process, and known limitations.
