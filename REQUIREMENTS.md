# Pingogram — Requirements

Version: 1.0 · Status: living document · Owner: project maintainer

## 1. Product summary

Pingogram is a third-party Telegram client for Android that follows Google's current platform
and design guidance as closely as possible: **Material 3 Expressive**, dynamic color, adaptive
layouts, predictive back, edge-to-edge, modern permissions and notification conventions. It uses
Telegram's official client library, **TDLib**, so chats, media, groups, channels, bots and secret
chats behave exactly like the official apps at the protocol level, while the UI is entirely ours.

**One-line pitch:** "Telegram, the way a first-party Google app would feel."

### 1.1 Goals

- G1. A native Android experience that looks and moves like a Google-made Material 3 Expressive app.
- G2. Feature parity with the official app for everyday messaging by v1.0 (see §5).
- G3. Fast, battery-friendly, small download (≤ 50 MB per-ABI APK target, calls included; `docs/PERFORMANCE.md` §1).
- G4. Fully open source, reproducible builds, releases on GitHub (APK), later F-Droid/Play.
- G5. Privacy-respecting: no analytics, no ads, no telemetry.

### 1.2 Non-goals (for now)

- Not a fork of the official app; no MTProto implementation of our own (TDLib does that).
- No iOS, desktop, web or Wear OS targets in this repo.
- No "premium bypass", no sponsored-message hiding, no multi-account automation, no bots farm,
  nothing that breaks Telegram's API terms.
- Telegram Premium purchase flows are out of scope (Premium *features* for users who already
  have Premium are in scope where TDLib exposes them).

## 2. Naming, legal and compliance

| Item | Decision |
|---|---|
| App name | **Pingogram** |
| Repository | `pingogram-android` |
| `applicationId` | `dev.kyralabs.pingogram` (debug builds: `dev.kyralabs.pingogram.debug`) |
| Kotlin package root | `dev.kyralabs.pingogram` |
| License | **GPL-3.0-or-later** with an additional permission (GPL §7) to combine Pingogram with Google's Play services and Firebase libraries for the `gms` flavor (`LICENSE-EXCEPTION.md`, owner decision 2026-10-21, `docs/adr/0010-gpl-additional-permission-for-play-services.md`); TDLib is Boost 1.0 |
| Trademark | Must not use "Telegram" in name, icon, package or store title. "Pingogram — Telegram client" is acceptable as a *description*. |
| Telegram API | Each build uses the owner's `api_id`/`api_hash` from https://my.telegram.org. Never committed. |
| API Terms | https://core.telegram.org/api/terms and https://core.telegram.org/api/obtaining_api_id — binding. |
| Sponsored messages | Must be shown in channels where TDLib returns them (`getChatSponsoredMessages`), rendered as a clearly-labelled card, never stripped. |
| Contributor DCO | Commits must be signed off (`git commit -s`). |

## 3. Platform targets

| Item | Value | Rationale |
|---|---|---|
| `minSdk` | 26 (Android 8.0) | Notification channels, adaptive icons, modern APIs; ~97% coverage |
| `targetSdk` | 36 (Android 16) or the latest Play requirement | Play requires targeting recent APIs; edge-to-edge is enforced from 35 |
| `compileSdk` | 37 | Required by the libraries: Compose 1.12 (`ui`, `foundation`), material3 1.5.0-beta01 and navigation-compose 2.10.2 declare `minCompileSdk=37` in their AAR metadata (checked 2026-10-20); compiling against a newer SDK does not change runtime behaviour |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `x86_64` (+ universal APK) | x86 32-bit dropped |
| 16 KB page size | Required — 64-bit TDLib native libs must be built 16 KB-aligned (NDK r28+) | Play requirement for apps targeting Android 15+ |
| Language | Kotlin 2.x, Compose compiler plugin | |
| Min Android Studio | current stable | |
| JDK | 17 (build) | AGP requirement |
| Form factors | Phones first; tablets/foldables via adaptive layouts; Chromebook OK | |

## 4. Technology stack (fixed)

