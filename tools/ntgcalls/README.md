# NTgCalls patches

`.github/workflows/ntgcalls.yml` applies these to NTgCalls (`NTGCALLS_COMMIT`) with `git apply`, in name order, before
it builds the AAR, and names its release after them (`NTGCALLS_PATCHES`). Why: `docs/adr/0025-conference-calls.md`.

- `0001-external-conference-encryption.patch`: conference calls whose blockchain and encryption TDLib keeps
  (`NTgCalls.initExternalConference`, `io.github.pytgcalls.ExternalEncryption`).

To change one: check NTgCalls out at `NTGCALLS_COMMIT`, apply the patches, edit, and write them again with
`git add -N . && git diff > …/tools/ntgcalls/0001-….patch`. Then bump `NTGCALLS_PATCHES` in the workflow and the
suffix of `ntgcalls` in `gradle/libs.versions.toml` together, run the workflow, and pin the new AAR's SHA-256
(`ntgcallsAarSha256`).
