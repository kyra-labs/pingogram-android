# Testing

## Pyramid

| Level | Tool | Where | Required for |
|---|---|---|---|
| Unit (JVM) | JUnit 5, MockK, Turbine, `kotlinx-coroutines-test` | every `core:*` and `feature:*` | Every repository, use case, ViewModel, mapper |
| Screenshot | Roborazzi (Robolectric) | `feature:*`, `core:ui` | Every `Screen` state × light/dark; run in CI, PNGs committed under `src/test/snapshots` |
| Compose UI (JVM) | `createComposeRule` + Robolectric | `feature:*` | Interaction-heavy screens (composer, code entry, swipe) |
| Instrumented | Espresso/Compose on emulator, test DC | `:app` `androidTest` | Smoke: launch → login on test DC → open a chat (nightly, not per PR) |
| Macrobenchmark | `benchmark/` | release build | Startup, chat-list scroll, conversation scroll; generates Baseline Profile |
| Static | ktlint, detekt, Android Lint (`abortOnError = true`, `warningsAsErrors` for our rules) | all | Every PR |

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
- Accessibility assertions: `assertHasClickAction`, `assertContentDescriptionEquals` on icon
  buttons; `AccessibilityChecks` enabled in Compose UI tests.
- Test names in backticks describing behaviour. One assertion concept per test.

## Test DC accounts

Telegram provides test phone numbers for the test data center (see
https://core.telegram.org/api/auth#test-accounts). Use them only in `androidTest` with
`useTestDc = true`. Never run automated flows against the production DC.

## CI gates

- PR: `check` (ktlint, detekt, lint, unit, screenshot verify), `assembleFossDebug`.
- Nightly: instrumented smoke on API 26 and API 36 emulators, Macrobenchmark on a Firebase
  Test Lab physical device if the owner configures it (optional).