- **TDLib** (pinned commit/tag, see `docs/TDLIB.md`), built in CI as an AAR with JNI bindings.
- **Jetpack Compose** with **Material 3 ≥ 1.4** (`MaterialExpressiveTheme` and the Expressive
  component set), `material3-adaptive`, `material3-adaptive-navigation-suite`, `material3-window-size-class`.
  The app must comply with the **complete** Material 3 Expressive guidance (color, typography,
  shape, motion, components, layout, states, accessibility) as specified in `docs/DESIGN_SYSTEM.md`.
- **Google Sans Flex** (variable, SIL OFL 1.1) as the only typeface, driven through its variable
  axes (`wght`, `wdth`, `opsz`, `GRAD`, `slnt`, `ROND`) per style — see `docs/DESIGN_SYSTEM.md` §3.
- **Navigation Compose** with type-safe routes (Kotlin serialization).
- **Hilt** for DI, **Coroutines/Flow** for async, **DataStore** for preferences.
- **Coil 3** for images (with TDLib file paths), **Media3/ExoPlayer** for audio/video,
  **CameraX** for in-app camera (later milestone).
- **AndroidX**: `core-splashscreen`, `activity-compose`, `lifecycle`, `work` (background
  downloads), the platform's `BiometricPrompt` for the passcode lock (Android 10+, not `androidx.biometric`:
  `docs/adr/0005-passcode-lock.md`, accepted by the owner 2026-10-21), the Android Keystore for the DB key (not the
  deprecated `security-crypto`).
- **Tooling**: Gradle version catalog, convention plugins in `build-logic/`, ktlint, detekt,
  Android Lint (fatal on error), Roborazzi screenshot tests, Turbine, MockK, JUnit 5.

## 5. Functional requirements

Requirements are tagged **[M1]…[M6]** by milestone (see `docs/ROADMAP.md`). "Must" = required
for that milestone; "Should" = do if cheap; "Later" = tracked, not scheduled.

### 5.1 Onboarding & authentication [M1]

- FR-A1 Must: Welcome screen with brand, "Start messaging", link to privacy/terms.
- FR-A2 Must: Phone number entry with country picker (ISO list + flag emoji), E.164 validation,
  Telegram's `authorizationStateWaitPhoneNumber`.
- FR-A3 Must: Code entry (SMS / Telegram app / call / fragment), auto-resend timer, paste support.
- FR-A4 Must: 2FA password (`WaitPassword`) with hint, "forgot password" flow via TDLib.
- FR-A5 Must: New-account registration (`WaitRegistration`) with first/last name, ToS acceptance.
- FR-A6 Must: QR login (`WaitOtherDeviceConfirmation`) as alternative, using Expressive shapes.
- FR-A7 Must: Email verification states (`WaitEmailAddress`, `WaitEmailCode`) handled gracefully.
- FR-A8 Must: Log out, and `AuthorizationStateClosed` / `LoggingOut` handled by resetting the graph.
- FR-A9 Should: Test-DC toggle hidden behind a long-press on the version label (debug only).

### 5.2 Chat list [M2]

- FR-L1 Must: Main chat list from `getChats`/`updateChatPosition`, sorted by TDLib position.
- FR-L2 Must: Each row: avatar (photo or initials with M3 color roles), title, verified/scam
  badges, last message preview with sender, time, unread count, mute/pin indicators, draft marker,
  typing indicator, online dot for users.
- FR-L3 Must: Chat folders (TDLib `chatFolders`) as a secondary row of **M3 Expressive button
  group / segmented chips** below the top app bar; "All chats" first; archive entry.
- FR-L4 Must: Swipe actions (archive, mark read / unread, pin, mute, delete) with haptics.
- FR-L5 Must: Pull-to-refresh is *not* needed (TDLib pushes updates) — show a subtle connection
  state pill ("Connecting…", "Updating…", "Waiting for network") in the app bar instead.
