# Roadmap

Work the milestones in order. Each milestone ends with a tagged pre-release (`v0.N.0`) built by
the release workflow so the owner can install and test on a phone.

## M0 — Project foundation → `v0.0.1`

- [x] Gradle project: version catalog, `build-logic` convention plugins, modules from
      `docs/ARCHITECTURE.md` (empty where needed), Kotlin 2.x, AGP latest stable, JDK 17
- [x] `secrets.properties` loading with clear failure if missing; `secrets.properties.example`
- [x] Debug keystore committed; release signing from env/`keystore.properties`
- [x] `core:ui` — `PingogramTheme` (M3 Expressive, dynamic color, contrast, AMOLED), full
      Google Sans Flex type scale (15 styles + 15 emphasized, variable axes per style), shapes
      + `MaterialShapes` helpers, `LocalPingogramColors`, preview helpers, splash
- [ ] App icon (adaptive + monochrome) — waiting for the owner's artwork
- [x] `build-logic` detekt rule `PingogramHardcodedToken` (no literal colors/sizes/tweens in features)
- [x] `benchmark/` module skeleton with `StartupBenchmark` and Baseline Profile generation
- [x] `core:tdlib` — `fetchTdlib` Gradle task, `TdlibClient`, `AuthState`, `ConnectionState`,
      database key in Keystore, parameters provider
- [ ] `:app` — `MainActivity` with `enableEdgeToEdge`, `NavHost`, Hilt, `NavigationSuiteScaffold`
      shell with placeholder tabs
- [x] ktlint, detekt, lint config; `./gradlew check` green
- [x] CI workflow green on PR; TDLib workflow produces an AAR release
- [ ] Release workflow produces signed APKs on tag — build verified locally with a throwaway key;
      the first real run needs the owner's signing/API secrets and a `v0.0.1` tag
- [x] README badges, CONTRIBUTING, issue templates in place

## M1 — Authentication → `v0.1.0`

- [ ] Welcome, phone + country picker, code entry (all code types), 2FA password, forgot
      password, registration, QR login, email states, logout, `Closed` handling
- [ ] FLOOD_WAIT countdown UI, offline banner, error mapping
- [ ] Unit tests for `AuthViewModel` against `FakeTdlibClient` for every state transition
- [ ] Screenshot tests for each auth screen (light/dark/dynamic)

## M2 — Chat list + read-only conversation → `v0.2.0`

- [ ] `ChatRepository`, `UserRepository`, `MessageRepository`, `FileRepository` with incremental
      update application and tests
- [ ] Chat list with folders, badges, typing/online, swipe actions, selection toolbar, archive
- [ ] Global search (chats, contacts, messages)
- [ ] Conversation: paginated history, date headers, bubbles with entities, read status,
      replies/forwards, photos/videos/GIFs/stickers (static)/voice (playback)/documents/polls
      (view)/service messages, reactions display, link previews
- [ ] Media viewer with shared-element transition
- [ ] Mark-as-read (`viewMessages`) and `openChat/closeChat` lifecycle
- [ ] Macrobenchmark baseline for startup + chat-list scroll; Baseline Profile generated

## M3 — Full messaging, settings, notifications → `v0.3.0`

- [ ] Composer: text + formatting, reply/edit, attach (photo picker, camera, file, location,
      contact, poll), voice recording, emoji + sticker panel (animated TGS), send-as, silent and
      scheduled send, drafts
- [ ] Message actions, reactions picker, forward sheet, delete, pin, select
- [ ] Settings: account, privacy & security (incl. passcode/biometric, sessions), notifications,
      data & storage, appearance, language, about + OSS licenses
- [ ] Notifications via TDLib notification groups, channels, MessagingStyle, shortcuts/direct
      share, reply/mark-read actions, Android 13 permission flow
- [ ] WorkManager background downloads/uploads with foreground service (`dataSync`)
- [ ] `gms` flavor with FCM `registerDevice` (optional, off by default in GitHub releases)

## M4 — Contacts, profiles, groups, channels, secret chats → `v0.4.0`

- [ ] Contacts list, add/edit/invite; user profile; shared media tabs
- [ ] Group/channel info, members, admins, permissions, invite links, create/leave/delete
- [ ] Forum topics, comments threads, pinned messages bar
- [ ] Secret chats: create/accept, TTL, lock indicators
- [ ] Deep links (`tg://`, `t.me`) via `getInternalLinkType`

## M5 — Calls → `v0.5.0`

- [ ] Voice calls (TDLib + tgcalls), full-screen call notification, in-call UI with Expressive
      shapes for avatar/encryption emoji, audio routing
- [ ] Decide on video calls / group calls scope (ADR)

## M6 — Polish → `v1.0.0`

- [ ] Stories viewer (read-only), translations, in-chat search, chat export, Glance widget,
      app shortcuts, tablet list-detail polish, accessibility audit on every screen
- [ ] Performance budget verified (`REQUIREMENTS.md` §6.1); APK size budget
- [ ] Localization: at least 5 languages via Crowdin/Weblate (owner decision)
- [ ] F-Droid metadata (`metadata/` / `fastlane/`) and reproducible-build notes
- [ ] 7-day dogfood with ≥ 10 testers, crash-free ≥ 99.5%

## Backlog (unscheduled)

Video calls, group calls, live streams, Premium features surface, custom emoji packs
management, chat themes/wallpapers (images), bots inline keyboards & web apps (Mini Apps
WebView), payments, business features, Wear OS companion.
