# Pingogram — Claude Code project rules

Pingogram is an open-source, third-party **Telegram client for Android**, written from scratch on
**TDLib** with **Kotlin + Jetpack Compose + Material 3 Expressive**. It is **not** a fork of the
official Telegram app; no code from `DrKLO/Telegram` or Telegram X is copied into this repo.

Read these before doing any work (they are the source of truth, in this order):

- @REQUIREMENTS.md — what we are building, scope, compliance, acceptance criteria
- @docs/ARCHITECTURE.md — modules, layers, data flow, naming
- @docs/DESIGN_SYSTEM.md — Material 3 Expressive rules, component mapping
- @docs/TDLIB.md — how we build and wrap TDLib
- @docs/PERFORMANCE.md — budgets and optimization rules; regressions block merge
- @docs/ROADMAP.md — milestones; always work on the lowest unfinished milestone unless told otherwise
- @docs/TESTING.md — what tests are required
- @docs/RELEASE.md — versioning, signing, release process

## Hard rules (never break these)

1. **Secrets.** Never write `api_id`, `api_hash`, keystore passwords, or tokens into source,
   Gradle files, docs, logs, tests or commit messages. They come only from `secrets.properties`
   (git-ignored) locally and from GitHub Actions secrets in CI. If a value is missing, fail the
   build with a clear message; never hard-code a fallback.
2. **Branding.** The word "Telegram" never appears in: app name, `applicationId`, package names,
   launcher icon, notification channel names, or store/release titles. It is allowed only in
   descriptive text such as "a Telegram client" or "Telegram account". The app is **Pingogram**,
   package root `app.pingogram`.
3. **Telegram API Terms of Service** (https://core.telegram.org/api/terms) are mandatory:
   no spam/automation features, no hiding or altering sponsored messages in channels, no
   scraping, no circumventing limits, no misrepresenting the client as official. Identify the
   client honestly in `SetTdlibParameters` (`deviceModel`, `applicationVersion`).
4. **Compose only, Material 3 Expressive only.** No XML layouts, no AppCompat/Material
   Components (View) widgets, no Material 2 (`androidx.compose.material`). UI is 100%
   `androidx.compose.material3` under `MaterialExpressiveTheme`, following **every** rule in
   `docs/DESIGN_SYSTEM.md`: color roles only, the full type scale, the shape scale +
   `MaterialShapes`, `MotionScheme.expressive()` springs, and the Expressive component set.
   No literal colors, sizes, durations or easing in feature code (detekt rule
   `PingogramHardcodedToken`). Exceptions: the manifest, splash theme, and framework-required
   resources (icons, strings, notification icons).
4b. **Typography is Google Sans Flex (variable).** Bundled under OFL in `core:ui`; every text
   style sets `wght`, `opsz` (= size), `ROND`, `GRAD` (−25 in dark) and `slnt` through
   `FontVariation.Settings` as specified in `docs/DESIGN_SYSTEM.md` §3. No other font family
   except `FontFamily.Monospace` for code and system fallbacks for missing scripts.
4c. **Performance is a requirement.** Follow `docs/PERFORMANCE.md`: immutable UI state,
   keyed lazy lists, animations in the draw/layout phase, explicit image sizes, cancellable
   TDLib calls, no polling. A PR that regresses a benchmark budget by > 10% does not merge.
5. **Kotlin only.** No Java sources except the generated TDLib JNI bindings (`org.drinkless.tdlib`).
6. **Architecture.** Unidirectional data flow: `Screen(Composable) → ViewModel(UiState, events) →
   Repository/UseCase → TdlibClient`. Composables never call TDLib directly. No `GlobalScope`.
   No blocking calls on the main thread.
7. **Dependencies** go through `gradle/libs.versions.toml` only, pinned to exact versions. Use
   the latest *stable* AndroidX/Compose versions; alpha/beta only when a required M3 Expressive
   component exists nowhere else, and then note it in `CHANGELOG.md`.
8. **Every PR**: compiles, `./gradlew check` passes (ktlint/detekt, unit tests), new ViewModels
   have unit tests, new screens have a Compose preview, `CHANGELOG.md` "Unreleased" is updated
   for user-visible changes.
9. **No copyrighted assets.** No Telegram logos, stickers, sounds, or icon sets. Use Material
   Symbols (Apache 2.0) and our own artwork only.
10. **Privacy.** No analytics SDKs, no crash reporters that send data off-device unless the
    owner explicitly adds one. No ad SDKs. Ever.

## Working style for Claude Code

- Start every task by restating which milestone/feature it belongs to and listing the files you
  expect to touch. For anything over ~3 files, write a short plan first and wait for approval if
  the task is ambiguous.
- Keep PRs small and single-purpose. Use **Conventional Commits**:
  `feat(chat): ...`, `fix(auth): ...`, `chore(deps): ...`, `docs: ...`, `ci: ...`, `refactor: ...`.
- Prefer editing existing files over creating parallel ones. Delete dead code.
- When a TDLib API is uncertain, check the `TdApi.java` in the generated bindings in
  `core/tdlib/libs` (or `td_api.tl` in the pinned TDLib version) instead of guessing.
- When a Material 3 Expressive API is uncertain, check the current Compose Material 3 release
  notes/API reference (versions and annotations change). Mark experimental opt-ins with the
  exact annotation required, in one `@OptIn` at the composable, never project-wide.
- Never run `git push --force`, never rewrite `main`, never delete tags.
- Never modify `.github/workflows/release.yml` signing steps without saying so explicitly.
- If something in these docs conflicts with the code, the docs win; fix the code, or propose a
  doc change in the PR description.

## Commands

```bash
./gradlew assembleDebug              # debug APK → app/build/outputs/apk/debug/
./gradlew check                      # ktlint + detekt + unit tests (must pass before finishing)
./gradlew testDebugUnitTest          # unit tests only
./gradlew ktlintFormat               # auto-format Kotlin
./gradlew :app:connectedDebugAndroidTest   # instrumented tests (needs emulator/device)
./gradlew assembleRelease            # needs signing config (see docs/RELEASE.md)
./gradlew dependencyUpdates          # list newer dependency versions (ben-manes plugin)
```

## Definition of done for a feature

- [ ] Behaviour matches `REQUIREMENTS.md`; edge cases (offline, FLOOD_WAIT, logged-out mid-flow) handled
- [ ] Passes the per-screen M3 Expressive audit (`docs/DESIGN_SYSTEM.md` §12): tokens only, Google Sans Flex styles, shapes, motion-scheme springs, listed components; light/dark/dynamic/AMOLED/high-contrast OK
- [ ] Works at font scale 200%, with TalkBack, and in landscape / tablet width (adaptive)
- [ ] Unit tests for ViewModel + repository; preview for every new screen state (loading/empty/error/content)
- [ ] No new lint warnings; no new `@Suppress` without a comment explaining why
- [ ] `docs/PERFORMANCE.md` §10 checklist done; benchmark for the screen unchanged or better
- [ ] `CHANGELOG.md` updated; docs updated if architecture changed

## Ask the owner before

- Adding any new third-party dependency over ~1 MB or any SDK that touches the network
- Changing `minSdk`, `targetSdk`, TDLib version, or the signing/release workflow
- Adding permissions to the manifest
- Anything that could conflict with Telegram's API terms
