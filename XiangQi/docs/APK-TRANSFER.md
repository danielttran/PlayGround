# APK transfer to Android SD cards

On 31 October 2026, a manual File Explorer copy to the connected Moto g stylus
(2022) SD-card Download folder displayed transfer progress and then disappeared
without leaving the file. Android logged `Mtp receive file got error Operation
Canceled`, response `0x201F` to `MTP_OPERATION_SEND_OBJECT`, and removed the
incomplete object. The card was mounted with roughly 108 GiB available.

The source Explorer tab was stale: it displayed a 2:57 PM APK timestamp and
50,815 KB while the actual current file was modified at 3:52 PM and contained
52,088,748 bytes (50,868 KB rounded up). Refreshing **the source `dist` tab**
with F5 corrected those properties. Selecting and copying the refreshed APK
then successfully replaced the destination through normal Explorer/MTP copy.
The phone's SHA-256 matched the PC file exactly. The before/after result strongly
points to stale source metadata rather than a bad APK or insufficient space;
it does not establish that every possible MTP cancellation has this cause.

The build publisher atomically replaces `XiangqiStudy-latest.apk` to preserve a
previous delivery when a build fails. It now also sends Windows Shell
`SHCNE_UPDATEITEM`/`SHCNE_CREATE` and `SHCNE_UPDATEDIR` notifications with Unicode
paths and synchronous delivery, so open Explorer views can invalidate old file
metadata. See Microsoft's [SHChangeNotify documentation](https://learn.microsoft.com/en-us/windows/win32/api/shlobj_core/nf-shlobj_core-shchangenotify).

The patched delivery was exercised with a clean 14-second production rebuild.
The previously open source tab automatically updated to the new 4:04 PM
timestamp without F5; its size remained the correct 50,868 KB. Production
engine/network and APK signature checks passed. The rebuild was byte-identical
to the hash-verified APK already copied to the phone:
`1fd5ed48812593ec255c3074816696b531466117f010c239ce6954d0bb8eef52`.

For a manual copy after downloading or rebuilding, refresh the source folder
first, then select and copy the APK again. An already populated clipboard can
still refer to an older Shell item. Refreshing only the phone folder does not
correct stale source properties.

For the diagnostic recovery, the user also authorized a direct USB file copy.
The already-authorized ADB connection successfully copied the APK to the same
SD-card Download directory, independently of MTP. A scan of that exact file
updated Android's file index, and Explorer displayed the complete 49.6 MB APK.
Both that copy and the subsequent refreshed Explorer copy were verified on the
phone by file size and SHA-256. No app was installed, no phone security settings
were changed, and no storage reset, formatting, driver replacement or restart
was performed.

This is a file-delivery fix; the Android application remains version 0.7.11.
