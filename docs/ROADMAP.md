# Roadmap

Work the milestones in order. Each milestone ended with a tagged pre-release (`v0.N.0`) built by
the release workflow so the owner can install and test on a phone; from `v1.0.0` on, releases are full releases.

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
- [x] Conference calls: upgrading a 1:1 call, inviting from the call, invitations in chats and Recent calls (2026-10-27)
- [ ] Conference calls: the per-frame encryption measured on a device (`tools/perfetto/conference-call.sh`,
      `docs/PERFORMANCE.md` §9)

## M6 — Polish → `v1.0.0`

**v1.0.0 was released on 2026-10-29 by the owner's decision, with these M6 items still open** (each unticked below,
as it was): the accessibility audit on a device; the performance budget verified; localization in at least 5
languages; the Google Play listing; the Galaxy Store and AppGallery listings; F-Droid's main repository; the 7-day
dogfood; the owner's sign-off on Telegram's API terms. M5.1's two device measurements (the video frame path, the
conference calls' encryption) are open too. They are finished in later releases.

What is left, item by item and with owners, is in `docs/audit/v1-readiness.md`.

- [x] Glance widget of recent chats (#144), app shortcuts
- [x] Stories viewer (read-only, #169), in-chat search (#164), chat export (#166), tablet list-detail polish (#171:
      keyboard and mouse in the chat list, the conversation and the viewers); also message translation (#165), custom
      emoji (#172) and the conversation taking the Chat appearance settings (#170)
- [ ] Accessibility audit on every screen (`docs/audit/accessibility.md`): done in code. That covers the screens outside
      the chats (#154), the chat screens' findings A1–A15, checks in every feature module and 200% goldens in every
      feature module. Left: TalkBack and Accessibility Scanner on a device
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
- [ ] Galaxy Store and AppGallery listings (the owner, `docs/STORES.md`): Galaxy Store gets `gms`, AppGallery `foss`
      (Huawei push is blocked on Telegram's side, `docs/adr/0033-hms-push-for-huawei-phones.md`). AppGallery is done:
      published on 2026-11-18, in every country it serves except India. Galaxy Store is left
- [ ] F-Droid metadata (`metadata/` / `fastlane/`) and reproducible-build notes.
  - Done:
    - the fastlane listing: texts, icon, feature graphic, screenshots;
    - the notes in `docs/RELEASE.md`, with reproducibility checked on one machine and across checkout directories;
    - unsigned release builds (`-Ppingogram.unsignedRelease=true`);
    - a draft recipe for review (`docs/fdroid/dev.kyralabs.pingogram.yml`).
  - Left for F-Droid's main repository, which builds from source:
    - the Telegram API values in its public recipe (owner decision);
    - TDLib's AAR packaged in the recipe;
    - the call engine and WebRTC built from source, as Telegram FOSS and Telegram X do (`docs/adr/0030-calls-in-the-fdroid-build.md`: no build without calls; the spike of 2026-10-27 found it feasible, the engine to build on it is next);
    - a check on another machine;
    - the submission.
- [x] Dogfood support: Send feedback and `docs/TESTING_GUIDE.md` (#151)
- [ ] 7-day dogfood with ≥ 10 testers, crash-free ≥ 99.5%
- [ ] Telegram API terms reviewed and signed off by the owner (`docs/API_TERMS.md`)

## Next version (after `v2.0.0`)

The owner's decision of 2026-11-08: these items from the Settings pages' redesign boards (`docs/DESIGN_SYSTEM.md`
§13.10, "Not yet") come in the version after 2.0.0, each once the owner has decided how it should work.

- [x] Folder tabs' style: text, icon and text, or icon only (Appearance → Chat list)
- [x] Sticker search in Settings → Stickers and emoji
- [x] "Suggest stickers by emoji" and "Loop animated stickers" switches
- [x] "Start a call" in Recent calls (`feature:calls`): a contact's voice or video call, or a new group call
- [x] "Check for updates" in About hidden when an app store installed Pingogram (owner, 2026-11-08)

## After v2.3.0

The owner's decision of 2026-11-17 (`REQUIREMENTS.md` §5.10, ADR 0039):

- [x] Backup and restore (ADR 0039)
- [x] Mentions & replies page (FR-X1)
- [x] Snippets (FR-X2)
- [x] Add to calendar, open in maps (FR-X3)
- [x] Skip silence in voice and round video messages (FR-X4)
- [x] Channel feed (FR-X5)

## After v2.4.0

The owner's decision of 2026-11-19:

- [x] Arrow keys and Enter between the Channel feed's cards (FR-X5): a hardware keyboard moves between the cards
  and Enter opens the post in its channel

## v2.5.0

The owner's decision of 2026-11-23: no 2.4.1; the fixes from the code and performance review of 2026-11-23, the
performance work behind 2.4.0's accepted benchmark results and the small features ship together. A serious problem in
2.4.0 before then is fixed on `hotfix/2.4.1` from the `v2.4.0` tag (`docs/RELEASE.md`, Hotfix).

Fixes:
- [x] The call service promotes itself to the foreground at once with a minimal notification, then updates it
- [x] A 1:1 call that ends while it connects closes its native session (the microphone stays open today); a group call
  join survives its screen closing
- [x] An account slot is not reused while its old client is still closing
- [x] Crash hardening: duplicate keys in GIF search, a malformed animated sticker, the scanner's camera start, the order
  of call signaling packets (check each first)
- [x] Opening a chat with a photo wallpaper decodes the photo once, not twice
- [x] Pull requests run the release build's lint, as `release.yml` does

Performance (the accepted results in #876's description, and the review):
- [x] Cold start: the account registry read off the main thread's path
- [x] Cold start: the profiles regenerated from a clean install (`tools/benchmark/generate-profiles`), so the startup
  profile holds startup code only
- [x] The users map projected per screen, off the main thread
- [x] Conversation: reply quotes in one update, transfer progress out of the screen's state, a bounded history window
- [x] Files: a state per file, no `getFile` for a file already on disk
- [x] Chat list scrolling within its budget (P90 7.5 ms against 8 ms in the release benchmark), the bars' shadows
  kept
- [x] Media viewer decode size, sticker frame timing, a cheaper wallpaper blur (minithumbnails stay as they are: cached,
  one 40 px decode each)
- [x] The late-frames budget revisited (ADR 0042, a stutter budget); profile and benchmark journeys widened
- [x] A new benchmark baseline recorded on the release build
- [x] `docs/PERFORMANCE.md` matches the code (download limit, history window, animated images)

Features:
- [x] "Typing…" sent to the chat, with a setting to turn it off
- [x] Incoming bubbles stand out in light theme
- [x] A compact chat list
- [x] Drafts: every unsent draft on one page
- [x] Birthdays: upcoming contacts' birthdays, with "Remind me" and "Add to calendar"
- [x] Birthday reminders in the backup, like bookmarks and message reminders
- [x] Composer: silent sending by default, a confirmation before a bot command, undo and redo
- [x] Selection: save or unpin several messages at once; what a double tap does, for incoming and outgoing messages
- [x] Folder tabs without unread counters, as an option
- [x] Incognito keyboard: the keyboard does not learn from what is typed in Pingogram
- [x] Links without tracking parameters, when sent and opened
- [x] Adding a contact does not share the phone number unless chosen

## v2.6.0

The owner's decisions of 2026-11-23 and 2026-11-24, in two parts that can go side by side. Every feature can be turned
off in Settings (`REQUIREMENTS.md` §5.10).

The rest of the review of 2026-11-23, in this order:
1. [ ] `MainActivity` handles rotation, folding, resizing and dark mode without being recreated (`configChanges`, as
   the call and media activities already do), each screen checked (owner, 2026-11-24)
2. [ ] ntgcalls linked with a version script, `--gc-sections` and packed relocations: about 1–1.5 MB off the arm64 APK
   (owner, 2026-11-24)
3. [ ] detekt rules for what is banned today only by discipline: `GlobalScope`, `runBlocking`, `composed {}`,
   `SubcomposeAsyncImage`
4. [ ] The Compose stability configuration made true: the plain `List`, `Set` and `Map` properties of
   `core.domain.model` become immutable collections, or the configuration stops calling them stable
5. [ ] `docs/PERFORMANCE.md` says how budgets are checked (`compare.py` on the owner's phone before each release, not
   CI); a memory benchmark for "chat list idle"; JankStats added where the doc names it, or removed from the doc
6. [ ] `ChatViewModel` split into parts, with no change in behaviour, before the features below touch it
7. [ ] The message menu's blur stops re-rendering while stickers and typing dots animate under it
8. [ ] Conversation: no bubble copies on each update while translation or search is on; one download tap does not
   rebuild every bubble; item keys without allocation; anchor lookups without scanning every item; the sponsored-view
   reporter without work per scroll frame (it reports exactly what it does today)
9. [ ] Rendering: no path clips on scrolling content (polygon avatars, custom emoji; §5); shared-element setup only on
   the tapped bubble; no hidden emoji text under static stickers; the video player's buffer sized for local files; call
   video set up off the main thread; the emoji panel's cells cached
10. [ ] The channel feed local-first (§3): what TDLib has on the phone before any request to the server
11. [ ] Chat list: sub-flows shared, not collected several times; one swipe state per row; no exception thrown on each
    navigation; the widget's chat picker off the main thread
12. [ ] Profile and benchmark journeys for calls and stories
13. [ ] Measured on the owner's phone, then fixed only if slow: the passcode's PBKDF2, the transcription resampler's
    allocations, QR scanning cropped to its frame
14. [ ] Start: the settings read once and kept, not awaited by each activity start (`main.awaitSettings`: 38 ms on a
    warm start in v2.3.0, 56 ms in v2.5.0, whose warm start to the chat list took 181 ms against 2.4.0's 143)
15. [ ] Start: the wait for TDLib's sign-in state (`PG:loginKnown`, about 220 ms of v2.5.0's 612 ms cold start)
16. [x] Start: v2.3.0 measured on the same account, to separate the database's growth from the code (v2.5.0's cold
    start to the first frame is 456 ms, 2.3.0's baseline 383)

Translations (the owner, on Weblate):
- [ ] Every module a Weblate component, not only Settings (`docs/TRANSLATING.md`), so a language can reach the 80% it
  needs to ship
- [ ] Brazilian Portuguese added

Features, in this order:
- [x] Notes and names on this phone: a private note on any chat, and a chat's own name for you, both in backups
- [x] Muted words: group messages with chosen words collapse into a line to tap open; never sponsored messages
- [x] Unanswered: private chats where the other person wrote last and nothing was answered for a chosen number of days,
  with Reply and Dismiss; it never sends anything
- [x] Daily digest: one optional morning notification of the day's unread chats, mentions and reminders, opening
  Catch-up
- [x] Cleanup suggestions: chats not opened for a chosen time, with Mute, Archive or Leave decided one by one
- [x] A Quick Settings tile that blurs message text and photos until tapped
- [x] Listen to posts: Android's on-device text-to-speech reads a message or a channel's unread posts aloud
- [ ] Proxies: added from a QR code, their speed tested, and a working one chosen when the current one fails
- [ ] Two-line compact chat list rows, if the owner wants them after trying the one-line rows

## v2.7.0

From the second review of 2026-11-26, what is not fixed before v2.6.0 (owner decision 2026-11-26):

- [ ] Calls: "Add people" survives its screen closing; starting and answering a call run outside the screen's scope;
  leaving a conference while it joins leaves it; an outgoing video call gets the camera's service type
- [ ] The rest of the users map's projection: about 20 collectors still take the whole map, the widgets and three
  contact pickers on the main thread
- [ ] Drafts and Unanswered without loading the whole chat list
- [ ] The notification summary posted only when it changes; keyword alerts without a coroutine per message;
  `contacts()` without a `CreatePrivateChat` per contact; decoded stickers kept by memory, not by count
- [ ] The link cleaner leaves code untouched and covers captions and edits
- [ ] Birthday reminders follow a contact's changed birthday
- [ ] Tall image documents decoded at the width they show
- [ ] Unanswered leaves out Telegram's service account; removing an account stops waiting for its client after a
  timeout
- [ ] detekt's banned-name rules without an import-alias way round, and production sources told by source set, not
  by the absolute path
- [ ] `ChatViewModel` after its split: one way to read the current history, each state owned once, its internal types
  in their own files
- [ ] Tests: a backup from an older release restores; the daily digest's and birthdays' schedulers

## Later

- [ ] UnifiedPush for `foss` (ADR 0004, "UnifiedPush for `foss`"): a spike against several distributors, then an ADR
- [ ] One look for the theme and the wallpaper (an ADR first)
- [ ] Refactors from the review of 2026-11-23, each when a feature next touches its area: `CallManager` and
  `GroupCallManager` share their call binding, camera, hold and finish code; `MainRoutes` split so a route edit stops
  recompiling every feature; `ChatListViewModel`'s 20 parameters; the code `ProfileViewModel` and `ChatInfoViewModel`
  duplicate; `StoryViewerViewModel`'s own downloads and ticker; `flowOn` and fewer `SharingStarted.Eagerly` in
  `feature:settings`; per-chat DataStore keys removed with their chat; `ChatViewModel` under detekt's LargeClass
  limit, its bots', composer's, message actions' and selection's parts moved into holders
- [ ] A native sticker renderer only if a trace shows Lottie's software rendering missing frames after v2.5.0's
  scheduling fixes; no Rust toolchain (the review of 2026-11-23: the slow paths are mapping and flows, and the APK
  budget is tight)
- [ ] F-Droid's build: the call engine and WebRTC built from source (`docs/adr/0030-calls-in-the-fdroid-build.md`). A
  spike picks the engine (NTgCalls on a WebRTC tree compiled with the NDK alone, or tgcalls as Telegram X builds it,
  keeping 1:1 video, voice chats, live streams, screen sharing and conference calls) and ends in an ADR; then every
  APK, GitHub's included, is built from those sources, and the F-Droid submission follows (owner, 2026-11-25)

Not planned, as they break Telegram's terms or `CLAUDE.md`'s rules (owner, 2026-11-23): appearing offline while online,
reading without read receipts, keeping deleted or edited messages, hiding or filtering sponsored messages, poll results
before voting, copying protected content, more accounts than Telegram's apps allow, and translation or speech
recognition through other companies' services.

Not pursued: IzzyOnDroid (owner, 2026-11-25).

## Backlog (unscheduled)

Group video calls, Premium features surface, custom emoji packs management, bot
reply keyboards and web apps (Mini Apps WebView), payments, business features, Wear OS companion, composing rich messages
(the rich text editor, owner 2026-11-11: Pingogram shows them, `docs/audit/feature-gaps-telegram-2026.md`).

- [ ] Reaction notifications, once TDLib handles reaction pushes (owner decision 2026-11-19; `docs/TDLIB.md`,
  "Reaction and poll-vote notifications")

Features other clients have, with verdicts and priorities: `docs/audit/feature-gaps-octogram.md` (2026-10-24).
