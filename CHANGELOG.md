# Changelog

All notable changes to Pingogram are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

The release workflow copies the section for the tagged version into the GitHub Release notes,
so keep headings exactly in the form `## [X.Y.Z] - YYYY-MM-DD`.

## [Unreleased]

### Added
- Project scaffold, documentation, CI, TDLib and release workflows.
- Gradle project: version catalog, `build-logic` convention plugins, Gradle 9.8.1 wrapper and the
  `:app`, `:core:*` and `:feature:*` modules from `docs/ARCHITECTURE.md` (mostly empty for now).
  AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00; `foss`/`gms` flavors; app version and
  per-ABI version codes from `-Pversion`.
- Telegram API credentials come from the git-ignored `secrets.properties`; the build fails with a
  clear message (never echoing values) when the file or a key is missing or malformed.
- Committed debug keystore for consistent debug signatures; release signing from environment or
  `keystore.properties` (v2/v3/v4), and release packaging fails early when signing is not configured.

- Static analysis in `./gradlew check`: ktlint 1.8 (`./gradlew ktlintFormat`), detekt 1.23.8 with the
  `PingogramHardcodedToken` rule (no literal colors, text sizes, sizes, shapes or animation specs in
  `:feature:*` code), Android Lint with warnings as errors, and `./gradlew dependencyUpdates`.

- `:benchmark` module: Macrobenchmark `StartupBenchmark` and Baseline/Startup Profile generation
  (`./gradlew :app:generateBaselineProfile`); release builds ship the profile via profileinstaller.

- `PingogramTheme` on `MaterialExpressiveTheme`: six generated brand color schemes (seed `#E8457C`,
  standard/medium/high contrast), dynamic color on Android 12+, system contrast on Android 14+,
  AMOLED black, harmonized online green; the full Google Sans Flex type scale (15 styles + 15
  emphasized, per-style variable axes); the expressive shape scale with `MaterialShapes` helpers;
  spacing tokens; preview helpers; splash and window themes.
- Roborazzi screenshot tests (Robolectric, native graphics) verified by `./gradlew check`.

- Release builds produce one APK per ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`) plus a universal APK,
  with distinct version codes.

- TDLib 1.8.68 for Android, built by the `Build TDLib` workflow and published as the `tdlib-a94b83d67a`
  release; the build downloads it and verifies its pinned SHA-256 (`pingogram.tdlib.aar` overrides it with
  a local AAR).
- `core:tdlib`: coroutine `TdlibClient` with typed authorization and connection state, automatic
  `SetTdlibParameters` (honest client identification), database key wrapped by the Android Keystore and
  kept out of backups; `FakeTdlibClient` and TdApi fixtures for tests.

### Changed
- Package root and `applicationId` are `dev.kyralabs.pingogram` (debug: `dev.kyralabs.pingogram.debug`).
- `compileSdk` is 37 (needed by Compose 1.12); `targetSdk` stays 36.
- Uses the pre-release `androidx.compose.material3:material3:1.5.0-beta01` (allowed by CLAUDE.md rule 7):
  in the stable 1.4.0 the Material 3 Expressive APIs (`MotionScheme.expressive()`, `MaterialShapes`,
  the emphasized type styles, the increased shape sizes) are internal.
- Backups and device transfer are disabled for all app data.

[Unreleased]: https://github.com/kyra-labs/pingogram-android/compare/main...HEAD
