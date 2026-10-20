# 0002 — Open-source licenses screen: our own Gradle task over the POMs, our own Compose screens

Status: accepted · Date: 2026-10-19 (revised the same day: owner decision D17, option a) · Milestone: M3
(`REQUIREMENTS.md` FR-S1, About)

## Context

About must list the open-source software Pingogram ships with and its licenses (FR-S1; Apache 2.0, BSD and the OFL
all ask for their notices to travel with binaries, and `docs/DESIGN_SYSTEM.md` §3.1 promises Google Sans Flex an
entry in an OSS licenses screen). Requirements for the solution:

- The list comes from the **actual dependency graph of the app being built**, per variant, at build time; nobody
  maintains a list of Maven dependencies by hand.
- Things that are not Maven dependencies with license metadata are added by hand: TDLib (a local AAR whose POM has
  no license), Google Sans Flex (bundled font, SIL OFL 1.1) and Material Symbols (the icons, Apache 2.0).
- It is a compliance gate: a dependency whose license we cannot name, or a license we have no text for, fails the
  build instead of shipping an incomplete list.
- No network at run time (`REQUIREMENTS.md` §6.2) and none at build time beyond Gradle's own dependency resolution
  (no GitHub API, no SPDX downloads), no Play Services in the `foss` flavor, no View UI (`CLAUDE.md` rule 4), small APK
  cost (`docs/PERFORMANCE.md` §1), and it must work with AGP 9.4, Gradle 9.8, the configuration cache, the build
  cache, R8 full mode and every variant.

Options considered:

1. **Google's `oss-licenses-plugin` + `play-services-oss-licenses`.** Its runtime part is `OssLicensesMenuActivity`,
   a View/AppCompat activity in a Play Services artifact: rejected for `foss` and for rule 4. Using only the plugin
   would leave us parsing its undocumented raw files without the license texts of POMs that link to them.
2. **cashapp/licensee.** A build-time validator that writes `artifacts.json` (SPDX ids and URLs, no license texts);
   no runtime part. A good gate, but we would still supply every text, the manual entries and the resource, and add
   a third-party plugin for the part that is left.
3. **AboutLibraries** (mikepenz, Apache 2.0): its Gradle plugin writes each variant's dependencies into a raw resource,
   merged with a config directory of extra libraries and license texts. It was the first implementation (PR #70) and
   worked, but the owner decided against it (**D17**): it is a third-party build dependency for a job of a few hundred
   lines, its output format and options change with its versions, and it does not fail the build on a missing license
   by itself (a unit test of ours did).
4. **Our own Gradle task in `build-logic`** that reads the POMs (owner decision D17, option a).

## Decision

Option 4, `pingogram.android.licenses` (`build-logic/convention`), applied by `:app` only (only the app knows every
dependency it ships):

- **One task per variant**, `generate<Variant>OpenSourceLicenses` (`OpenSourceLicensesTask`, cacheable), registered
  through AGP's variant API for every flavor and build type (`benchmarkRelease` and `nonMinifiedRelease` included).
  Its output is a generated resource directory of the variant (`variant.sources.res.addGeneratedSourceDirectory`)
  holding `raw/open_source_licenses.json`.
- **Dependencies**: the module components of the variant's runtime classpath graph
  (`runtimeConfiguration.incoming.resolutionResult.rootComponent`), without our own projects, without BOMs (components
  whose only variants are platforms) and without the platform modules of Kotlin Multiplatform libraries
  (`androidx.compose.ui:ui` redirects to `ui-android` through Gradle Module Metadata's `available-at`; the library is
  listed once, by the module people declare).
- **POM resolution** (`PomResolver`): most dependencies publish Gradle Module Metadata, so Gradle never fetches their
  POMs while resolving the graph. Each POM is asked for as an artifact-only dependency `group:artifact:version@pom`
  of a detached, non-transitive configuration, read through a lenient artifact view: Gradle takes it from its cache or
  downloads it from the declared repositories (with their content filters and the same checks as every artifact);
  nothing else touches the network. An artifact-only dependency takes the file by name, whatever variants the module
  lists, so multiplatform modules need no attributes. A detached configuration resolves each module once, so the POMs
  go in rounds that hold one version per module (two versions of `guava-parent` would otherwise collapse into the
  newer). Parent POMs are known only after reading the children, so they follow in further rounds until no new parent
  turns up. This is how cashapp/licensee and AboutLibraries resolve POMs. It runs in the mapping of `rootComponent`
  that sets the task's `libraries` input, which Gradle evaluates while it stores the configuration cache entry (when
  it also resolves the graph), never in the task action: resolving configurations there is not allowed with the
  configuration cache, and neither is `ArtifactResolutionQuery`. The input is the libraries' values (no file paths),
  so the task's build cache key is the same on every machine; `fossRelease` and `fossNonMinifiedRelease` share one
  entry.
