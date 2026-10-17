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

### Changed
- Package root and `applicationId` are `dev.kyralabs.pingogram` (debug: `dev.kyralabs.pingogram.debug`).
- `compileSdk` is 37 (needed by Compose 1.12); `targetSdk` stays 36.
- Backups and device transfer are disabled for all app data.

[Unreleased]: https://github.com/kyra-labs/pingogram-android/compare/main...HEAD
