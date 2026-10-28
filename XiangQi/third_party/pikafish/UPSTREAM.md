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
`pikafish.nnue` is copied byte-for-byte to `app/src/main/assets/pikafish.nnue`.

The engine is GPL v3; see `Copying.txt` and `AUTHORS`. Its separately licensed
NNUE weights prohibit commercial use without permission; see `NNUE-License.md`.
This personal study build includes the notices in the APK as well as here.
The PlayGround MIT license does not relicense these third-party components.

Runtime uses one engine thread and 32 MiB hash. The network is copied to private
app storage on first analysis. No network permission, account or remote analysis
server is needed. ARM64 devices use Pikafish; other ABIs use the explicitly
labelled lightweight Java engine. No engine strength rating is claimed for it.