- **POM values** (`Pom`, javax.xml without DTDs or external entities): name, description, url (else the scm url,
  `http(s)` only), developers (else the organization) and licenses, with Maven's inheritance from parent POMs for
  licenses, developers, organization, url and scm (not name; not the description, which in a parent describes the
  parent), and `${…}` placeholders filled from the chain's properties and project values.
- **SPDX mapping** (`SpdxLicenses`): a small table of the licenses in the graph and in the manual entries (today
  Apache-2.0, BSD-3-Clause, BSL-1.0, OFL-1.1), each with the spellings and links its POMs use (Apache 2.0 comes as
  "The Apache Software License, Version 2.0", "Apache 2.0", "Apache-2.0"… with `http` and `https` links), compared
  loosely (case, punctuation, scheme, `www.`, trailing slash, `.txt`/`.html`).
- **`config/licenses/`** holds what Maven does not tell: `texts/<SPDX id>.txt`, the full text of every license (the
  OFL and Apache texts are the files the app already ships in `core:ui`'s `assets/licenses/`), and `extra.json`: an
  entry with a `group:artifact` id completes that dependency (TDLib: name, website, authors, `BSL-1.0`; its version
  stays the dependency's), any other id is a library of its own (Google Sans Flex, `OFL-1.1`; Material Symbols,
  `Apache-2.0`).
- **Gate**: the task fails, listing every problem with the artifact's coordinates and what to add, when a POM or
  parent POM cannot be resolved and no license is known, when a POM license is not in the table (or its name and link
  name different licenses), when a license has no text, or when an `extra.json` entry completes a dependency the
  variant does not have.
- **Format** (documented on `OpenSourceLicenses`): `{"formatVersion": 1, "libraries": [{id, name, version,
  description, website, developers, licenses: [SPDX id]}], "licenses": {SPDX id: {name, url, text}}}`, each text once.
- `:app`'s `GeneratedOpenSourceLicensesRepository` reads it with the platform's `org.json`, referencing
  `R.raw.open_source_licenses` directly (so R8's resource shrinking keeps it), maps it once to `core:domain`'s
  `OpenSourceLibrary` behind `OpenSourceLicensesRepository`, and caches it for the process.
- `feature:settings` draws **our own screens**: `OpenSourceLicensesRoute` (one segmented group in a lazy list: name,
  version · licenses) and `OpenSourceLibraryRoute` (details, then each license's full text, reflowed for the screen and
  selectable, plus a link to it online), with the settings components (`docs/DESIGN_SYSTEM.md` §2.2, §6).
- Tests: build-logic's unit tests cover POM parsing and inheritance, the SPDX table, the gate's messages and the JSON
  format; `:app`'s unit tests read the file generated for their own variant (Material 3 under Apache 2.0, TDLib under
  BSL-1.0 by its short commit, the font's and icons' texts equal to the bundled assets, every library with a license
  text, no BOMs, each multiplatform library once).

## Consequences

- No third-party build dependency. The list is exact per variant: 140 entries for `fossRelease`, `gmsRelease` and
  `fossBenchmarkRelease` today (the same libraries AboutLibraries listed), 142 for `fossDebug` with its debug-only
  libraries.
- Libraries whose POM inherits its url link to the parent's page (`https://github.com/google/guava`) instead of
  Maven's appended guess (`…/guava/listenablefuture`), which does not exist; a few old AndroidX libraries show their
  POM's name ("Android Support Library fragment") where AboutLibraries showed another.
- Updating a dependency needs nothing. A dependency under a new license, or a new spelling of a known one, fails the
  build until `SpdxLicenses` has it and `config/licenses/texts/` its text; check first that the app may ship under it.
- POMs are resolved again only when the configuration cache entry is stored anew (or on every build without the
  configuration cache), from Gradle's cache once downloaded; `--offline` works once they are there.
- Changing Google Sans Flex's license file means updating `config/licenses/texts/OFL-1.1.txt` too (the `:app` test
  says so).
- TDLib's version is its commit; the screens show its first 10 characters, as its releases are named.
