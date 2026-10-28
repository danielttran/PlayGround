# Xiangqi Study

A native Android app for replaying international Xiangqi games, exploring alternatives, and analyzing positions offline. The dark green board and library follow the supplied visual references.

The starter library contains **285 complete recorded games and 23,340 half-moves** from the adult 2025 World Xiangqi Championship. The official classical time control was **60 minutes plus 30 seconds per move**. These are championship records, rather than a claim that all participants are the world's highest-ranked players or hold grandmaster titles.

The men's final, Yin Sheng–Lai Ly Huynh, comes first. Games by the requested players are prioritized: Lai Ly Huynh, Nguyen Thanh Bao, Wu Zonghan, Yin Sheng, Meng Fanrui, Tang Dan, and Chen Lichun. The remaining records stay available in the library. The source archive may not contain every game by these players. [Source and curation evidence](docs/SOURCES.md) explains provenance, time-control checks, excluded records, and the limits of matching notice names to participants.

## Use the app

- Search the library by player, event, or round; tap a game to open it.
- Step through moves, drag the replay slider, autoplay, or flip the board. The app remembers replay and variation positions.
- Tap **Analyze** for a Red-perspective evaluation, suggested move, depth, and continuation. Positive centipawns favor Red; negative values favor Black.
- Tap **Play from here** to explore a variation using the board or legal-move list. Return to the recorded game whenever needed.
- Use **Import games** to choose a file or paste text. Use **Export** to save the recorded game or current variation as ICCS PGN.

The ARM64 build bundles **Pikafish 2026-09-06**, which runs locally. The portable emulator build uses the smaller built-in classical engine. The displayed engine name identifies the engine actually used. The built-in engine has limited search strength and does not adjudicate tournament repetition or perpetual-chase rules. Pikafish receives the complete position history and applies its engine rules; neither mode is a complete WXF match arbiter.

There is no `INTERNET` permission, analytics, account, or cloud analysis. Imports and saved study positions are stored in the app's private directory. Export records you want to keep before uninstalling; uninstalling removes private app data. Opening a source link deliberately hands that link to an external browser.

## Install on an Android 12 ARM64 phone

The prepared debug APK is placed at `dist/XiangqiStudy-0.1.0-android12-arm64.apk`. Copy it to the phone, open it, and allow installation from the selected file manager when Android prompts. Alternatively, with USB debugging and Android platform tools configured:

```powershell
adb install -r dist/XiangqiStudy-0.1.0-android12-arm64.apk
```

The app's minimum Android version is 8.0/API 26. The bundled native APK requires ARM64; an x86 emulator uses a separate build without the native library. Phone installation and ARM64 engine execution remain unverified until tested on the actual device.

## Build and check

Run commands from `C:\Dev\PlayGround\XiangQi`. Install a JDK 17 or newer, Android SDK platform 35, and Android build tools. Set `JAVA_HOME` to the JDK and configure the SDK with `ANDROID_HOME` or an untracked `local.properties` file containing `sdk.dir=...`. The checked-in wrapper uses **Gradle 8.11.1**, with **Android Gradle Plugin 8.9.3**. The first Gradle build downloads its dependencies.

```powershell
.\gradlew.bat assembleDebug lintDebug
.\scripts\test-core.ps1
```

The ordinary build produces `app/build/outputs/apk/debug/app-debug.apk`. The `dist` file is a convenient copy, rather than a separate build type. For an x86 emulator build with the built-in engine:

```powershell
.\gradlew.bat -PwithoutNative assembleDebug assembleDebugAndroidTest
```

Native ARM64 runtime testing requires a compatible phone or ARM64 Android environment. The Windows protocol smoke is a separate check:

```powershell
python scripts/test-pikafish.py --engine C:\path\to\Pikafish-Windows-x86-64-universal.exe
```

Place the matching official `pikafish.nnue` beside that executable, or pass `--network`. This check sends the same UCI initialization/history commands and parses the actual engine output with the production Java parser; it does not execute Android code.

Verification: 146 core assertions, initial-position perft `44 / 1920 / 79666`, legal replay of all 285 games/23,340 half-moves, Windows Pikafish protocol checks, and four passing Android 12 device tests. Both clean builds pass lint with no issues; APK signatures and exact bundled engine/network hashes are verified. See [validation and screenshots](docs/VALIDATION.md) for evidence and device-specific limits.

## Import formats

Supported formats are **ICCS-coordinate Xiangqi PGN** and the normalized JSON format used by `app/src/main/assets/games.json`. ICCS uses `a0` at Red's lower-left corner. For example:

```pgn
[Event "Personal study"]
[Red "Player"]
[Black "Opponent"]
[Variant "Xiangqi"]
[Result "*"]

1. b2e2 b9c7 *
```

PGN comments and side variations are skipped; the main line is imported. A `FEN` tag can supply a custom starting position. Every imported move is checked before anything is saved. An invalid record rejects the complete import; duplicate move records are skipped. The file and saved import library limits are 5 MB.

A JSON pack is an array of game objects, or an object with a `games` array. Each game needs `moves`, a list of ICCS strings; supported metadata includes `event`, `round`, `date`, `red`, `black`, `result`, `sourceUrl`, `startFen`, and `timeControl`. Results are `1-0`, `0-1`, `1/2-1/2`, or `*`; `timeControl` uses seconds, such as `3600+30`.

Binary XQF/CBR/CBL files, WXF move notation, and Chinese move notation require conversion before import. They are not supported import formats in this version.

## Refresh the starter library

The downloader uses Python 3.10+ and the standard library:

```powershell
python scripts/download_wxf.py
python scripts/download_wxf.py --offline
python scripts/download_wxf.py --refresh
```

It fetches the official index and 16 adult round pages with caching, bounded retries, and request spacing. It excludes rapid/youth links and records without moves, checks the recorded time control, and applies `data/curation-policy.json`. See [SOURCES.md](docs/SOURCES.md) before changing the curation or adding another event. Revalidate every regenerated game sequence before shipping a replacement library.

## Credits and code layout

Game records: [World Xiangqi Federation](https://www.wxf-xiangqi.org/index.php?Itemid=101&id=1788&lang=en&option=com_content&view=article). Each bundled game retains its source URL. Attribution does not establish a blanket WXF redistribution license.

Native engine: [official Pikafish](https://github.com/official-pikafish/Pikafish/releases/tag/Pikafish-2026-09-06), derived from Stockfish. Engine code is GPL-3.0; the NNUE weights carry a separate upstream license that requires permission for commercial use. Exact release source pointers, hashes, and notices are under `third_party/pikafish` and in the app's engine notices. The parent repository license does not replace third-party licenses.

[ARCHITECTURE.md](docs/ARCHITECTURE.md) describes the app, storage, import boundary, engine process, and known limitations.