- FR-L6 Must: Search (global): chats, contacts, messages, via `searchChats`, `searchMessages`,
  with recent searches, using the M3 search bar, opened from the search button beside the floating
  navigation bar (the rail's button on larger windows; `docs/DESIGN_SYSTEM.md` §6.1).
- FR-L7 Must: a "New chat" FAB (medium extended, collapsing on scroll) opening a New chat screen with new
  group / new channel / new contact and the contacts, with a container transform (`docs/DESIGN_SYSTEM.md` §6).
- FR-L8 Should: Selection mode (long-press) with bulk actions in a **floating toolbar**.
- FR-L9 Should: Archived chats screen with the same list component.

### 5.3 Conversation screen [M2–M3]

- FR-C1 Must: Paginated message list (`getChatHistory`), reverse layout, jump-to-bottom FAB,
  unread divider, date headers (sticky), scroll-to-message from search/reply.
- FR-C2 Must: Message bubbles with M3 colors (`primaryContainer` outgoing, `surfaceContainerHigh`
  incoming), grouped consecutive messages with Expressive rounded shapes (no tails, `docs/DESIGN_SYSTEM.md` §4), read receipts, edited label,
  forward header, reply quote, link previews, reactions row.
- FR-C3 Must: Text with entities (bold, italic, code, pre, spoiler, links, mentions, hashtags,
  custom emoji placeholder), selectable text, copy, translate (later).
- FR-C4 Must: Photos, albums (grid), videos (inline player), GIFs/animated, stickers (static +
  animated TGS via rlottie or Lottie-compatible renderer), voice (waveform + player),
  documents, locations, contacts, polls, dice, service messages.
- FR-C5 Must: Composer: multi-line text, formatting menu, attach sheet (gallery via Photo
  Picker, file, camera, location, contact, poll), voice record (hold), emoji & sticker panel,
  send-as (channels), silent send, schedule send, reply/edit bar, draft sync.
- FR-C6 Must: Message actions on long-press, in a menu anchored to the lifted message (a bottom sheet with
  TalkBack or in short windows): reply, copy, forward, edit, pin, delete (for me / for all), select, report,
  save to downloads, react.
- FR-C7 Must: Reactions picker (quick row + full grid) with Expressive spring animations.
- FR-C8 Must: Chat header → profile; typing/online status; muted state; call buttons (later).
- FR-C9 Should: Threads / comments, topics in forums, pinned messages bar with cycling.
- FR-C10 Should: Media viewer (fullscreen, zoom, swipe between media, share/save).

### 5.4 Contacts, profiles, groups & channels [M3–M4]

- FR-P1 Must: Contacts list with sections, invite, add/edit contact.
- FR-P2 Must: User profile: photo carousel, bio, username, phone (if visible), shared media
  tabs, notifications toggle, block/report, start secret chat.
- FR-P3 Must: Group/channel info: description, members (paged), admins, links, settings
  (title, photo, permissions, admin rights, invite links, slow mode) where TDLib allows.
- FR-P4 Must: Create group / channel / supergroup, add members, leave/delete.
- FR-P5 Should: Join via link/QR, public link search, boosts (read-only).

### 5.5 Settings [M3]

- FR-S1 Must: Account (profile photo, name, username, bio, phone change), Privacy & Security
  (last seen, photo, calls, groups, blocked users, passcode lock w/ biometrics, 2FA, active
  sessions), Notifications (per-type channels, exceptions, sounds), Data & Storage (auto-download
  per network, storage usage via `getStorageStatistics`, clear cache), Chat settings (theme mode,
  color theme (`docs/adr/0016-color-themes.md`), message corner radius, font size, chat wallpaper—solid/gradient
  only), Language (per-app language API), About (version, licenses, GitHub link).
- FR-S2 Must: Appearance: System / Light / Dark, a color theme (wallpaper colors on Android 12+, the brand's or
  curated Material 3 themes, `docs/adr/0016-color-themes.md`), AMOLED black option, contrast levels
  (standard/medium/high per M3).

### 5.6 Notifications & background [M3]

- FR-N1 Must: Push via TDLib's long-polling in a foreground-less model (TDLib keeps a
  connection); on Android 13+ request `POST_NOTIFICATIONS` with rationale.
- FR-N2 Must: Notification channels: Private chats, Groups, Channels, Calls, Other. Conversation
  style (`MessagingStyle`), shortcuts for top chats (direct share: [M4], owner 2026-10-21), reply/mark-read actions,
  bubbles opt-in, grouped per chat, respect mute/exceptions from TDLib.
- FR-N3 Should: FCM push support (TDLib `registerDevice` with `deviceTokenFirebaseCloudMessaging`)
  behind a build flag so F-Droid builds stay Google-free.
- FR-N4 Must: Downloads/uploads continue via `WorkManager` + a foreground service with
  `dataSync` type when the app is backgrounded.

