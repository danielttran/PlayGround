# Xiangqi Study 0.7.8 audit

Date: 2026-10-31. Version code 17. Baseline: 0549cb5 (0.7.7).

## Reproductions and fixes

- At 200% font size, the selected appearance label's rendered text exceeds its
  fixed-height view. The corrected bounds check fails against 0.7.7 in 10.53 s.
  Arrow rows now grow with their text, retaining a 56 dp minimum and 48 dp buttons.
- A 160 dp settings panel gives its fixed preview all the available space,
  leaving no full arrow touch target. This reproduces in 7.027 s against 0.7.7.
  Preview size now follows the actual measured panel height/width and reserves
  72 dp for controls. Extremely short panels give preview space to the controls.
- Rapidly replacing color and game-information dialogs lets the old dismissal
  clear the new dialog reference. Both reproduce against 0.7.7. Dismissal now
  clears only the current instance. The same guard is applied to replay-speed,
  go-to, engine-side and engine-settings dialogs. Existing guarded import,
  legal-move, error and appearance-dialog cleanup remains intact.

An initial engine-dialog fixture read the dialog before Android's queued button
callback ran; that test was corrected to wait for UI idle. It is not counted as
an application defect. Final coverage includes the normal engine-settings
reopen flow and actual color/engine window dismissal on Activity destruction.
Cleanup assertions also wait for the UI thread to finish destruction before
reading window state; the framework's destroyed flag alone is not that barrier.

## Audit rounds

The first round reviews selection persistence, custom-color precedence and
dialog ownership. The second reproduces the concrete failures against the old
APK and checks the fixes. The third checks the affected settings together with
record layout, variations and analysis pause/resume across library/background
and Activity recreation. Source collections, rules and native adapter are
unchanged; full corpus evidence remains in the earlier audits.

All 18 targeted cases are verified across the final runs: 16 pass in the broader
137.891-second run, and the two cleanup cases pass in 18.214 seconds after fixing
their premature test reads. The cases comprise seven appearance/lifecycle,
five record-layout, four variation/modal and two persistent-analysis checks.
Lint reports No issues found. The emulator crash buffer is empty after the run.

Android interaction checks target emulator-5554 only, an Android 12 x86 instance
using the built-in engine. Native ARM64 engine and matching network bytes are
verified separately by the phone-build gates. No automatic installation or test
targets the personal phone.

## Delivery

The clean phone build succeeds in 25 seconds. Packaged version is 0.7.8 / code
17; lint and APK v2 signature verification pass. Both online gates confirm latest
official stable Pikafish 2026-09-06 and matching NNUE
`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.
All five bundled game assets compare byte-for-byte unchanged against 0.7.7:
428 games / 36,227 half-moves.

The verified APK replaces the older public build. Size: 52,033,688 bytes.
SHA-256: `abada6b8710254a68be7458e25f626efceb2c3c8b6ce943495910ffa383ba9d1`.
