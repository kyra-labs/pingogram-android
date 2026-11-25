# Pingogram — Requirements

Version: 1.0 · Status: living document · Owner: project maintainer

## 1. Product summary

Pingogram is a third-party Telegram client for Android that follows Android's current platform
guidance and **Material 3 Expressive** as closely as possible: dynamic color, adaptive
layouts, predictive back, edge-to-edge, modern permissions and notification conventions. It uses
Telegram's official client library, **TDLib**, so chats, media, groups, channels, bots and secret
chats behave exactly like the official apps at the protocol level, while the UI is entirely ours.

**One-line pitch:** "Telegram, in Material 3 Expressive." (Owner decision 2026-10-29: Pingogram is described by
the design system it follows, Material 3 Expressive, and Android's platform guidance, never by another company's
apps or design.)

### 1.1 Goals

- G1. A native Android experience that looks and moves as Material 3 Expressive specifies.
- G2. Feature parity with the official app for everyday messaging by v1.0 (see §5).
- G3. Fast, battery-friendly, small download (≤ 53 MB arm64-v8a APK, ≤ 128 MB universal, calls and voice chats
  included; `docs/PERFORMANCE.md` §1).
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
| `compileSdk` | 37 | Required by the libraries: Compose 1.12 (`ui`, `foundation`), material3 1.5.0-beta02 and navigation-compose 2.10.2 declare `minCompileSdk=37` in their AAR metadata (checked 2026-10-20); compiling against a newer SDK does not change runtime behaviour |
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
- FR-A9 Must: a demo mode for the stores' reviewers, who cannot be given credentials, as every login needs a code
  Telegram sends to the account's phone (owner decision 2026-11-01, `docs/adr/0034-demo-mode-for-store-review.md`;
  it replaces the test-server sign-in, which Telegram's test servers no longer allow). In every build:
  - **Entry**: pressing and holding the Welcome screen's logo for about a second opens a dialog that opens the demo.
    Nothing on screen points to it.
  - **What it shows**: sample chats stored on the phone. A `DemoTdlibClient` answers the app's requests, with no
    network and no TDLib instance.
  - **Labels**: a "Demo" banner on every screen, and "Demo" on Settings' account card.
  - **Leaving**: "Leave demo" in Settings deletes everything it made and returns to Welcome.
  - **Kept apart**: it runs as an account graph of its own, which notifications, push, widgets, shortcuts, Direct
    Share and the accounts limit never see.
  - **Permanent**: the stores' App access instructions describe it (`docs/PLAY_STORE.md` §1).

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
  with recent searches, using the M3 search bar, opened from the search button of the Chats tab's header (owner
  decision 2026-11-06; `docs/DESIGN_SYSTEM.md` §6 "Chat list top bar").
- FR-L7 Must: a "New chat" FAB (beside the floating navigation bar, or in the rail; owner decision 2026-11-06) opening a
  New chat screen with new group / new channel / new contact and the contacts, with a container transform
  (`docs/DESIGN_SYSTEM.md` §6).
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
  color theme (`docs/adr/0016-color-themes.md`), message corner radius, font size, chat wallpaper: the theme's colors,
  or Telegram's wallpapers and patterns and the user's photos, synced to the account for light and dark theme apart,
  with dimming in dark theme and blur for photos (owner decision 2026-11-20); a chat's own wallpaper from its info
  page; both on one wallpaper page that previews the conversation and sets nothing until Apply, ADR 0041; a conversation with a photo or a fill wallpaper takes its colors from it, owner decision 2026-11-21), Language (per-app language API), About (version, licenses, GitHub link).
- FR-S2 Must: Appearance: System / Light / Dark, a color theme (wallpaper colors on Android 12+, the brand's or
  curated Material 3 themes, `docs/adr/0016-color-themes.md`), AMOLED black option, contrast levels
  (standard/medium/high per M3), and how chats look (group bubbles; Chat appearance with the message text size,
  bubble corners and wallpaper). How chats behave is on its own page, Settings → Chats (owner decision 2026-11-19):
  double-tap to react, snippets, which Enter sends, voice messages (transcription, raise to listen, skip silence), and
  the chat list's swipes and chats shown first.
- FR-S2a Must: Pingogram's look (`docs/DESIGN_SYSTEM.md` §13, `docs/adr/0035-experimental-ui.md`): first an opt-in
  "Experimental UI" (owner decision 2026-11-07), the app's only look since the owner's decision of 2026-11-08, with no
  switch.

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
- Stories on profiles (owner decision 2026-11-20, `docs/audit/feature-gaps-telegram-2026.md` "Story albums"): a
  "Stories" tab on people's profiles and channels', with the chat's story albums as covers (title, count) above the
  stories it keeps on its profile as a grid with their days; a story plays the profile's stories from it, an album
  opens its own grid. Loading, "No stories yet" and an error with "Try again". No tab where a chat keeps no stories
  (bots, deleted accounts, groups). On the user's own profile, "New album" asks for a name, then for its stories; in
  the user's own album, its menu renames it, adds or removes stories (picked from the profile's or the album's) and
  deletes it after asking.

