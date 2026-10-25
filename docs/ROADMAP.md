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
- [x] App icon (adaptive + monochrome), from the owner's artwork in `art/`
- [x] `build-logic` detekt rule `PingogramHardcodedToken` (no literal colors/sizes/tweens in features)
- [x] `benchmark/` module skeleton with `StartupBenchmark` and Baseline Profile generation
- [x] `core:tdlib` — `fetchTdlib` Gradle task, `TdlibClient`, `AuthState`, `ConnectionState`,
      database key in Keystore, parameters provider
- [x] `:app` — `MainActivity` with `enableEdgeToEdge`, `NavHost`, Hilt, `NavigationSuiteScaffold`
      shell with placeholder tabs
- [x] ktlint, detekt, lint config; `./gradlew check` green
- [x] CI workflow green on PR; TDLib workflow produces an AAR release
- [x] Release workflow produces signed APKs on tag (first release: `v0.1.0`, with M1)
- [x] README badges, CONTRIBUTING, issue templates in place

## M1 — Authentication → `v0.1.0`

- [x] Welcome, phone + country picker, code entry (all code types), 2FA password, forgot
      password, registration, QR login, email states, logout, `Closed` handling
- [x] FLOOD_WAIT countdown UI, offline banner, error mapping
- [x] Unit tests for `AuthViewModel` against `FakeTdlibClient` for every state transition
- [x] Screenshot tests for each auth screen (light/dark/dynamic)

## M2 — Chat list + read-only conversation → `v0.2.0`

- [x] `ChatRepository`, `UserRepository`, `MessageRepository`, `FileRepository` with incremental
      update application and tests
- [x] Chat list with folders, badges, typing/online, swipe actions, selection toolbar, archive
- [x] Global search (chats, contacts, messages)
- [x] Conversation: paginated history, date headers, bubbles with entities, read status,
      replies/forwards, photos/videos/GIFs/stickers (static)/voice (playback)/documents/polls
      (view)/service messages, reactions display, link previews
- [x] Media viewer with shared-element transition
- [x] Mark-as-read (`viewMessages`) and `openChat/closeChat` lifecycle
- [x] Macrobenchmark baseline for startup + chat-list scroll; Baseline Profile generated (2026-10-22, the owner's
      Galaxy S24 Ultra, `docs/PERFORMANCE.md` §9 "Baseline"). Startup is well within budget; chat list and
      conversation scrolling are not yet (frame P90 10.4 ms and 9.1 ms, over 10% of frames late), tracked below

## M3 — Full messaging, settings, notifications → `v0.3.0`

- [x] Composer: text + formatting, reply/edit, attach (photo picker, camera, file, location,
      contact, poll), voice recording, emoji + sticker panel (animated TGS), send-as, silent and
      scheduled send, drafts
- [x] Message actions, reactions picker, forward sheet, delete, pin, select
- [x] Settings: account, privacy & security (incl. passcode/biometric, sessions), notifications,
      data & storage, appearance, language, about + OSS licenses
- [x] Notifications via TDLib notification groups, channels, MessagingStyle, conversation shortcuts,
      reply/mark-read actions, Android 13 permission flow (direct share moved to M4 by the owner, 2026-10-21)
- [x] WorkManager background downloads/uploads with foreground service (`dataSync`)
- [x] `gms` flavor with FCM `registerDevice` (in every GitHub release next to `foss` from 0.3.0, `LICENSE-EXCEPTION.md`)
- [x] Sponsored messages in channels (`REQUIREMENTS.md` §2; audit G01)
- [x] Material 3 Expressive alignment: the Must and Should items of
      `docs/audit/m3-expressive-gap-analysis.md` (decided 2026-10-19), density first (G63–G69). G23's rest (the
      conversation title opens the profile) is done in M4 with the profile; G53 (dialog widths) needs a device check
      by the owner

## M4 — Contacts, profiles, groups, channels, secret chats → `v0.4.0`