### 5.7 Calls [M5]

- FR-V1 Should: Voice calls via TDLib + libtgvoip/tgcalls (`createCall`, `acceptCall`) with
  CallKit-style full-screen notification, speaker/mute, encryption emoji.
- FR-V2 Should [M5.1]: 1:1 video calls, then group voice chats and watching live streams (`docs/adr/0019-video-and-group-calls-scope.md`); group video later.

### 5.8 Secret chats [M4]

- FR-E1 Should: Create/accept secret chat, self-destruct timer, screenshot blocking flag,
  encryption key visualization. TDLib does the crypto; UI must show the lock state clearly.

### 5.9 Polish & extras [M6]

- Stories (view), translation, saved messages tabs, message search in chat, chat export,
  widgets (Glance), App Shortcuts, Wear/auto notifications pass-through, tablet list-detail.

## 6. Non-functional requirements

### 6.1 Performance ("optimized Telegram")

Pingogram must be measurably faster and lighter than the official app on the same device.
Binding budgets and techniques are in `docs/PERFORMANCE.md`; headline targets:

- Cold start → chat list ≤ 1.2 s, warm start ≤ 400 ms (Macrobenchmark, Baseline + Startup Profiles shipped).
- Chat list / conversation scroll at the display's native rate (60/90/120 Hz): P90 frame ≤ 8–10 ms, jank < 1%.
- Open a cached chat ≤ 150 ms; optimistic send ≤ 50 ms.
- Memory ≤ 120 MB idle, ≤ 250 MB in a 10k-message media chat; APK ≤ 50 MB arm64 (raised from 35 MB for the call engine, owner 2026-10-22).
- Background battery ≤ 1.5% / 8 h; idle data ≤ 2 MB / 24 h; no polling, no periodic jobs < 15 min.
- Compose: immutable state, keyed/contentTyped lazy lists, zero off-screen recompositions,
  animations in draw/layout phase; TDLib: suspendable, batched, prioritized, cancellable calls.

### 6.2 Security & privacy

- TDLib database encrypted with a key generated in the Android Keystore
  (`databaseEncryptionKey`); `useSecretChats = true`.
