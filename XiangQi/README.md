# Xiangqi Study

A native Android app for playing Xiangqi, browsing an offline database, and reviewing games with Pikafish. Version **0.9.1** adds **System / Light / Dark** display modes and **Cobalt / Amber / Plum / Graphite** app themes under Options. Cobalt Dark is the default. App colors are independent of board and piece colors. The existing **676 human games and 120 engine games / 76,445 half-moves**, including Gemini's annotations and tactical collection, are preserved.

The [0.9.1 audit](docs/AUDIT-0.9.1.md) covers themes, readable native controls and preservation of game/review state. The [0.9.0 audit](docs/AUDIT-0.9.0.md) covers the board-first redesign and review alignment. Each saved suggestion belongs to the move it evaluates, using the position before that move. Tap a suggestion to explore its alternative; return to the recorded game afterward. Long-press a played move to read its annotation. Older lifecycle fixes remain documented in the [0.8.1 audit](docs/AUDIT-0.8.1.md).

**Play** starts a standard game or resumes an unfinished one. Choose Red, Black or Random. **Database** separates My Games, Human, Engine and named Imports; played games can be deleted with Undo. **Options** sets the difficulty for new games and the board's appearance. See [playing and review behavior](docs/PLAY-FLOW.md).

The library's Return to current game and Import games actions share one normal-color row. The active collection tab keeps its selected color; About & sources is removed from the header.

If an Explorer copy to a phone finishes but the APK disappears, see the [verified APK-transfer fix](docs/APK-TRANSFER.md). The publisher now notifies Windows Shell when replacing the APK; refreshing the source folder and copying the file again resolved the reproduced MTP failure.

The Engine tab includes the existing 120 Pikafish / Fairy-Stockfish NNUE games. This release preserves that pack and does not add or reclassify tournament sources. Earlier archive research remains documented in [engine source research](docs/ENGINE-SOURCES.md).

An optional [Ingenious Xiangqi complete-game collection](data/collections/ingenious/README.md) contains **68 games / 5,552 half-moves** from the requested *100 Ingenious Xiangqi Games*. It excludes unresolved partial/truncated records and follows the existing participant filter. All delivered games start normally; two full records were recovered from independent sources. Its acquisition audit found no duplicates or matching excerpts against the original 398-game preload. Import its [JSON](data/collections/ingenious/Ingenious-Xiangqi-full-games.json) or [PGN](data/collections/ingenious/Ingenious-Xiangqi-full-games.pgn), select Human games and name the collection. It remains separate in My imports. Dates/clocks are left unknown when unsupported, and the set is not certified as classical or preloaded.

The starter library contains **633 recorded men's classical games / 59,797 half-moves** across seven packs: 204 World Championship 2025 games, 99 World Championship 2023 games, 52 historical World Championship master games, 18 Vietnamese international games from 2022–2023, 36 Vietnamese classics from 2009/2016, 124 Phuong Trang Masters 2025 games, and **100 selected classical master games from 1980–2018**. The new set includes 15 Vietnamese-player encounters. Across all packs, Lai Ly Huynh appears in 40 games, Nguyen Thanh Bao in 34, Trenh A Sang in 15, and Andy Wo in 26; these counts overlap when they meet.

The minimum is **20 numbered moves**: a Red/Black turn pair is one numbered move, so 39 half-moves reaches Red's move 20 and stays; 38 or fewer half-moves is removed regardless of result. The rule applies to bundled collections, not private imports. Remaining IDs and moves are preserved. [Removal records](data/collection-length-curation.json) identify every omitted game.

The new study set is selected from legally replayed master-versus-master candidates in a pinned community database. Explicit classical markers are required, and conflicting short clocks are excluded. Bounded Pikafish history samples screen large early advantages and endings needing further review. These are selection checks, not exhaustive accuracy grades or a claim of the objectively best games. Dates and rounds follow attributed source transcriptions. Exact clocks are displayed only where recorded; otherwise the classical marker and uncertainty remain explicit. See [the master-game sources and selection](docs/MASTER-CLASSICS.md).

## Use the app

