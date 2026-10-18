# Testing

## Pyramid

| Level | Tool | Where | Required for |
|---|---|---|---|
| Unit (JVM) | JUnit 5, MockK, Turbine, `kotlinx-coroutines-test` | every `core:*` and `feature:*` | Every repository, use case, ViewModel, mapper |
| Screenshot | Roborazzi (Robolectric) | `feature:*`, `core:ui` | Every `Screen` state × light/dark; run in CI, PNGs committed under `src/test/snapshots` |
| Compose UI (JVM) | `createComposeRule` + Robolectric | `feature:*` | Interaction-heavy screens (composer, code entry, swipe) |
| Instrumented | Espresso/Compose on emulator, test DC | `:app` `androidTest` | Smoke: launch → login on test DC → open a chat (nightly, not per PR) |
| Macrobenchmark | `benchmark/` | release build | Startup, chat-list scroll, conversation scroll; generates Baseline Profile |
| Static | ktlint, detekt (+ `PingogramHardcodedToken`), Android Lint (`abortOnError = true`, `warningsAsErrors = true`) | all | Every PR |

## Rules

- A ViewModel without a test does not merge. Tests cover: initial state, each event, each
  error path (`FloodWait`, `Network`, `Unauthorized`), process-death restoration via `SavedStateHandle`.
- Use `FakeTdlibClient` (`core:testing`) — never MockK the TDLib `Client` directly.
- Fixtures: `TdApiFixtures.chat(id = …)`, `.user(…)`, `.message(…)` builders in `core:testing`.
- Use `StandardTestDispatcher` with `runTest`; inject dispatchers; no `Thread.sleep`, no
  `delay` longer than 1 ms in tests.
- Screenshot tests: `@Config(qualifiers = "w411dp-h891dp-xxhdpi")` compact phone, plus one
  `w840dp` expanded variant for adaptive screens. Record with `./gradlew recordRoborazziDebug`,
  verify with `verifyRoborazziDebug` in CI.
  - The `pingogram.android.roborazzi` convention plugin sets them up (Robolectric native graphics,
    JUnit 4 tests on the JUnit Platform through the vintage engine). `pingogram.android.feature`
    applies it; other modules add it next to `pingogram.android.compose`.
  - Write `@RunWith(RobolectricTestRunner::class)` tests that call
    `captureRoboImage { PingogramTheme(dynamicColor = false) { … } }`: brand colors, so goldens do
    not depend on a wallpaper. Example: `core/ui/.../ThemeShowcaseScreenshotTest.kt`.
  - Goldens: `src/test/snapshots/<package>.<TestClass>.<test>.png`. Record them with
    `./gradlew :<module>:recordRoborazziDebug` and commit them; `./gradlew check` fails on any
    difference (it runs `verifyRoborazziDebug`). A plain `testDebugUnitTest` skips the captures.
    Record on Linux like CI: other platforms rasterize text slightly differently.
  - Robolectric runs API 34 by default (`PingogramAndroid.ROBOLECTRIC_SDK`): its API 35+ runtimes
    need JDK 21 and the build uses JDK 17. A test that needs a newer API sets
    `@Config(sdk = [...])` and must run on JDK 21.
- Accessibility assertions: `assertHasClickAction`, `assertContentDescriptionEquals` on icon
  buttons; `AccessibilityChecks` enabled in Compose UI tests.
- Test names in backticks describing behaviour. One assertion concept per test.

## Static analysis

`./gradlew check` runs ktlint, detekt and Android Lint on every module (convention plugins
`pingogram.ktlint`, `pingogram.detekt`, `pingogram.android.lint`) and the build-logic tests. There are
no baselines: fix the finding, or suppress a single occurrence with `@Suppress("RuleId")` and a comment
saying why.

- ktlint: `ktlint_official` style from `.editorconfig`. `./gradlew ktlintFormat` fixes most findings,
  build-logic included.
- detekt: `config/detekt/detekt.yml` on top of detekt's default config; the stable detekt CLI runs in
  its own JVM, reports land in `<module>/build/reports/detekt/` (HTML, SARIF).
- `PingogramHardcodedToken` (`build-logic/detekt-rules`) fails `:feature:*` production code that uses
  literal colors, `sp`/`dp` values (except `0.dp`), corner shapes or animation specs instead of the
  `MaterialTheme` tokens (`docs/DESIGN_SYSTEM.md` §1). Test sources and `core`/`app` are not checked.
- Android Lint: every warning is an error; `NewApi`, `UnusedResources` and `MissingTranslation` are
  fatal. Checks that do not fit the project are disabled, each with its reason, in
  `AndroidLintConventionPlugin`.

## Test DC accounts

Telegram provides test phone numbers for the test data center (see
https://core.telegram.org/api/auth#test-accounts). Use them only in `androidTest` with
`useTestDc = true`. Never run automated flows against the production DC.

## CI gates

- PR: `check` (ktlint, detekt, lint, unit, screenshot verify), `assembleFossDebug`.
- Nightly: instrumented smoke on API 26 and API 36 emulators, Macrobenchmark on a Firebase
  Test Lab physical device if the owner configures it (optional).
