# NTgCalls patches

`.github/workflows/ntgcalls.yml` applies these to NTgCalls (`NTGCALLS_COMMIT`) with `git apply`, in name order, before
it builds the AAR, and names its release after them (`NTGCALLS_PATCHES`). Why: `docs/adr/0025-conference-calls.md`.

- `0001-external-conference-encryption.patch`: conference calls whose blockchain and encryption TDLib keeps
  (`NTgCalls.initExternalConference`, `io.github.pytgcalls.ExternalEncryption`).
- `0002-export-only-jni-entry-points.patch`: `libntgcalls.so` exports its JNI entry points only, through a version
  script (`ntgcalls.map`): `Java_*`, one symbol per native method of the AAR's `classes.jar` and `libs/webrtc.jar`,
  which the runtime looks up by name, and `JNI_OnLoad`. Every other symbol is local, so `--gc-sections` drops what no
  entry point reaches (NTgCalls' own sources are compiled with `-ffunction-sections -fdata-sections` for it), and the
  relocations are packed (`--pack-dyn-relocs=android`, read by Android's loader from API 23). The workflow checks the
  result with `check-jni-exports.py`: no other export, no native method without its symbol, relocations packed.

To change one: check NTgCalls out at `NTGCALLS_COMMIT`, apply the patches, edit, and write them again with
`git add -N . && git diff > …/tools/ntgcalls/0001-….patch`. Then bump `NTGCALLS_PATCHES` in the workflow and the
suffix of `ntgcalls` in `gradle/libs.versions.toml` together, run the workflow, and pin the new AAR's SHA-256
(`ntgcallsAarSha256`).
