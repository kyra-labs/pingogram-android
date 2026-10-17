# Release process

## Versioning

- Semantic versioning. `versionName` = tag without `v` (e.g. `v0.2.0` → `0.2.0`).
- `versionCode` = `MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100 + ABI_INDEX` where
  `ABI_INDEX` is 0 universal, 1 armeabi-v7a, 2 arm64-v8a, 3 x86_64 — so per-ABI APKs have
  distinct, ordered codes and the universal APK is lowest. Implemented in
  `build-logic` (`AndroidApplicationConventionPlugin`), reading the version from the Gradle
  property `-Pversion=` (set by CI from the tag) with a fallback `0.0.0-dev` locally.
- Pre-releases: `v0.x.y` (anything before 1.0) and `-beta.N`/`-rc.N` suffixes are marked
  as pre-release on GitHub.

## Signing

- **Debug**: `app/debug.keystore` committed (password `android`, alias `androiddebugkey`) and
  set explicitly on the `debug` signing config, so contributors get consistent debug signatures.
- **Release**: never committed. Locally, `keystore.properties` in the repository root (git-ignored):

  ```properties
  storeFile=/absolute/path/pingogram-release.jks
  storePassword=…
  keyAlias=pingogram
  keyPassword=…
  ```

  A relative `storeFile` is resolved against the repository root.

  In CI the workflow decodes `SIGNING_KEYSTORE_BASE64` to `$RUNNER_TEMP/release.jks` (outside the
  checkout) and passes its path and the other values as environment variables
  (`SIGNING_KEYSTORE_PATH`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
  `SIGNING_KEY_PASSWORD`). The Gradle signing config (`build-logic`,
  `AndroidApplicationConventionPlugin`) uses the environment when all four variables are set,
  otherwise `keystore.properties` when all four keys are present, and is simply absent otherwise.
  Without it, configuration, debug builds and `./gradlew check` still work, but any release APK or
  bundle task (`assemble*Release`, `bundle*Release`, `package*Release`) fails before anything runs,
  with a message naming the missing variables or keys. Passwords are never printed.

- Use **v2 + v3 (+ v4) APK signatures**; `enableV1Signing = false` (minSdk 26).

## Artifacts

| File | Description |
|---|---|
| `pingogram-X.Y.Z-universal.apk` | all ABIs, for most users |
| `pingogram-X.Y.Z-arm64-v8a.apk` | 64-bit ARM (most phones since 2017) |
| `pingogram-X.Y.Z-armeabi-v7a.apk` | 32-bit ARM (old devices) |
| `pingogram-X.Y.Z-x86_64.apk` | emulators / Chromebooks |
| `SHA256SUMS.txt` | checksums for all of the above |
| `pingogram-X.Y.Z.aab` | Play bundle (workflow artifact only, not attached to the release) |
| `mapping.txt` | R8 mapping (workflow artifact, 90 days) |

Default flavor for GitHub releases is `foss`. A `gms` build is produced only if the owner
enables it in the workflow input.

## Release checklist

1. `main` is green; `CHANGELOG.md` has an `## [Unreleased]` section with everything since the
   last tag.
2. Open a PR `chore(release): vX.Y.Z` that:
   - moves `Unreleased` items under `## [X.Y.Z] - YYYY-MM-DD`,
   - updates the comparison links at the bottom of `CHANGELOG.md`.
3. Merge, then tag the merge commit: `git tag -a vX.Y.Z -m "vX.Y.Z" && git push origin vX.Y.Z`.
4. `release.yml` builds, signs, checksums, extracts the changelog section and publishes the
   GitHub Release. Pre-release if version < 1.0.0 or has a suffix.
5. Install the universal APK on at least two devices (one Android 8–10, one 14+), run the
   smoke list in `docs/TESTING.md`, and if anything is wrong delete the release (keep the tag
   history clean by releasing a patch instead of re-tagging).
6. Announce (README "Download" badge updates automatically via shields.io "latest release").

## Hotfix

Branch from the tag (`git checkout -b hotfix/X.Y.Z+1 vX.Y.Z`), fix, PR to `main`, cherry-pick
if `main` has diverged, tag from the hotfix branch.

## Play Store (later)

The same workflow uploads the `.aab` as an artifact. When the owner is ready, add
`r0adkll/upload-google-play` (or Gradle Play Publisher) behind a secret
`PLAY_SERVICE_ACCOUNT_JSON`; keep GitHub as the canonical release channel.
