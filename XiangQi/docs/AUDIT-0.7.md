# Xiangqi Study 0.7.0 — separate collections and replay speed

Date: 2026-10-28. Version code 9. Delivery: `dist/XiangqiStudy-latest.apk`.

## Behavior

Human and Engine tabs contain only bundled records of that type. My imports
selects one named import from a dropdown; its label includes Human/Engine and
game count. Every explicit import asks for a name and classification, validates
the whole batch and creates a separate collection. The same game can belong to
different imports or also exist in a bundled pack. Duplicates within one batch
are removed. Source metadata is preserved separately from user collection names.

Each collection remembers search, date/collection sorting and scroll. Replay
navigation stays inside its collection. Visiting another tab and returning to
the current game restores its original collection, saved position and ordering.

Legacy imports migrate into Imported games without changing their game IDs,
preserving saved positions and variations. Schema 2 commits both collection
descriptors and games atomically. Stale repository instances reload the latest
snapshot under the existing process-wide lock. Failed batches do not create
empty collections or partially overwrite existing data.

Replay speed accepts 0.5–60 seconds between moves, defaults to one second and
persists independently of engine search time. A changed speed reschedules a
pending replay tick. Pause, backgrounding, navigation and the final move stop
playback. Decimal comma and Unicode decimal digits are normalized for input.

Analysis off removes the evaluation rail and its margin, expanding the board
to the available row width. Analysis on allocates a 16 dp rail and 4 dp gap.
The rail matches the board height at narrow and normal widths. Coordinates
continue using the existing rim; moves and turn changes do not resize the board.

## Engine collection boundary

The user requested 2026 tournaments with verified top dedicated-engine entrants.
No qualifying downloadable game archive has been verified. The inspected Kaka
thread documents cumulative unbalanced-position ranking tests; its hidden
attachments may be rating images. It is not established as a tournament archive.
The Engine tab therefore has an explicit empty state in this build. No older
events, fresh self-play, training positions or variants are substituted.

Forum email verification was requested through normal registration and retried
once after delivery failed. Account creation and authenticated inspection remain
pending the verification email. Private email/CAPTCHA evidence is ignored under
test-results and is not published. See [ENGINE-SOURCES.md](ENGINE-SOURCES.md).

## Verification

The first focused Android 12 emulator gate passes **30 tests in 81.919 seconds**:
repository migration/isolation/atomicity, collection controls and import flow,
replay speed, Canvas rendering, evaluation perspective and board width changes.

Host core checks pass: 170 existing rules/parser/engine assertions, 62 MultiPV,
27 date sorting, 244 appearance, 100 analysis visuals and 452 player names.
All 398 human games and 33,629 half-moves replay legally. The corpus validator
now honors an optional starting FEN in its TSV input.

The initial broad emulator run exposed an overly strict existing assertion:
a finite three-second fallback search can complete depth 1 and return three
root-only candidates, so six arrows cannot be required. The UI check now
requires all available candidate/reply moves, while the dedicated rendering
tests still require both sides when replies are supplied. No engine replies
are fabricated to satisfy a timing-dependent test.

The final complete Android 12 emulator run passes **75 tests in 339.312 seconds**
after that test correction and the final resource/lint cleanup. It includes
returning to an imported game after visiting another collection, with its query,
sort, position and navigation scope restored. The earlier broad run completed
75 tests in 377.628 seconds with only the assertion failure described above;
the failure is retained under ignored test-results.

Clean portable and production builds pass. Android lint reports **no issues**.
The production build verifies **Pikafish 2026-09-06**, the latest official stable
release at build time, and its matching NNUE SHA-256
`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.
The build checks upstream before compilation and again during final APK payload
verification. The engine payload and human game packs are unchanged.

Final APK: **52,000,817 bytes**, SHA-256
`66deae88ba72aa0091096a0e45f8b436e253d79f8c9ee0223ea43ca0d9c40a2d`.
APK payload hashes and the v2 signature pass. Only
`dist/XiangqiStudy-latest.apk` remains in the delivery folder; the prior build
was replaced atomically. Internal portable audit APKs are not phone deliveries.

Interactive tests run only on emulator-5554. No tests or automatic installation
run on the connected personal phone. Emulator fallback checks do not establish
native ARM64 execution; the native adapter is unchanged from the prior build.

## Screenshots

Fresh Android 12 emulator captures were visually inspected:

- [Human library](../screenshots/07-human-library.png), with all 398 bundled games.
- [My imports](../screenshots/07-my-imports.png), showing two actual example
  World Championship games imported into a user-named Vietnam study collection.
  This demonstration exists only in emulator storage and is not preloaded.
- [Import name and type](../screenshots/07-import-setup.png).
- [Replay speed](../screenshots/07-replay-speed.png).
- [Full-width board with analysis off](../screenshots/07-board-analysis-off.png).
