# Xiangqi Study 0.7.13 audit and SD-card delivery

Date: 2026-10-31. Version code: 22.

## Fix found during audit

Five bundled source-correction notes contain explicit outcomes. Version 0.7.12 displayed these notes in Game information even while Hide game results was enabled, bypassing the intended spoiler boundary. Raw source notes now appear only when recorded results are explicitly shown. Stored results, source metadata and exports remain unchanged; the final move-record row still deliberately reveals the result.

A regression check uses the real 2025 World Championship correction record `wxf-2025-men-r8-g9`. It verifies the dialog hides both the correction prose and result when spoilers are hidden, restores both when results are enabled, and preserves the original recorded outcome.

## Audit results

- All 633 bundled games / 59,797 half-moves legally replayed through production Java.
- All seven packs reproduce exactly from attributed source records and policies, with no duplicate IDs or complete main lines.
- Compared with the original 558-game Git revision: exactly 25 short records removed, exactly 100 additions, and all 533 retained IDs/move lists preserved. Every delivered game reaches numbered move 20.
- Core checks passed: 170 rules/import/engine/cancellation assertions, 62 MultiPV assertions, 27 date-sorting assertions, 252 appearance assertions, 100 analysis-visual assertions and 504 name assertions.
- Android 12 emulator: all six `ReplayLayoutAuditTest` cases passed in 89.316 seconds, covering conditional analysis, fixed-board lazy scrolling, dense move/engine columns, deliberate result footer, clear event-year labels, responsive replay controls and the new source-note spoiler regression.
- The first instrumentation request was issued before emulator user storage finished unlocking; Android refused to start the normal app with `Package org.xiangqi.study is not encryption aware`. After confirming RUNNING_UNLOCKED, the retry passed all six cases. No direct-boot or device-security setting was changed to bypass this condition.
- Portable emulator audit build and final native ARM64 build both compiled successfully. Android lint reported no issues. The portable APK was used only on the emulator.
- Production preBuild and final payload verification checked the latest official stable Pikafish release online: 2026-09-06, with matching NNUE SHA-256 `7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`. APK Signature Scheme v2 verification passed.

## Delivery

Final file: `dist/XiangqiStudy-latest.apk`, version 0.7.13 / 22, 52,141,633 bytes.

SHA-256: `6168199a1a71f3e87a397b3c65b086b420b3e0531fe046503faf2588db0ff67a`.

The public APK publisher replaced the previous APK atomically; only the latest public build remains in `dist`. The verified APK was copied over the authorized USB connection to the Moto g stylus (2022), SanDisk SD card, `Download/XiangqiStudy-latest.apk`. A staged copy was hash-checked before replacing the previous destination. Final phone size and SHA-256 matched the PC exactly. Android's file index returned the correct filename, 52,141,633-byte size and APK MIME type after scanning the exact destination. No app was installed or personal phone settings changed.
