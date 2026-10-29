# v1.0 readiness audit

Date: 2026-10-22 · Code: `main` with voice calls (M5) merged · Against `REQUIREMENTS.md` §6, §7 and §9

Each item is one of:

- **Pass**: the code does it, and a test or the build checks it where one can.
- **Fail**: the code does not do it yet. The owner column says who fixes it:
  - **R**: the release and calls session, which fixed its own items in this PR (marked *fixed here*).
  - **A**: the chat screens session.
  - **B**: the settings, theming and localization session.
  - **Owner**: needs the owner's decision or accounts.
- **Needs device**: it can only be shown on a phone (benchmarks, battery, dogfood); the code is in place.

## Summary

| | Pass | Fail | Needs device or owner |
|---|---|---|---|
| §6 Non-functional | 18 (2 fixed here) | 5 | 5 |
| §7 Platform checklist | 10 | 3 | 1 |
| §9 Acceptance criteria | 0 | 2 | 3 (one is the owner's terms sign-off, prepared here) |

What blocks 1.0, in order:

1. **Scrolling misses its budget** (§6.1): in the first baseline (`docs/PERFORMANCE.md` §9), the chat list's frame P90 is
   10.4 ms against 8 ms, and the conversation has over 1% late frames. #150 lowered the cost on the JVM; the owner's
   phone has to measure it again. Owner: A, then the owner.
2. **The 7-day dogfood** with at least 10 testers, at least 99.5% crash-free (§6.5, §9.4). "Send feedback" and `docs/TESTING_GUIDE.md` are ready. Owner.
3. **Localization**: Weblate is set up (#152) but no language is translated yet, where M6 asks for at least 5 (§6.4).
   Owner: the owner (translators), then B.
4. **Accessibility evidence** (§6.3): #154 added the checks (`assertAccessible()`) to settings, profile, contacts,
   sign-in and the app shell (`docs/audit/accessibility.md`); the chat screens still need them and the fixes A2 and
   A5–A15 listed there (A1, A3 and A4, keyboard and mouse, are done in #171), and contacts, profile, media, scanner and core:ui still need 200% font goldens. Owner A (chat
   screens), B (the rest).
5. **Benchmark thresholds** (§9.3): `tools/benchmark/compare.py` checks a run against the budgets and the last
   baseline, and `docs/RELEASE.md` runs it on the owner's phone before each release. A device in CI (Firebase Test Lab)
   and a nightly run are still open. Owner.
6. **Account deletion in the app** (Google Play, `docs/PLAY_STORE.md` §8). Owner B; not a §6/§7 item, but Play
   requires it for the `gms` listing.
7. **Pingogram's privacy policy inside the app** (Galaxy Store and AppGallery, `docs/STORES.md` §3). Both stores ask
   for the policy to be reachable in the app. The Welcome screen links Telegram's policy, and Settings → About has no
   row for `PRIVACY.md`. A "Privacy policy" row in About is the fix, needed before the first submission to either
   store. Owner B; not a §6/§7 item.

## §6 Non-functional requirements

### 6.1 Performance

| Item | Status | Evidence / what is missing | Owner |
|---|---|---|---|
| Cold start ≤ 1.2 s, warm ≤ 400 ms | Needs device (passes on the owner's phone) | `StartupBenchmark`: 487 ms cold, 135 ms warm with the profile (Galaxy S24 Ultra). Not yet measured on §1's mid-range reference device | Owner |
| Scroll at native rate, P90 ≤ 8–10 ms, jank < 1% | **Fail** | Chat list P90 10.4 ms (budget 8 ms); conversation late frames > 1% in the device baseline (`docs/PERFORMANCE.md` §9). #150 prefetches rows while the lists scroll (JVM scroll cost 2–21% lower); the device numbers are not measured again yet | A, owner (device run) |
| Open a cached chat ≤ 150 ms; optimistic send ≤ 50 ms | Needs device | `OpenChatBenchmark` measures the `openChat` trace section, from the tap in the chat list to the first messages drawn, opening Saved Messages with its history cached. `SendMessageBenchmark` (2026-10-27) measures the `sendMessage` section, from the send tap to the bubble on its way drawn, sending to Saved Messages and deleting each message after; its budget is in `benchmark/budgets.json` for `compare.py`. Neither has run on the phone yet (`docs/PERFORMANCE.md` §9 has the command) | Owner (device run) |
| Memory ≤ 120 MB idle, ≤ 250 MB in a media chat | Needs device | `TdlibMessageRepository` keeps 3 chats (LRU). `onTrimMemory` now clears it (*fixed here*: the trim was never called) | R |
| APK ≤ 50 MB arm64, universal ≤ 125 MB | Pass (*fixed here*) | The release workflow now fails when an APK is over budget. It had no size gate before | R |
| Battery ≤ 1.5% / 8 h, data ≤ 2 MB / 24 h, no polling | Needs device | No polling or alarms in the code (checked: no `AlarmManager`); the one periodic work is the weekly storage clean-up, only while charging and idle (`StorageOptimizationWorker`, 2026-10-27). Battery Historian and data runs are manual | Owner |
| Compose: immutable state, keyed lists, no off-screen recompositions, draw/layout-phase animations | Pass | Compose compiler stability config; `ChatListRecompositionTest`, `ChatRecompositionTest` | — |
| TDLib calls: suspendable, cancellable, batched | Pass | `TdlibClient.send` is cancellable; `viewMessages` batched in `ChatViewModel` | — |

### 6.2 Security and privacy

| Item | Status | Evidence | Owner |
|---|---|---|---|
| TDLib database encrypted with a Keystore-wrapped key; `useSecretChats` | Pass | `KeystoreDatabaseKeyProvider`, `TdlibParametersTest` | — |
| `allowBackup="false"`, TDLib directories out of every backup | Pass | Manifest, `backup_rules.xml`, `data_extraction_rules.xml`, `AppManifestTest` | — |
| Passcode with biometrics; screen hidden in recents | Pass | ADR 0005, `AppLockRepository`, `FLAG_SECURE` / `setRecentsScreenshotEnabled` | — |
| Network only to Telegram (TDLib) and user-requested links; `gms` push the only exception | Pass | No HTTP client in the app; Coil loads TDLib files only; `checkFossGoogleFree` | — |
| `networkSecurityConfig` with cleartext disabled | Pass (*fixed here*) | It was missing. Android 8–9 allow cleartext by default, so the manifest now names a config that refuses it everywhere | R |
| Logs carry no message text, phone numbers or tokens; TDLib verbosity ≤ 2 in release | Pass | Verbosity 1 in release; "Send feedback" redacts crash and log (`RedactionTest`) | — |
| Contacts sync opt-in; QR scanner keeps nothing | Pass | ADR 0011, ADR 0012 | — |

### 6.3 Accessibility

| Item | Status | Evidence / what is missing | Owner |
|---|---|---|---|
| 48 dp targets, content descriptions, roles, merged message semantics | Pass | Compose tests across features; `minimumInteractiveComponentSize` | — |
| Contrast ≥ 4.5:1 / 3:1 in all contrast levels | Pass | `ThemePalettesTest` | — |
| Font scale 200% without clipping | **Fail** (evidence) | 200% goldens exist in auth, calls (#149), chat, chats, feedback, media, scanner, settings and app. Contacts, profile and core:ui have none; #154 fixed what it cut at 200% in profile and contacts (F2, F8, F15) | A, B |
| `AccessibilityChecks` in Compose UI tests (`docs/TESTING.md`) | **Fail** (partly) | Google's framework does not run under Robolectric; #154 added our own `assertAccessible()` (names, 48 dp targets) to settings, profile, contacts, sign-in and the app shell. The chat screens have none yet (`docs/audit/accessibility.md` §3) | A |
| Reduced motion | Pass | `isReducedMotion()`, shape morphs snap | — |
| TalkBack and Accessibility Scanner pass on every screen | Needs device | `docs/DESIGN_SYSTEM.md` §9 asks for a manual run before "done" | Owner / testers |

### 6.4 Internationalization

| Item | Status | Evidence / what is missing | Owner |
|---|---|---|---|
| English source strings, plurals, `java.time`, `NumberFormat` | Pass | Plurals throughout; lint `ExtraTranslation` fatal; pseudo-locales `en-XA` and `ar-XB` in debug builds (#152) | — |
| RTL verified | Pass (goldens), needs device for a pass in Arabic or Hebrew | RTL goldens in most modules and pseudo-locale goldens of the app (#152: long text, right to left); no RTL golden in scanner or core:ui | A |
| Per-app language | Pass | `PlatformAppLanguageRepository`, `locales_config.xml` | — |
| At least 5 languages (M6) | **Fail** | The pipeline is in place (#152, `docs/adr/0020-translations-on-weblate.md`, `docs/TRANSLATING.md`: Hosted Weblate, a language ships at 80% of the strings, the locale config generated by `pingogram.android.locales`). No translations exist yet, so only English ships | Owner (open the Weblate project, find translators), B |

### 6.5 Reliability

| Item | Status | Evidence | Owner |
|---|---|---|---|
| `FLOOD_WAIT` back-off with a countdown | Pass | `TdError.retryAfter`, `FloodWaitNote`, auth countdown | — |
| Network loss, connection state, DC migration | Pass | Connection pill; `TdlibNetworkTypeReporter` | — |
| Crash-free ≥ 99.5% before a public release | Needs device | Dogfood. "Send feedback" keeps the last crash on the device for testers to share; nothing is reported by itself | Owner |

## §7 Android platform standards checklist

| Item | Status | Evidence / what is missing | Owner |
|---|---|---|---|
| `MaterialExpressiveTheme`, expressive motion, §12 audit | Pass (per screen, in each PR) | `PingogramHardcodedToken`; goldens per screen | — |
| Google Sans Flex type scale, `opsz`, `GRAD` | Pass | `GoogleSansFlexRenderingTest` | — |
| Dynamic color, brand fallback | Pass | `systemDynamicColors`, ADR 0016 | — |
| Edge-to-edge, insets | Pass | `enableEdgeToEdge()`; `Scaffold` insets | — |
| Predictive back | Pass | `enableOnBackInvokedCallback`; `PredictiveBackHandler` in the viewer, attach sheet, selection | — |
| Adaptive layouts, list-detail | Pass | `ListDetailPaneScaffold`, third pane, `SupportingPaneScaffold` | — |
| Themed icon, splash screen | Pass | Monochrome layer; `core-splashscreen` | — |
| Runtime permissions with rationale; Photo Picker | Pass | Rationale steps for notifications, camera, microphone, location, contacts; no storage permission from Android 10 | — |
| Foreground services declare their type | Pass | `dataSync` (transfers), `specialUse` (Keep connected, foss), `phoneCall` / `microphone` (calls) | — |
| Per-app language, keyboard and mouse, state restoration | Needs device for keyboard and mouse | Saved state throughout; Enter-to-send and hover are not covered by tests | Owner / testers |
| Accessibility (§6.3), haptics | **Fail** | See §6.3 | A |
| No deprecated APIs; lint fatal | **Fail** (partly) | Lint `NewApi`, `UnusedResources` and `ExtraTranslation` are fatal and all warnings are errors (`MissingTranslation` informational since #152). Kotlin warnings fail the build in the modules that apply `pingogram.kotlin.warnings` (`allWarningsAsErrors`, main and test sources): `:app`, `core:common`, `core:tdlib`, `feature:calls`, `feature:feedback`, `feature:media` and `feature:scanner` (2026-10-27). The other modules still compile with warnings, counted below ("Kotlin warnings"). A lint disable for `MissingApplicationIcon` was stale and is removed (*fixed here*) | R (Kotlin warnings: each module's owner) |
| Performance budgets per screen | **Fail** | §6.1 | A |
| Live Updates (`ProgressStyle`) for long transfers | Pass | `TransferNotificationBuilder` | — |

## §9 Acceptance criteria for v1.0

| # | Criterion | Status | What is missing | Owner |
|---|---|---|---|---|
| 1 | Install the universal APK, log in, use chats, groups, channels, media, stickers, voice, reactions, notifications and settings without the official app | Needs device | Every feature is in; the dogfood shows it | Owner |
| 2 | All §7 boxes ticked on every screen | **Fail** | §7's three fails | A, B |
| 3 | CI green on `main`; `./gradlew check` and Macrobenchmark thresholds pass | **Fail** | `check` runs in CI; Macrobenchmark runs on the owner's phone, checked by `tools/benchmark/compare.py` before each release (`docs/RELEASE.md`); no device in CI | Owner (a device in CI) |
| 4 | No crashes in a 7-day dogfood with ≥ 10 testers | Needs device | `docs/TESTING_GUIDE.md`, "Send feedback" | Owner |
| 5 | Telegram API terms compliance reviewed by the owner and in the release notes | Needs owner (*prepared here*) | `docs/API_TERMS.md` is the checklist; each release's notes now link to it. The owner reviews it before 1.0 | Owner |

## Also found

- **Kotlin warnings** (§7, "No deprecated APIs"), counted on 2026-10-27 with a fresh compile of every module's main
  and unit test sources (`--rerun-tasks`), each warning once. A module applies `pingogram.kotlin.warnings` in its
  build file once they are fixed:

  | Module | Main | Test | Mostly |
  |---|---|---|---|
  | `core:data` | 28 | 108 | coroutines test APIs (`runCurrent`, `UnconfinedTestDispatcher`) without their opt-in; `kotlinx.collections.immutable`'s deprecated `put`, `remove`, `putAll` |
  | `core:notifications` | 1 | 75 | coroutines test APIs without their opt-in; Robolectric's deprecated `isBroadcastIntent` |
  | `core:ui` | 2 | 6 | coroutines test APIs without their opt-in; an unused expression; a redundant cast |
  | `feature:chat` | 24 | 8 | `kotlinx.collections.immutable`'s deprecated `remove`, `put`, `add`; test names with characters Windows refuses; unused expressions |
  | `feature:chats` | 3 | 0 | `ListItem`'s old overload, a deprecated `copy`, an unused expression |
  | `feature:profile` | 4 | 1 | `rememberModalBottomSheetState`, `ListItem`'s old overload, a redundant cast and safe call |
  | `feature:settings` | 16 | 0 | unused expressions (`Unit` in empty `when` branches), `rememberModalBottomSheetState`, `Slider` and `ListItem`'s old overloads |
  | `core:datastore`, `core:domain`, `core:navigation`, `core:testing`, `feature:auth`, `feature:contacts`, `benchmark` | 0 | 0 | can apply the plugin as they are |

  One warning stays suppressed in `:app`'s `gms` source set, with its reason: firebase-messaging 26 deprecates
  `getToken` and `deleteToken` (and `onNewToken`) for `register`, whose token comes through the service's
  `onRegistered`. Moving there changes how the token reaches TDLib, so it waits for a `gms` phone to check it. B.

- **Weekly `optimizeStorage`** (`docs/PERFORMANCE.md` §3): the owner decided on a weekly job (2026-10-27), and it is
  in: unique periodic work per account, while charging and idle, waiting for transfers and exports, scheduled 5 s after
  the start (§2 changed with it). Left: seeing it run on a phone (`adb shell dumpsys jobscheduler | grep -A5
  storage_optimization`, or run it at once with `adb shell cmd jobscheduler run -f dev.kyralabs.pingogram <job id>`).
  Owner.
- **LeakCanary in debug builds** (`docs/PERFORMANCE.md` §9): in since 2026-10-27 (owner approval), debug builds only,
  with `check<Variant>NoDebugTools` keeping it out of `release`, `benchmarkRelease` and `nonMinifiedRelease`. It finds
  leaks only on a device, and none has run yet: no leak is known, in B's modules (`feature:calls`, `feature:media`) or
  others'. The first runs on the owner's phone (PERFORMANCE §9 says how) list them here, by module. Owner, then each
  module's owner.
- **No JankStats in debug builds** (`docs/PERFORMANCE.md` §9): not a dependency yet. A.
- **The nightly workflow** (§9 "to add in M2") does not exist. It is only useful with a device in CI. Owner.
