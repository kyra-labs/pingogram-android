# Release process

## Versioning

- Semantic versioning. `versionName` = tag without `v` (e.g. `v0.2.0` → `0.2.0`).
- `versionCode` = `MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100 + ABI_INDEX` where
  `ABI_INDEX` is 0 universal, 1 armeabi-v7a, 2 arm64-v8a, 3 x86_64 — so per-ABI APKs have
  distinct, ordered codes and the universal APK is lowest. Implemented in
  `build-logic` (`AndroidApplicationConventionPlugin`), reading the version from the Gradle
  property `-Pversion=` (set by CI from the tag) with a fallback `0.0.0-dev` locally.
- Pre-releases: `v0.x.y` (anything before 1.0) and `-beta.N`/`-rc.N` suffixes are marked
  as pre-release on GitHub. From 1.0.0 (2026-10-29) on, a version without a suffix is a full release, and the newest one
  is GitHub's "Latest"; the TDLib and NTgCalls releases stay pre-releases, so they never take it.

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
| `pingogram-X.Y.Z-<abi>-gms.apk` | the same four for the `gms` flavor (push notifications through Firebase) |
| `SHA256SUMS.txt` | checksums for all of the above |
| `pingogram-X.Y.Z-fossRelease.aab`, `-gmsRelease.aab` | Play bundles (workflow artifact only, not attached to the release); the `gms` one also alone as `pingogram-X.Y.Z-play`, the one to upload to Play (`docs/PLAY_STORE.md` §7) |
| `mapping.txt` | R8 mapping (workflow artifact, 90 days) |

The per-ABI APKs come from ABI splits configured in `AndroidApplicationConventionPlugin`. AGP cannot
build an app bundle while splits are on (https://issuetracker.google.com/402800800), so a Gradle
invocation that runs a `bundle*` task builds without splits, and `release.yml` builds the APKs and
the bundle in two separate invocations. Native code for other ABIs (32-bit x86, riscv64), which some
AndroidX libraries ship, is left out of every APK and the bundle, so devices with those ABIs are refused
at install instead of crashing when TDLib loads.

Every release builds both flavors: `foss` first, then `gms`, for which the workflow writes `google-services.json` from
the secret `GOOGLE_SERVICES_JSON_BASE64` (`docs/OWNER_SETUP.md` §8b), so `foss` never sees Firebase's configuration.
Without the secret the release fails before anything is published. `foss` stays the default download (the plain file
names); `gms` APKs carry a `-gms` suffix. Both share the application id and the signing key, so one replaces the other
on a phone with its data kept. Distributing `gms` is allowed by `LICENSE-EXCEPTION.md`
(`docs/adr/0010-gpl-additional-permission-for-play-services.md`).

## Channel uploads

Builds also go to the owner's two Telegram channels, posted by a bot that logs in over MTProto with Telethon
(`tools/ci/telegram_publish.py`), which lets a bot upload files of up to 2 GB; the Bot API refused files over 50 MB. The
bot is the one of `TELEGRAM_BOT_TOKEN`, an admin who may post in both channels (`docs/OWNER_SETUP.md` §4). Its session
lives in memory only; the token and the API hash are never printed, put on a command line or written to disk, and no
third-party action sees them. Telethon and cryptg are pinned with their hashes in `tools/ci/requirements-telegram.txt`
(`pip install --require-hashes`). Without the token or a channel's secret nothing is posted, so forks build as before.

- **Releases channel** (`TELEGRAM_CHANNEL_ID`, `release.yml`'s `releases channel` job, tags only, after the release job
  published the GitHub Release, from its assets; the uploads take about 2 minutes per 100 MB, some 15 minutes for the 8
  APKs, so they have a job of their own and never hold up the release or the Play upload):
  - **The announcement**, which notifies:
    - "Pingogram X.Y.Z" in bold, "Beta" under it for a pre-release, then the date of the version's CHANGELOG heading;
    - that CHANGELOG section as Telegram HTML: headings in bold, "•" bullets ("◦" nested), `code` and links kept,
      everything else escaped;
    - a link to the GitHub Release, with link previews off.

    It is one message. A message holds 4,096 characters: a section too long for it is left out, and "What's new:" links
    it in the tag's `CHANGELOG.md` instead (`…/blob/vX.Y.Z/CHANGELOG.md#xyz---yyyy-mm-dd`, GitHub's anchor of its
    heading).
  - **Then each APK as a document of its own**, silently, so only the announcement notifies:
    - the order is `foss` universal, arm64-v8a, armeabi-v7a, x86_64, then `gms` in the same order;
    - in Telegram they are named `pingogram-X.Y.Z-<arch>-foss.apk` / `-gms.apk`, while the GitHub Release keeps its
      names, which Obtainium, `SHA256SUMS.txt`, the README and the website rely on.

    Each caption gives:
    - the architecture and who it is for, and the build (FOSS, or GMS with push through Firebase Cloud Messaging);
    - the size in whole MB (1,048,576 bytes, as the size budgets count it);
    - the SHA-256, computed from the file and checked against `SHA256SUMS.txt`: a file that does not match is not
      posted, and the step fails;
    - the asset's download URL on the GitHub Release, in full. Captions show no link preview.

    No `SHA256SUMS.txt` is posted: each caption holds its file's checksum.