- Choose **Human**, **Engine** or **My imports** below Import games. Human and Engine contain the app's collections; My imports has a picker for each separately named import, including its Human/Engine classification and game count. Existing imports migrate to **Imported games**, preserving saved positions. Search, sorting, scroll and Previous/Next game navigation stay within the selected collection.
- Search by player, event, round or collection. Filtering and date sorting run on a dedicated background worker; typing is debounced by 120 ms, folded metadata/sort orders are cached, and obsolete query results are discarded. Each collection remembers its own search and sort choice. All bundled Chinese name variants have readable names with source or transliteration provenance; search also accepts original Chinese names and aliases. Source names remain intact for export.
- Choose **Newest first**, **Oldest first** or **Collection order**. Partial and unknown dates stay below complete dates in either date order; no missing month/day is invented.
- All matching games appear in one scrollable library, with search and sorting covering every game. The complete move record uses compact 38 dp rows, one per half-move, pairing the played move with its saved Pikafish alternative; scrolling it leaves the board in place. Analysis lines share this area in a separate right column. With analysis off, the record uses the full width.
- The replay header puts a compact **Library** button, the current game's labelled Red/Black players and the **Game menu** in one row. The board starts immediately below it. The library also labels each player's side. A small dot on the board edge marks the side to move. Swipe right from the game to the library; swipe left to return to the current game, or use **Return to current game**. Your position and variation are restored.
- The five replay buttons are **Previous game, Next game, Play, Previous move, Next move**, in that order. Previous/Next game follow the selected collection's search and sort order and restore each game's saved position or variation. Play toggles autoplay. Opening the legal-move or engine-side chooser pauses playback at that position; obsolete choices cannot change a later position. **Options → Replay speed** sets **0.5–60 seconds between moves** (default 1 second); opening Options pauses playback, and the choice survives app restarts. A compact progress slider directly below the board navigates the complete record or variation. Dragging updates the board while analysis waits until release; keyboard and accessibility changes also navigate.
- Open the top-right **Game menu** for **Flip board**, **Start analysis / Stop analysis**, **Go to move…**, **Play against engine**, **Export game** and **Settings**. **Go to move…** accepts a half-move number from **0** (the starting position) to the end of the current record or variation.
- Choose **Start analysis** for an evaluation, suggested move, depth, and continuation. **Positive values favor Red; negative values favor Black**, regardless of whose turn it is.
- Analysis stays enabled until you choose **Stop analysis**. Every new position restarts analysis, including replay, autoplay, suggested moves, variations and switching games. Reaching a depth/time limit keeps the toggle enabled. Search pauses in the library, during engine play or while the app is in the background, then resumes when you return to study. This choice survives app recreation.
- The slim evaluation bar beside the board follows the same Red/Black perspective and flips with the board. Analysis off removes the rail and gap so the board expands to the full available width. Analysis on uses a 16 dp rail and 4 dp gap. While waiting for a new evaluation it is neutral; an old position's score is cleared. Coordinates retain the same compact rim in both sizes.
- Choose **1–5 analysis lines** in Engine settings. Each continuation draws its candidate move and the opponent's reply when available, using the respective side's arrow color and line number. The arrows update during search and show only the first two legal moves of each continuation. Played moves use a separate arrow instead of highlighted squares.
- Pieces are slightly larger, with extra clearance between each Chinese character and its inner rim. Move and analysis arrows are thicker and 60% transparent (40% opacity); their numbered labels stay opaque.
- **Stop analysis** clears the continuation arrows, hides the evaluation bar and analysis panel, and expands the board. The analysis panel appears only while analysis is enabled outside engine play.
- While viewing a recorded game, **tap a piece and a legal destination** to try a variation. This works with analysis on or off and keeps the source moves intact. Touching the board pauses autoplay before you choose the destination. **Return to recorded game** restores the complete source line at the current half-move; enabled analysis follows both variations and the restored record. **Explore suggested move** and **Stop playing** also enter variation editing, whose status opens an accessible legal-move list.
- Choose **Game menu → Play against engine** to start a separately saved personal game from the viewed position. Choose Red, Black or Random; the source record is preserved. Undo takes back your latest move and any computer reply, cancelling pending work. Surrender saves the result. Hint runs the unrestricted engine for two seconds and displays one suggested arrow; it does not enable live evaluation.
- Use **Import games** to choose a file or paste text, name the new collection and select Human games or Engine games. Each import adds one picker entry in My imports. It never merges with built-in collections or previous imports; the same game can belong to different imports. Duplicate records inside one batch are skipped. Use **Game menu → Export game** to save the recorded game or current variation as ICCS PGN.
- **Settings** hides recorded results in library cards and game information by default, including accessibility announcements there. Unstructured source-review notes are shown only when recorded results are enabled, because those notes can contain outcomes. The final row after all recorded moves deliberately reveals the result, including for accessibility. Variations show an unfinished label. Engine evaluations and exported results remain available.
- Choose **24 board presets** and **16 Chinese-text piece finishes**, with Traditional/Simplified characters. New matched pairs are **Warm maple**, **Neon violet**, **Antique gold** and **Carved honey**. Their wood grain, glow, patina, rims and raised-disc effects are drawn in Canvas; no website URL is drawn in the river. Board palettes remain available; retired piece finishes have been removed. New piece pairs are Ruby and snow, Scarlet and cobalt, Sunset and navy, and Garnet and mint. The [appearance sources](docs/APPEARANCE-SOURCES.md) distinguish the supplied/inspected references from our original drawings. No international-symbol pieces are offered.
- Custom RGB colors set the board, Red piece backgrounds and Black piece backgrounds independently. **The latest selection takes effect:** a board preset replaces the custom board color; a piece preset replaces both custom piece backgrounds; a custom color replaces only its selected surface. Selecting the same preset again also restores its colors. Opening settings or changing coordinates, characters or result visibility preserves unrelated colors.
- Toggle **Off**, **ICCS** or **Numeric** coordinates. Labels fit the existing board rim, so enabling coordinates does not shrink the grid or pieces.
- **Analysis always uses maximum strength**: depth limit 128, all available CPU cores and RAM-aware hash allocation. Live analysis runs until stopped or the maximum depth is reached. Hint and automatic review have a two-second per-position budget; their strength is not handicapped by the playing level. Only the number of displayed analysis lines is configurable. The portable emulator's built-in engine is substantially weaker and is identified in its UI.