### 5.10 Beyond the official app [after v2.3.0]

Owner decision of 2026-11-17. Each works on the phone with what TDLib already gives; nothing is sent anywhere but
Telegram, nothing acts for the user, and sponsored messages are shown exactly as Telegram sends them (§2). Every
feature here can be turned off in Settings; a new behavior that changes an existing one is off by default (owner
decision 2026-11-23).

- FR-X1 Should: **Mentions & replies.** One page, opened from the Chats tab's header, listing the messages that mention
  the user and the replies to the user's messages, across all chats, newest first, unread first; each opens its chat
  at the message, and reading it there marks it read. Built from TDLib's mention and reply searches (check `TdApi`
  for what searches across chats and what only per chat); no new storage.
- FR-X2 Should: **Snippets.** Texts the user saves (a name and a text, up to 100), kept on the phone with the
  account's other local data, managed in Settings; in the composer, typing `/` followed by a snippet's name, or the
  composer's menu, inserts its text into the field. Inserting never sends: the user taps send. Bots' commands keep
  `/` in chats with bots, where snippets come second in the suggestions.
- FR-X3 Should: **Add to calendar, open in maps.** A message's actions offer "Add to calendar" when Android's on-device
  `TextClassifier` finds a date or time in its text, and "Open in maps" for an address; the calendar opens with the
  event filled in (`CalendarContract` insert intent) and the user saves it there. No permission, no network.
- FR-X4 Should: **Skip silence and playback speed.** Voice and round video messages can skip their silences (the
  player's own skip-silence), a switch in Settings → Chats → Voice messages beside raise to listen, remembered for the app;
  the switch stays in Settings only, as the speed chip is a single tap with no menu to hold it. While one is in the
  player, a speed chip beside its time (on a round video message, at its circle's bottom) cycles 1× → 1.5× → 2×, at
  once and remembered for the app for whatever plays next; music plays at 1×.
- FR-X5 Should: **Channel feed.** The user's channels as one timeline, newest posts first, each post with its channel
  and "Open in channel", laid out as the owner's design board shows. Owner decisions of 2026-11-18:
  - A "Feed" folder tab shows once the user follows 2 or more channels; Settings → Chat folders → "Show Feed tab" (on
    by default) hides it.
  - A post counts as read once 60% of it is on screen for 1 second, as in the channel, and is marked read there.
  - Each channel's sponsored message shows once per feed session, where the channel would show it (after that
    channel's newest post), looking and acting as in the channel: its label and buttons, its view reported when shown
    and its click when tapped, never dimmed, shrunk or left out.
  - "Mark all as read" asks first when it would mark 20 or more posts.
  Owner decisions of 2026-11-19:
  - Older posts stay current without opening the channels and without timers or polling: the feed takes whatever
    updates TDLib sends for its posts (reactions and views, edits, deletions), reads a post again (`getMessages`)
    right after the user reacts to it from the feed, and reads the posts on screen again, one call per channel,
    whenever the feed comes back into view or is pulled to refresh. A post `getMessages` no longer returns leaves the
    feed. A long press offers the reactions bar above the lifted card.
  - The feed's search button searches posts in the user's channels (`searchMessages` with the channel chat type
    filter), leaving out channels the feed's channel filter leaves out while it is on; results show as global search's
    message rows, and a tap opens the post in its channel, as a card does.
  Owner decision of 2026-11-20:
  - With a hardware keyboard, the arrow keys move the focus between the cards, the list keeping the focused one in
    view, and Enter opens the post in its channel, as a tap does; Tab reaches the buttons in a card (its reactions,
    More, Open in channel), and More or the Menu key (Shift+F10) opens the post's menu. Two panes keep the focus on
    the card whose channel opened beside the feed; screen readers keep the card's custom actions.
- FR-X6 Should: **Backup and restore of what Pingogram keeps on the phone** (`docs/adr/0039-local-backup-and-restore.md`,
  owner decisions of 2026-11-17). Settings → On this phone → Backup & restore: a folder the user picks once (Storage
  Access Framework, no permission), "Back up now" into one file encrypted with a password set at the first backup,
  and the last backup's date and size; the settings (never the passcode, locked chats or one-time hints) and each
  account's bookmarks, reminders, birthday reminders, keyword hits, snippets and notes and names on chats (never of
  secret or locked chats, never the demo). Restoring asks for the password, shows what the backup holds, replaces the settings and merges each
  account's data into the same Telegram account only; the chat list offers a restore after signing in. "Back up automatically": Off,
  Daily or Weekly (Weekly by default once a folder is picked), while charging or the battery is not low, keeping the
  newest 3 files, encrypted with the key of the last backup's password kept in the Android Keystore (never the
  password). Nothing is sent anywhere.