- **Debug channel** (`TELEGRAM_DEBUG_CHANNEL_ID`, `ci.yml`'s `debug channel` job, pushes to `main` only): one plain
  message (the short commit, its subject, the run), then the `foss` debug APKs of every ABI, universal first, under
  their built names, silently. No `gms`. Debug builds never go to the releases channel; without the debug secret they
  are not posted, and when it names the releases channel (the same value, or the same channel once the bot resolves
  both) nothing is posted and the job warns. Before 1.0.0 (#293), CI sent its debug APK to `TELEGRAM_CHANNEL_ID`.
- **Reposting** (`.github/workflows/telegram-post.yml`, run by hand): a tag and a channel (`debug` by default, or
  `releases`). It downloads that published release's APKs and `SHA256SUMS.txt`, reads the tag's `CHANGELOG.md` and
  posts exactly as `release.yml` does: to preview the format in the debug channel, or to post a release again after a
  failed post. Its `dry_run` prints the posts without sending them. A pull request that changes the uploader runs it as
  a dry run over the latest release, without secrets.
- Telegram's flood waits are waited out (up to 5 minutes, then retried once), with a short pause between posts. A file
  that fails does not stop the rest; the step fails at the end. Both channel steps are `continue-on-error`: a Telegram
  outage or a wrong secret never fails a build or a release.
- `python3 tools/ci/telegram_publish.py release --version X.Y.Z --dist <dir> --changelog CHANGELOG.md --repo
  owner/name --dry-run` prints every message and caption locally, without secrets, network or Telethon. Its tests
  (`python3 -m unittest discover -s tools/ci -p 'test_*.py'`) run in CI.

## Release checklist

1. `main` is green; `CHANGELOG.md` has an `## [Unreleased]` section with everything since the
   last tag.
2. On the owner's phone, signed in as `docs/PERFORMANCE.md` §9 describes, run the benchmarks on `main` and check them
   against the budgets and the last baseline:

   ```bash
   ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
     -Pandroid.testInstrumentationRunnerArguments.notClass=dev.kyralabs.pingogram.benchmark.BaselineProfileGenerator
   python3 tools/benchmark/compare.py benchmark/build/outputs/connected_android_test_additional_output/
   ```

   A budget miss or a time more than 10% above the baseline stops the release until it is fixed, or until the owner
   accepts it in the release PR (a budget change is an ADR, `docs/PERFORMANCE.md` §9). After the release, record the
   run as the new baseline: `compare.py … --write-baseline benchmark/baselines/<device>.json --commit vX.Y.Z`, committed
   with the next change.
3. Open a PR `chore(release): vX.Y.Z` that:
   - moves `Unreleased` items under `## [X.Y.Z] - YYYY-MM-DD`,
   - updates the comparison links at the bottom of `CHANGELOG.md`.
4. Merge, then tag the merge commit: `git tag -a vX.Y.Z -m "vX.Y.Z" && git push origin vX.Y.Z`.
5. `release.yml` builds, signs, checksums, checks the APK sizes against `docs/PERFORMANCE.md` §1 (arm64-v8a ≤ 50 MB,
   universal ≤ 123 MB; over budget fails the release before anything is published), extracts the changelog section
   (followed by a link to `docs/API_TERMS.md`) and publishes the GitHub Release. Pre-release if version < 1.0.0 or has
   a suffix.
6. Install the universal APK on at least two devices (one Android 8–10, one 14+), run the
   smoke list in `docs/TESTING.md`, and if anything is wrong delete the release (keep the tag
   history clean by releasing a patch instead of re-tagging).
7. Announce (README "Download" badge updates automatically via shields.io "latest release").

## Hotfix

Branch from the tag (`git checkout -b hotfix/X.Y.Z+1 vX.Y.Z`), fix, PR to `main`, cherry-pick
if `main` has diverged, tag from the hotfix branch.

## F-Droid and IzzyOnDroid

Store listings read the fastlane metadata in `fastlane/metadata/android/en-US/` (F-Droid's
[descriptions and graphics](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/)):
`title.txt` (≤ 50 characters), `short_description.txt` (≤ 80), `full_description.txt` (≤ 4000, simple HTML),
`changelogs/<versionCode>.txt` (≤ 500) and `images/` (`icon.png` 512 px, `featureGraphic.png` 1024 × 500,
`phoneScreenshots/`, `tenInchScreenshots/`). They describe the `foss` flavor, the one these stores ship; Google Play's listing, for `gms`, is in
`docs/PLAY_STORE.md` §4.

- **Each release** adds `changelogs/<versionCode>.txt` with the universal APK's version code (`MAJOR * 1_000_000 +
  MINOR * 10_000 + PATCH * 100`, e.g. `40000.txt` for 0.4.0), a short summary of the CHANGELOG section, in the
  `chore(release)` pull request. Stores install the universal APK or build it; per-ABI version codes (…01–…03) have
  no changelog of their own.
- **Images**: `tools/fastlane/images.sh` renders the icon and the feature graphic from `art/` with headless Chromium
  (`CHROMIUM=…` if it is not on the PATH) and copies the light screenshots of `docs/screenshots/`, which
  `tools/readme/screenshots.sh` makes from the goldens; run both after re-recording the goldens they use.
- **IzzyOnDroid** ships the APKs of the GitHub releases as they are, signed by the owner, and reads this metadata: it
  needs nothing else from the repository.
- **F-Droid's main repository** builds from source with its own key. `docs/fdroid/dev.kyralabs.pingogram.yml` drafts
  its recipe for review, in fdroiddata's format; it is not submitted. The build side is ready:
  - `-Ppingogram.unsignedRelease=true` packages release APKs without a signing config (`*-release-unsigned.apk`);
    every other build still fails without one;
  - `-Ppingogram.tdlib.aar=…` takes a TDLib built from source instead of the release asset (`docs/TDLIB.md`).

  Still open, in the recipe as TODOs:
  - **Telegram API values.** They would sit in clear text in the public recipe (owner decision).
  - **TDLib.** Its AAR has to be packaged in the recipe as `.github/workflows/tdlib.yml` does.
  - **NTgCalls (voice calls).** Its build downloads prebuilt WebRTC, which F-Droid does not accept. Calls stay in
    F-Droid's build, as in Telegram FOSS's and Telegram X's: the call engine and WebRTC will be built from source
    (`docs/adr/0030-calls-in-the-fdroid-build.md`). The spike (2026-10-27) showed it builds within F-Droid's limits;
    which engine builds on that WebRTC is the next step.

## Reproducible builds

A build is reproducible when the same commit, built again, gives the same APK apart from its signature, which lets a
store (F-Droid's "reproducible builds", IzzyOnDroid's checks) verify that a signed release came from this source.

- **Pinned inputs**: the Gradle wrapper with its checksum (`distributionSha256Sum`), every dependency at an exact
  version (`gradle/libs.versions.toml`), JDK 17 (Temurin in CI), the TDLib AAR by commit and SHA-256 (`docs/TDLIB.md`),
  and the generated color themes, checked by CI against their generator. No build time, host name or random value
  goes into the APK; `BuildConfig` holds the version and the Telegram API values from `secrets.properties`, which a
  verifying build must use too.
- **Checked** (2026-10-22): two clean builds of `fossBenchmarkRelease` (R8 and resource shrinking as in `release`, the
  same commit and checkout, `:app:clean` and `--no-build-cache` in between) gave the same 1,258 entries, byte for byte,
  in the same order with the same zip layout; only the APK signing block differed. To repeat it:

  ```bash
  ./gradlew :app:assembleFossBenchmarkRelease
  cp app/build/outputs/apk/foss/benchmarkRelease/app-foss-arm64-v8a-benchmarkRelease.apk /tmp/first.apk
  ./gradlew :app:clean && ./gradlew :app:assembleFossBenchmarkRelease --no-build-cache
  # compare every entry, then the bytes before the APK signing block (e.g. with diffoscope or apksigcopier compare)
  ```

- **Checked across directories** (2026-10-22): the same commit in two checkouts at different paths
  (`git worktree add`, the second one level deeper), each built cleanly with `--no-build-cache`. The arm64-v8a and
  universal APKs gave the same 1,276 and 1,288 entries, byte for byte, in the same order. Paths do not reach the APK.
- **Not checked yet**:
  - a build on another machine or JDK build;
  - an unsigned `release` (`-Ppingogram.unsignedRelease=true`) compared with a signed GitHub release
    (`apksigcopier compare`). That one needs the release's Telegram API values.

## Google Play

`docs/PLAY_STORE.md`: the Data safety form, the permission declarations, the listing, Play App Signing with the
existing key, and the `gms` bundle each tag builds (the artifact `pingogram-X.Y.Z-play`). Its optional upload to the
internal track (`release.yml`'s `upload-to-play` job, Gradle Play Publisher) is off unless the repository variable
`PLAY_PUBLISH` is `true` and the secret `PLAY_SERVICE_ACCOUNT_JSON` is set. GitHub stays the canonical release channel.

## Galaxy Store and AppGallery

`docs/STORES.md`: Galaxy Store gets the `gms` universal APK (`pingogram-X.Y.Z-universal-gms.apk`), AppGallery the
`foss` one (`pingogram-X.Y.Z-universal.apk`), both from the GitHub release's assets and signed with the same key, and
both uploaded by hand for now. Their listing images come from `tools/stores/images.sh`.