The delivered ARM64 APK bundles **Pikafish 2026-09-06** and its matching NNUE, the latest official stable release verified for this version. Every native build checks the official release online and verifies the executable, network and upstream license files; failure stops the build. The app itself runs analysis offline. The internal portable emulator build uses the smaller built-in engine. The displayed engine name identifies the engine actually used. Pikafish receives the complete position history and applies its engine rules; neither engine mode is a complete WXF match arbiter.

There is no `INTERNET` permission, analytics, account, or cloud analysis. Imports and saved study positions are stored in the app's private directory. Export records you want to keep before uninstalling; uninstalling removes private app data. Opening a source link deliberately hands that link to an external browser.

## Install on an Android 12 ARM64 phone

The delivery file is **`dist/XiangqiStudy-latest.apk`**. Copy it to the phone, open it, and allow installation from the selected file manager when Android prompts. Install over the existing app to retain imported games and saved positions. Alternatively, with USB debugging and Android platform tools configured:

```powershell
adb install -r dist/XiangqiStudy-latest.apk
```

The app's minimum Android version is 8.0/API 26; native Pikafish requires ARM64 and Android 10/API 29 or newer. Android 12/API 31 meets that requirement. Each verified delivery atomically replaces the latest APK, then removes older Xiangqi Study APKs from `dist`. Portable audit APKs stay under `test-results/apk-audit` and are not delivered as phone builds. See [the 0.7.13 audit](docs/AUDIT-0.7.13.md) for the exact checks performed and their device coverage.

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