- FR-X7 Should: **Incognito keyboard** (owner decision 2026-11-23). Settings → Privacy and security → "Incognito
  keyboard", off by default: every text field asks the keyboard not to learn from what is typed
  (`IME_FLAG_NO_PERSONALIZED_LEARNING`), through one helper around the app; a secret chat's fields always ask. A
  keyboard may ignore the request; nothing else changes.
- FR-X8 Should: **Links without tracking parameters** (owner decision 2026-11-23). Settings → Privacy and security →
  "Remove tracking from links", on by default: links the user sends (messages, notification replies, the share sheet's
  comment) and links the user opens lose their tracking parameters: every `utm_*`, `fbclid`, `igshid`, `igsh`,
  `gclid`, `dclid`, `gbraid`, `wbraid`, `msclkid`, `yclid`, `mc_cid`, `mc_eid`, `_hsenc`, `_hsmi`, and `si` on YouTube
  and Spotify links (`LinkCleaner`). Everything else in a link stays as written; links in received messages show as
  sent. A sponsored message's link always opens exactly as Telegram sent it (§2).
- FR-X9 Should: **Adding a contact shares the phone number only when chosen** (owner decision 2026-11-23). Add
  contact (by phone number or by username) and "Add to contacts" have "Share my phone number", which starts from
  Settings → Privacy and security → Contacts → "Share my phone number with new contacts", off by default; the chat
  bar's "Add contact" follows that setting. Editing an existing contact starts with the number not shared.
- FR-X10 Should: **Folder tabs without unread counters** (owner decision 2026-11-23). Settings → Chat folders →
  Folder tabs → "Unread counters", on by default: off, the folder tabs show no unread counts, whatever they show
  (names, icons and names, or icons), and screen readers hear their names alone.
- FR-X11 Should: **Send silently by default** (owner decision 2026-11-23). Settings → Chats → Messages → "Send
  silently by default", off by default: on, messages sent from a chat (text, files and media, polls, checklists,
  locations, inline results, voice and round video messages) and from the share sheet go without a notification
  sound, and the send menus offer "Send with sound" in place of "Send without sound".
- FR-X12 Should: **Confirm bot commands** (owner decision 2026-11-23). Settings → Chats → Messages → "Confirm bot
  commands", off by default: on, a command picked from a bot's commands sheet or the suggestions above the composer
  asks "Send /help?" first, kept across process death; a command typed and sent as text goes as before.
- FR-X13 Should: **Undo and redo in the message box** (owner decision 2026-11-23). Settings → Chats → Messages →
  "Undo and redo", on by default: the message box's menu and its screen-reader actions offer "Undo" and "Redo" while
  its history allows them; off, they leave the menu. A keyboard's Ctrl+Z works either way.
- FR-X14 Should: **Save or unpin several messages** (owner decision 2026-11-23). The conversation's selection
  toolbar offers "Save to Downloads" where every selected message may be saved and has a file, and "Unpin" where every
  one is pinned; each acts on all of them and ends selection mode. No setting: they join the toolbar's other actions.
- FR-X15 Should: **What a double tap does** (owner decision 2026-11-23). Settings → Chats → Messages →
  "Double-tap to react": on others' messages and on the user's own, each React (the default), Reply or Nothing; the
  switches saved before read as React or Nothing.
- FR-X16 Should: **Drafts** (owner decision 2026-11-23). One page, opened from a button in the Chats tab's header
  that shows while a chat has a draft, listing every main or archived chat with a message the user started and has
  not sent, newest draft first, as the chat list previews it (a draft that is only a reply is left out); a tap opens
  the chat with the draft in the message box, and a draft sent or cleared there leaves the page. Settings → Chats → Chat list → "Drafts button", on by
  default: off, the header has no Drafts button.
- FR-X17 Should: **Send typing status** (owner decision 2026-11-23). Settings → Privacy and security → "Send typing
  status", on by default: while on, a chat sees what the user is doing in it (typing, recording a voice or round video
  message, uploading a photo, a video, a file or a voice or round video message; a GIF shows nothing), sent again at
  most every five seconds while it lasts and ended when the message goes, the text is cleared, or the user leaves the
  conversation or its screen stops; while off, nothing is sent, not even an action's end.
- FR-X18 Should: **Birthdays** (owner decision 2026-11-23). While Telegram names contacts with a birthday these days,
  the Contacts tab shows a "Birthdays" row above the contacts, opening a page that lists them with the day and, where
  shared, the age they turn. Each offers "Send a message"; "Remind me", which only the user sets, one reminder a year
  per contact, kept on the phone and ringing on the day; and "Add to calendar", which hands a yearly event to the
  calendar app. Settings → Privacy and security → Contacts → "Birthdays", on by default: off, the row is hidden, and
  reminders already set still ring.