- [x] Contacts list, add/edit/invite; user profile; shared media tabs; the conversation title opens the profile
      (audit G23's rest)
- [x] Group/channel info, members, admins, permissions, invite links, create/leave/delete
- [x] Forum topics, comments threads, pinned messages bar
- [x] Secret chats: create/accept, TTL, lock indicators (accepting is TDLib's own, `docs/TDLIB.md`)
- [x] Deep links (`tg://`, `t.me`) via `getInternalLinkType` (scope and what is left out: `docs/adr/0013-deep-links.md`)
- [x] Direct share: share text, links and files from other apps into a chat (a share target with a chat picker and a
      prefilled composer; the conversation shortcuts as Direct Share targets), moved from M3 (owner, 2026-10-21);
      `docs/adr/0014-direct-share.md`

## M5 — Calls → `v0.5.0`

- [x] Voice calls (TDLib + NTgCalls, `docs/adr/0015-call-media-engine.md`; tgcalls has no Android build outside
      Telegram's apps), full-screen call notification, in-call UI with Expressive shapes for avatar/encryption emoji,
      audio routing, Telecom (`docs/adr/0018-calls-and-telecom.md`)
- [x] Decide on video calls / group calls scope (`docs/adr/0019-video-and-group-calls-scope.md`: all feasible; 1:1
      video next, then group voice chats and live streams)

## M5.1 — Video and group calls (after `v0.5.0`)

- [x] 1:1 video calls (`docs/adr/0019-video-and-group-calls-scope.md`): camera, self-view, switch camera, camera on
      and off, picture-in-picture, lower resolutions on mobile data, roaming, Data Saver and weak connections
- [x] Video call buttons in the conversation's top bar and the profile: one call button with a voice and video menu
      (owner, 2026-10-23), and "Call back" keeping the call's kind
- [ ] The video frame path measured on a device (CPU and memory during a 720p call, `docs/PERFORMANCE.md`)
- [x] Group voice chats: join, speak, mute, raise hand, leave; start one as an admin (`docs/adr/0023-group-voice-chats-and-live-streams.md`;
      who is speaking from Telegram's recent speakers until the owner decides on audio levels)
- [x] Live streams: watching, with picture-in-picture (ADR 0023)
- [x] Conference calls: joining by a `t.me/call` link, with the emojis to compare (`docs/adr/0025-conference-calls.md`,
      NTgCalls patched for TDLib's encryption)
- [ ] Conference calls: upgrading a 1:1 call, inviting, invitations in chats and Recent calls; the per-frame
      encryption measured on a device (`docs/PERFORMANCE.md` §9)

## M6 — Polish → `v1.0.0`

What is left for 1.0, item by item and with owners, is in `docs/audit/v1-readiness.md`.

- [x] Glance widget of recent chats (#144), app shortcuts
- [x] Stories viewer (read-only, #169), in-chat search (#164), chat export (#166), tablet list-detail polish (#171:
      keyboard and mouse in the chat list, the conversation and the viewers); also message translation (#165), custom
      emoji (#172) and the conversation taking the Chat appearance settings (#170)
- [ ] Accessibility audit on every screen: done outside the chat screens (#154, `docs/audit/accessibility.md`); the
      chat screens' findings A1–A15 and checks, 200% goldens in every module, then TalkBack and Accessibility Scanner
      on a device
- [ ] Performance budget verified (`REQUIREMENTS.md` §6.1); the APK size budget is checked by every pull request
      (`ci.yml`) and every release (`release.yml`). Scrolling misses its budget in the first baseline (`docs/PERFORMANCE.md` §9): the chat list's
      frame P90 10.4 ms against 8 ms, the conversation's late frames over 1%; #150 lowered the cost on the JVM, and
      the owner's phone measures it again. Open a chat ≤ 150 ms: `OpenChatBenchmark` and the `openChat` trace
      section are in, not yet run on the phone
- [x] Benchmark thresholds: on the owner's phone before each release (`tools/benchmark/compare.py`, `docs/RELEASE.md`
      step 2); a device in CI is still open
- [ ] Localization: at least 5 languages. The pipeline is in (Hosted Weblate, #152, `docs/TRANSLATING.md`); no
      language is translated yet
- [x] Google Play readiness for `gms` (`docs/PLAY_STORE.md`, #153)
- [ ] Google Play listing (the owner, `docs/PLAY_STORE.md` §8). Deleting the account from the app, which Play requires,
      is in (Settings → Privacy and security → Delete account), and so is its web page
      (`docs/website/delete-account.md`, published at `https://pingogram.kyralabs.dev/delete-account/`)
- [ ] F-Droid metadata (`metadata/` / `fastlane/`) and reproducible-build notes.
  - Done:
    - the fastlane listing: texts, icon, feature graphic, screenshots;
    - the notes in `docs/RELEASE.md`, with reproducibility checked on one machine and across checkout directories;
    - unsigned release builds (`-Ppingogram.unsignedRelease=true`);
    - a draft recipe for review (`docs/fdroid/dev.kyralabs.pingogram.yml`).
  - Left for F-Droid's main repository, which builds from source:
    - the Telegram API values in its public recipe (owner decision);
    - TDLib's AAR packaged in the recipe;
    - NTgCalls without prebuilt WebRTC, or an F-Droid build without calls;
    - a check on another machine;
    - the submission.
- [x] Dogfood support: Send feedback and `docs/TESTING_GUIDE.md` (#151)
- [ ] 7-day dogfood with ≥ 10 testers, crash-free ≥ 99.5%
- [ ] Telegram API terms reviewed and signed off by the owner (`docs/API_TERMS.md`)

## Backlog (unscheduled)

Group video calls, Premium features surface, custom emoji packs management, chat themes/wallpapers (images), bot
reply keyboards and web apps (Mini Apps WebView), payments, business features, Wear OS companion.

Features other clients have, with verdicts and priorities: `docs/audit/feature-gaps-octogram.md` (2026-10-24).