See [the 0.7.13 audit](docs/AUDIT-0.7.13.md) for current validation and known limits. The [2025 Phuong Trang source audit](docs/FUTA2025-SOURCES.md) documents eight omitted rapid, ambiguous or incomplete records. The [0.7.9 appearance audit](docs/AUDIT-0.7.9.md) and the [0.7.4 collection audit](docs/AUDIT-0.7.4.md) and [Vietnamese collection screenshot](screenshots/074-vietnamese-masters.png) describe the unchanged corpus. See the [0.7.3 screenshots](docs/AUDIT-0.7.3.md#screenshots) for the unchanged replay layout and direct variation controls. The [0.7.3 audit](docs/AUDIT-0.7.3.md), [0.7.2 audit](docs/AUDIT-0.7.2.md), [0.7.1 audit](docs/AUDIT-0.7.1.md), [0.7 audit](docs/AUDIT-0.7.md), [0.6 audit](docs/AUDIT-0.6.md), [0.5.2 audit](docs/AUDIT-0.5.2.md), [0.5.1 audit](docs/AUDIT-0.5.1.md), [0.5 audit](docs/AUDIT-0.5.md), [0.4 audit](docs/AUDIT-0.4.md), [0.3 audit](docs/AUDIT-0.3.md), [0.2 audit](docs/AUDIT.md) and [initial 0.1 validation](docs/VALIDATION.md) remain historical evidence and do not describe the current APK.

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

PGN comments and side variations are skipped; the main line is imported. A `FEN` tag can supply a custom starting position. The optional source `Collection` tag is retained on import and export, independently of the user-chosen import name. Every imported move is checked before anything is saved. An invalid record rejects the complete import; duplicate move records inside the same batch are skipped. Separate imports can contain the same game. The file and saved import library limits are 5 MB.

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
python scripts/download_world_2023.py
python scripts/download_vietnamese.py
python -X utf8 scripts/download_vietnamese_classics.py --check
```

The 2025 and 2023 downloaders examine their adult round pages and admit only the men's sections. The historical downloader uses the bounded selection in `data/historical-policy.json`. Regenerate the broader 2023 pack before the Vietnamese supplement, which deduplicates against it. Downloaders cache requests, use bounded retries and request spacing, and apply the curation policy. `app/src/main/assets/game-packs.json` lists the separate bundled packs and their source manifests. Review [2023 sources](docs/WORLD-2023-SOURCES.md), [2025 sources](docs/SOURCES.md) and [historical sources](docs/HISTORICAL-SOURCES.md), then revalidate regenerated moves before shipping a replacement library.

## Credits and code layout

Game records: [World Xiangqi Federation](https://www.wxf-xiangqi.org/index.php?Itemid=101&id=1788&lang=en&option=com_content&view=article) and the [Vietnam federation public database](https://vietnamchess.vn/databank/xqbase-vietnamchess). Each bundled game retains its source URL. Attribution does not establish a blanket WXF redistribution license.

Native engine: [official Pikafish](https://github.com/official-pikafish/Pikafish/releases/tag/Pikafish-2026-09-06), derived from Stockfish. Engine code is GPL-3.0; the NNUE weights carry a separate upstream license that requires permission for commercial use. Exact release source pointers, hashes, and notices are under `third_party/pikafish` and in the app's engine notices. The parent repository license does not replace third-party licenses.

[ARCHITECTURE.md](docs/ARCHITECTURE.md) describes the app, storage, import boundary, engine process, and known limitations.

Appearance settings use **‹ current choice ›** rows, wrapping from the last choice to the first in either direction. The pinned preview stays visible while the controls scroll. Arrow changes save immediately; board/piece preset changes replace custom colors only for their respective surfaces, while coordinate and character choices preserve custom colors.

Piece finishes now include **Ruby and snow**, **Scarlet and cobalt**, **Sunset and navy** and **Garnet and mint**. The ten retired piece IDs migrate safely to Classic; board presets with similar names are unchanged. **Move slide duration** offers Off, 100, 200, 300, 500 or 800 ms and is independent of replay speed. Opening a game and large position jumps settle immediately; normal forward/reverse moves and captures slide. Rapid navigation cancels the old slide and follows the newest position. Chinese character style appears directly below coordinates.
