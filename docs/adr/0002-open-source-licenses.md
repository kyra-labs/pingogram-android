# 0002 — Open-source licenses screen: AboutLibraries data, our own Compose screens

Status: accepted · Date: 2026-10-19 · Milestone: M3 (`REQUIREMENTS.md` FR-S1, About)

## Context

About must list the open-source software Pingogram ships with and its licenses (FR-S1; Apache 2.0, BSD and the OFL
all ask for their notices to travel with binaries, and `docs/DESIGN_SYSTEM.md` §3.1 promises Google Sans Flex an
entry in an OSS licenses screen). Requirements for the solution:

- The list comes from the **actual dependency graph of the app being built**, per variant, at build time; nobody
  maintains a list of Maven dependencies by hand.
- Things that are not Maven dependencies with license metadata are added by hand: TDLib (a local AAR whose POM has
  no license), Google Sans Flex (bundled font, SIL OFL 1.1) and Material Symbols (the icons, Apache 2.0).
- No network at run time (`REQUIREMENTS.md` §6.2), no Play Services in the `foss` flavor, no View UI (`CLAUDE.md`
  rule 4), small APK cost (`docs/PERFORMANCE.md` §1), and it must work with AGP 9.4, Gradle 9.8, the configuration
  cache, R8 full mode and both flavors.

Options considered:

1. **Google's `oss-licenses-plugin` + `play-services-oss-licenses`.** Its runtime part is `OssLicensesMenuActivity`,
   a View/AppCompat activity in a Play Services artifact: rejected for `foss` and for rule 4. Using only the plugin
   would leave us parsing its undocumented raw files without the license texts of POMs that link to them.
2. **cashapp/licensee.** A build-time validator that writes `artifacts.json` (SPDX ids and URLs, no license texts);
   no runtime part. Good as a gate, but we would still have to supply every text and write the parser and the
   manual-entry mechanism ourselves. Not adopted; its allow-list idea is covered below by a unit test.
3. **AboutLibraries** (mikepenz, Apache 2.0): the Gradle plugin `com.mikepenz.aboutlibraries.plugin.android` writes
   each variant's runtime dependencies into a raw resource, merging a config directory of extra libraries and license
   texts; `aboutlibraries-core` parses it (on Android with the platform's `org.json`; its classes target Java 21). Its prebuilt
   `aboutlibraries-compose-m3` UI exists but follows its own layout, not our segmented settings rows.

## Decision

AboutLibraries **15.3.0**, data only:

- `:app` applies the Android plugin (only the app knows every dependency it ships) with `offlineMode = true` (no
  network at build time either: no GitHub API, no SPDX downloads) and `includePlatform = false` (BOMs carry no code).
  Verified: configures and runs under the configuration cache with AGP 9.4.1 / Gradle 9.8.1, one task per variant
  (`prepareLibraryDefinitions<Variant>`, e.g. `FossRelease`, `GmsRelease`, `FossBenchmarkRelease`).
- `config/aboutlibraries/` holds what Maven does not tell: `libraries/` merges the TDLib entry into the
  `org.drinkless:tdlib` dependency (name, website, authors, `BSL-1.0`) and adds Google Sans Flex (`OFL-1.1`) and
  Material Symbols (`Apache-2.0`); `licenses/` holds the full texts of every license in the graph (Apache 2.0,
  BSD 3-Clause, Boost 1.0, OFL 1.1). The OFL and Apache texts are the files the app already ships in `core:ui`'s
  `assets/licenses/`.
- Only the plugin is used. `:app`'s `GeneratedOpenSourceLicensesRepository` reads the file with the platform's
  `org.json` (a few lines: `aboutlibraries-core` would parse it the same way, but its classes target Java 21, which the
  build's JDK 17 cannot load in unit tests, and it adds code to the APK). It references `R.raw.aboutlibraries`
  directly, so R8's resource shrinking keeps the file and no `getIdentifier` lookup is needed), mapped once to
  `core:domain`'s `OpenSourceLibrary` behind `OpenSourceLicensesRepository`, and cached for the process.
- `feature:settings` draws **our own screens**: `OpenSourceLicensesRoute` (one segmented group in a lazy list: name,
  version · licenses) and `OpenSourceLibraryRoute` (details, then each license's full text, reflowed for the screen and
  selectable, plus a link to it online), with the settings components (`docs/DESIGN_SYSTEM.md` §2.2, §6).
- Guards in `:app`'s unit tests against the generated data of the build itself: Material 3 and TDLib are listed with
  their licenses, every library has a license with its text (a new dependency under another license fails until its
  text is added to `config/aboutlibraries/licenses/`), and the font's and icons' texts equal the bundled asset files.

## Consequences

- APK cost: about +50 KB per APK (measured +52 KB for `fossBenchmarkRelease` arm64-v8a while `aboutlibraries-core`'s
  24 small classes were still in; without them slightly less), for the generated list (98 KB raw, 14 KB compressed),
  the parser, the two screens, their strings and an icon. No runtime library.
- The list is exact per variant: `gms` lists what `gms` ships, debug builds also their debug-only libraries.
- Updating a dependency needs nothing; adding one under a new license needs its text in `config/aboutlibraries/`.
  Changing Google Sans Flex's license file means updating `OFL-1.1.json` too (the test says so).
- TDLib's version is its commit; the screens show its first 10 characters, as its releases are named.
- Bumping the plugin (`aboutLibraries` in the version catalog) can change the file's format: the repository test reads
  the real generated file, so a change shows there.