- `android:allowBackup="false"`, exclude TDLib dirs from any backup.
- Optional app passcode with biometric prompt; screen obscured in recents when enabled.
- Network only to Telegram data centers (via TDLib) and user-requested links; no third-party
  network calls (the `gms` flavor's push service, FR-N3, is the one exception; `foss` has none). `networkSecurityConfig` with cleartext disabled.
- Debug logs never contain message text, phone numbers or tokens; TDLib verbosity ≤ 2 in release. Logs and the last
  crash stay on the device; "Send feedback" redacts them into a report the user reads and shares themselves
  (`docs/TESTING_GUIDE.md`), never sent by the app.
- Contacts sync (FR-P1, `docs/adr/0011-contacts-sync.md`, approved by the owner 2026-10-21) is opt-in and off by
  default: only with it on are the names and phone numbers of the phone's contacts read (`READ_CONTACTS`) and sent to
  Telegram (its contact import), when it is turned on, when Contacts opens while it is on and on "Sync now", never in
  the background; turning it off deletes the imported numbers from Telegram.
- The camera (`CAMERA`, no camera is required to install) has two uses:
  - The QR code scanner (FR-P5, `docs/adr/0012-camerax-and-zxing-qr-scanner.md`, approved by the owner 2026-10-21),
    asked when the scanner opens, after it says why: frames are decoded on the device by ZXing and dropped, nothing
    the camera sees is kept or sent, and only the text of a Telegram link reaches TDLib. Scanning from an image uses
    the Photo Picker, without a permission.
  - Video calls (FR-V2, `docs/adr/0019-video-and-group-calls-scope.md`, approved for 1:1 video), asked when the user
    turns the camera on in a call: the video goes to the other person only, end-to-end encrypted like the call's
    audio, while the camera is on, and is not kept.

### 6.3 Accessibility (Google standards)

- All interactive elements ≥ 48 dp, `contentDescription` on icon buttons, roles set on custom
  components, TalkBack-navigable message list with merged semantics per message.
- Contrast ≥ 4.5:1 text, ≥ 3:1 for large text/icons across all three M3 contrast levels.
- Font scaling to 200% without clipping; `sp` for text, `dp` for layout; no fixed heights on text.
- Reduced-motion: respect `Settings.Global.ANIMATOR_DURATION_SCALE`; no essential info in motion only.

### 6.4 Internationalization

- English source strings; RTL layouts verified (Arabic/Hebrew); per-app language selector;
  plurals via ICU; dates via `java.time` + `DateTimeFormatter` localized; numbers via
  `NumberFormat`. Telegram's own translations (`getLanguagePackStrings`) considered later.
- Translations come from Hosted Weblate (owner decision 2026-10-22), never machine-translated into the repository; a
  language ships once it covers 80% of the strings (`docs/TRANSLATING.md`). Pseudo-locales `en-XA` and `ar-XB` in debug
  builds check long text and right to left.

### 6.5 Reliability

- Handle `FLOOD_WAIT_x` by backing off and surfacing a friendly countdown.
- Handle network loss, `updateConnectionState`, DC migration transparently.
- Crash-free sessions ≥ 99.5% on internal testers before a public release.

## 7. Google platform standards checklist

Every screen must satisfy this list before merge:

- [ ] Material 3 Expressive theme (`MaterialExpressiveTheme`), expressive `MotionScheme`, full compliance audit (`docs/DESIGN_SYSTEM.md` §12)
- [ ] Google Sans Flex type scale incl. emphasized styles; `opsz` = size; `GRAD` 0 (25 with high contrast)
- [ ] Dynamic color on Android 12+ with brand-seeded fallback; tonal palettes, not hard-coded hex
- [ ] Edge-to-edge (`enableEdgeToEdge()`), insets via `WindowInsets` / `Scaffold` content padding
- [ ] Predictive back (`android:enableOnBackInvokedCallback="true"`, `PredictiveBackHandler`
      for custom sheets/drawers)
- [ ] Adaptive: `WindowSizeClass`; list-detail on expanded width; no `Configuration` hacks
- [ ] Themed (monochrome) adaptive icon; `core-splashscreen`
- [ ] Runtime permissions with rationale: notifications, camera, mic, location, contacts; use
      Photo Picker instead of storage permission
- [ ] Foreground services declare `foregroundServiceType`
- [ ] Per-app language, large-screen keyboard/mouse support, state restoration (rotation, process death)
- [ ] Accessibility (§6.3), haptics via `HapticFeedbackType`, no custom vibration patterns > 50 ms
- [ ] No deprecated APIs; lint `NewApi`, `UnusedResources`, `ExtraTranslation` fatal (`MissingTranslation`
      informational: a language ships at 80% translated, `docs/adr/0020-translations-on-weblate.md`)
- [ ] Performance budgets in `docs/PERFORMANCE.md` met for the screen
- [ ] Android 16 Live Updates (`ProgressStyle` notification) for long uploads/downloads where available

## 8. Release requirements

- Semantic versioning `MAJOR.MINOR.PATCH`; tag `vX.Y.Z` triggers the release workflow.
- Release artifacts: `pingogram-X.Y.Z-arm64-v8a.apk`, `-armeabi-v7a.apk`, `-x86_64.apk`,
  `-universal.apk` (`foss`), the same four with a `-gms` suffix (`gms`, push through Firebase), `SHA256SUMS.txt`,
  and the `.aab` bundles of both flavors attached as a workflow artifact.
- Signed with the owner's release keystore (GitHub secret); debug builds use a committed
  debug keystore so anyone can build.
- Changelog section auto-extracted from `CHANGELOG.md` into the GitHub Release body.
- Pre-release tags (`v0.x.y`, `-beta.N`) are marked "pre-release" on GitHub.

## 9. Acceptance criteria for v1.0

1. A new user can install the universal APK from GitHub Releases, log in with phone + code
   (+2FA), and use chats, groups, channels, media, stickers, voice messages, reactions,
   notifications and settings without touching the official app.
2. Internal audit: all §7 boxes ticked on every screen, including the M3 Expressive audit and performance budgets.
3. CI green on `main`; `./gradlew check` and Macrobenchmark thresholds pass.
4. No crashes in a 7-day dogfood with ≥ 10 testers.
5. Telegram API terms compliance reviewed by the owner and documented in the release notes.
