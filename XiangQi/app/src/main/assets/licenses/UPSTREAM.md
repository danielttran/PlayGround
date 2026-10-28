# Bundled Pikafish

Unmodified official Pikafish **2026-09-06** release, tag `Pikafish-2026-09-06`.

- Release: https://github.com/official-pikafish/Pikafish/releases/tag/Pikafish-2026-09-06
- Exact corresponding source: https://github.com/official-pikafish/Pikafish/tree/Pikafish-2026-09-06
- Source archive: https://github.com/official-pikafish/Pikafish/archive/refs/tags/Pikafish-2026-09-06.zip
- Build instructions: https://github.com/official-pikafish/Pikafish/wiki/Compiling-from-source
- Download: `Pikafish.2026-09-06.7z` from that release.
- Release archive SHA-256: `41952bbfe2520faceb5902c69e6ab4845cc999841d2b49a95cc1be7867a25e5b` (verified against the GitHub release asset digest before extraction).
- Android executable SHA-256: `6c06b8752e10c1ed605fa836d2c9bbf885e9c402b216023040ddf4586f4320b1`.
- Network SHA-256: `7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`.

`Pikafish-Android-arm64-universal` is copied byte-for-byte to
`app/src/main/jniLibs/arm64-v8a/libpikafish.so`. The filename allows Android's
package manager to extract this executable into its executable native library
directory. It remains a standalone UCI process; it is not loaded with JNI.
ELF inspection confirms a static AArch64 `ET_EXEC` executable with no `PT_INTERP`
or dynamic-library dependency. Its Android identification note declares API 29,
NDK r27c (build 12479018). It is not a PIE shared library despite the packaging
filename. Native availability is guarded to Android 10/API 29 or newer. Android
12/API 31 satisfies this requirement; API 26-28 uses the Java fallback on a
compatible packaged ABI. The separate portable APK supports other ABIs.
`pikafish.nnue` is copied byte-for-byte to `app/src/main/assets/pikafish.nnue`.

The engine is GPL v3; see `Copying.txt` and `AUTHORS`. Its separately licensed
NNUE weights prohibit commercial use without permission; see `NNUE-License.md`.
This personal study build includes the notices in the APK as well as here.
The PlayGround MIT license does not relicense these third-party components.

The matching official release and archive digest were rechecked against the
upstream `/releases/latest` API on 2026-10-28; no newer stable release or changed
release package was published. The bundled network is the exact network in that
verified release archive.

Analysis settings select depth 12-128, a time budget or an untimed search, engine
threads and 64-512 MiB hash. Defaults search to depth 128 or until Stop, use all
available CPU cores, and choose 256 MiB hash on devices with at least 4 GiB RAM,
128 MiB otherwise, or 64 MiB on low-RAM devices. The official release advertises
no Skill Level or UCI_LimitStrength option; no handicap is applied. MultiPV is
one, dedicating search to the best continuation. Completed exact scores and
legal PVs stream to the UI while searching. Search strength still depends on
device resources and time; no guaranteed rating is claimed.

The network is copied to private app storage on first analysis. Its exact
length and SHA-256 are checked before installation and before reusing an
existing copy after process start or file metadata changes. Truncated or corrupt
copies are replaced atomically with the bundled network. No network permission,
account or remote analysis server is needed. The portable Java engine is
explicitly labelled and does not have grandmaster strength.