- FR-X19 Should: **Compact chat list** (owner decision 2026-11-23). Settings → Chats → Chat list → "Compact chat
  list", off by default: on, the chat list's rows show a 40 dp avatar on rows at least 56 dp high, so more chats fit;
  they still grow with the font size.
- FR-X20 Should: **Notes and names on this phone** (owner decisions 2026-11-25). On the info page of a group, a
  channel or a bot, or the profile of someone who is not a contact, the user's own name for the chat (up to 64
  characters), shown wherever its title shows (chat list, conversation, search, pickers, notifications, the widget),
  with Telegram's name under it on the info page; and a private note (up to 2,000 characters). Contacts keep
  Telegram's own contact name and contact note (FR-P2), synced and with no local ones: someone who becomes a contact
  shows their contact name, and their local entry, kept and hidden, shows again if they stop being one. Both are kept
  on the phone with the account's data, never sent to Telegram, and go into backups (the same account only, never of
  secret or locked chats). Settings → Chats → "Notes and names on this phone", on by default, says which chats it is
  for: off, Telegram's names show again and what the user wrote is kept.
- FR-X21 Should: **Muted words** (owner decisions 2026-11-25). Settings → Chats → "Muted words": a switch, off by
  default, above the user's words and phrases (up to 100, each up to 64 characters), which can be added while it is
  off. While on, a message in a basic group, a supergroup or a forum topic whose text or caption has one (whole words,
  any letter case, with or without accents; within the text in scripts written without spaces, such as Chinese,
  Japanese and Thai) shows as a line, "Message from Ada · muted word · Tap to show", which shows it until the user
  leaves the chat. Never channels or private chats, never the user's own messages, never a message that mentions or
  replies to the user, never service or sponsored messages (Telegram's terms). The chat list's preview and
  notifications say "Message with a muted word" instead of its text. Nothing is deleted or marked read; the words stay
  on the phone, are never sent to Telegram, and go into backups.

## 6. Non-functional requirements

### 6.1 Performance ("optimized Telegram")

Pingogram must be measurably faster and lighter than the official app on the same device.
Binding budgets and techniques are in `docs/PERFORMANCE.md`; headline targets:

- Cold start → chat list ≤ 1.2 s, warm start ≤ 400 ms (Macrobenchmark, Baseline + Startup Profiles shipped).
- Chat list / conversation scroll at the display's native rate (60/90/120 Hz): P90 frame ≤ 8–10 ms, jank < 1%.
- Open a cached chat ≤ 150 ms; optimistic send ≤ 50 ms.
- Memory ≤ 120 MB idle, ≤ 250 MB in a 10k-message media chat; APK ≤ 53 MB arm64-v8a and
  ≤ 128 MB universal (raised from 35 MB for the call engine, owner 2026-10-22; set from the APKs with voice chats plus
  a small margin, owner 2026-10-23; raised for TDLib 1.8.70, owner 2026-11-11, `docs/adr/0038-apk-budget-for-tdlib-1-8-70.md`; raised for the
  features after v2.3.0, owner 2026-11-20, `docs/adr/0040-apk-budget-after-v2-3-0.md`).
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
- The camera (`CAMERA`, no camera is required to install) has four uses:
  - The QR code scanner (FR-P5, `docs/adr/0012-camerax-and-zxing-qr-scanner.md`, approved by the owner 2026-10-21),
    asked when the scanner opens, after it says why: frames are decoded on the device by ZXing and dropped, nothing
    the camera sees is kept or sent, and only the text of a Telegram link reaches TDLib. Scanning from an image uses
    the Photo Picker, without a permission.
  - Video calls (FR-V2, `docs/adr/0019-video-and-group-calls-scope.md`, approved for 1:1 video), asked when the user
    turns the camera on in a call: the video goes to the other person only, end-to-end encrypted like the call's
    audio, while the camera is on, and is not kept.
  - Round video messages (FR-C5, `docs/adr/0032-round-video-messages.md`, approved by the owner 2026-10-23), asked when
    the user first switches the microphone button to video, after it says why: the camera and the microphone run only
    while the user records, the video goes only into the message the user sends (end-to-end encrypted in secret
    chats), and is not kept.
  - Opening the camera app to take a photo for a chat (the composer's camera button, the attach menu's "Camera") or a
    new story ("Camera"), which Android allows only once the permission is granted, as the app declares it for the
    uses above (#497; owner decision 2026-11-14). Asked when the user first taps the camera, the tap saying why;
    Pingogram itself sees nothing until the camera app hands back the photo, which goes only into what the user sends.

### 6.3 Accessibility (Android standards)

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

## 7. Android platform standards checklist

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
