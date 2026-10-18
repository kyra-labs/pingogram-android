# Changelog

All notable changes to Pingogram are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

The release workflow copies the section for the tagged version into the GitHub Release notes,
so keep headings exactly in the form `## [X.Y.Z] - YYYY-MM-DD`.

## [Unreleased]

## [0.2.0] - 2026-10-18

### Added
- Tapping a chat opens the conversation (read-only for now). The top bar shows the avatar and name with
  "online", "last seen …", who is typing, or the number of members or subscribers. Messages load as you
  scroll back, with date pills, a divider before the first unread message and a button that jumps to the
  newest. Bubbles group consecutive messages, show sender names and avatars in groups, the time, "edited",
  and sent, read, pending or failed status, forwarded-from headers, reply quotes, reactions and link
  previews. Text keeps its formatting: bold, italic, underline, strikethrough, code, quotes, spoilers (tap
  to reveal) and links. Service messages appear as pills.
- Media in conversations. Photos fill their bubble with a blurred preview while they load, albums show as
  one mosaic, and videos and GIFs show their thumbnail with the length. Stickers, animated emoji, dice and
  round video messages appear without a bubble. Voice messages show their waveform and length; documents
  show their name and size and download on tap, with progress and cancel. Music, polls with their results,
  locations and venues (which open your maps app), contacts, games and invoices have their own cards.
  Tapping a photo, video, GIF or album item opens it full screen.
- Voice messages and music play in the conversation, one at a time: the waveform fills as it plays, tapping
  it seeks, and voice messages play on to the next one. A file that isn't downloaded yet downloads first.
  Playback pauses when you leave the chat or unplug headphones.
- Messages you see in a conversation are marked as read, and the unread count follows.
- Tapping a reply quote jumps to the original message and highlights it, loading older messages if needed.
- A full-screen media viewer: swipe through all the photos and videos of a chat (GIFs among the chat's GIFs)
  with "N of total" at the top; pinch or double-tap to zoom and drag to pan; videos play with play/pause and
  a seek slider, GIFs loop silently. The sender, date and caption show over the media; tap to hide them
  along with the system bars. Swipe down or go back to close. The viewer is always dark, like a photo app.
- Photos, videos and GIFs grow out of their bubble into the media viewer and shrink back into it when you close
  it. The viewer can share what it shows with another app or save it to Pictures/Pingogram or Movies/Pingogram,
  unless the chat protects its content; on Android 8 and 9 saving first asks for storage access, with the reason.
  Tapping a downloaded document opens it in an app that can view it.
- Search, from the round button beside the navigation bar or at the top of the rail. Before you type it shows the
  people you message most and your recent searches, which you can remove one by one or clear. As you type it finds
  your chats, your contacts, public chats and channels, and messages in all your chats, with the matching words
  highlighted; more messages load as you scroll. Tapping a message opens its chat at that message.
- On tablets, foldables and other large screens, the chat list and the open conversation sit side by side;
  the open chat is highlighted in the list. Narrowing the window shows the conversation alone, and back
  returns to the list.

### Changed
- Folder tabs respond like Material 3 Expressive button groups: a pressed folder grows and nudges its
  neighbours, and the selected folder turns into a pill with a bold label. Every folder stays a tab; the row
  scrolls sideways when they do not fit. Switching folders fades the list out and the new folder's list in,
  from its top; with animations turned off, the lists swap at once.
- "Archived chats" is bold while it holds unread chats, like any unread chat.
- The chat list, the archive and conversations sit on a sheet with rounded top corners below their top bar, in the
  style of Google's Material 3 Expressive apps; chats and messages scroll inside it. Selected chats turn into
  rounded pills set in from the edges, and swiping a chat reveals its action on a rounded card.

### Fixed
- A swipe on a chat runs its action once. Archiving a chat inside a folder no longer archives and unarchives
  it over and over with its row stuck halfway, archiving in All chats no longer brings the chat straight back,
  and "Undo" on "Chat archived" moves the chat back to the chat list again. A chat just marked as read or
  unread by swiping can be swiped again at once, for example to unarchive it in Archived chats.
- With Android's "Bold text" setting on, text is one step bolder instead of nearly black, and unread chat
  names still stand out from read ones.
- Switching back to a chat folder shows its list where you left it, or at its top the first time, instead of
  jumping to the chat that was first in the other folder.
- The navigation bar stays in place when you switch tabs, instead of sliding sideways and back.
- Going back from a chat, the archive or another tab with the back gesture is smoother: the screen you leave
  shrinks a little and fades away over the one you go back to, instead of shrinking far, waiting and vanishing at
  once. Opening and leaving a chat no longer redraws every screen behind it, which caused stutters.
- Switching chat folders no longer stutters: the new folder's chats appear in their places instead of sliding in
  from where the other folder had them, and the chat list does less work per chat.
- Fewer stutters when going between screens: each text style's font is prepared once, instead of again whenever
  enough other styles had been shown since.

## [0.1.2] - 2026-10-18

### Added
- The chat list. The top bar shows the connection state ("Connecting…", "Waiting for network…") while not
  connected. Your chat folders are tabs with unread counts, with "All chats" where you placed it. Each chat
  shows its avatar with an online dot, verified or scam badges, a lock for secret chats, who is typing, your
  draft or the last message with its sender, the time in your 12- or 24-hour format, read ticks, and unread,
  mention, reaction, muted and pinned markers. Saved Messages and deleted accounts get their own icons. More
  chats load as you scroll. Opening a chat comes with the conversation screen.
- Chat actions in the chat list. Swipe a chat right to mark it as read or unread, or left to archive it (with
  Undo). Long-press to select chats: a floating toolbar takes the navigation bar's place to pin, mute, mark
  as read or unread, archive or delete them all at once, and the selected avatars turn into a check. Deleting
  asks first and offers "Also delete for …" or "Delete for all members" where Telegram allows it; leaving a
  group or channel is worded as such. An "Archived chats" row at the top of All chats opens the archive, which
  works like the chat list. Screen readers get every action on each chat, so none needs a swipe.
- A floating navigation bar on phones, in the style of Google's Material 3 Expressive apps: the tabs in a
  pill that floats over the content (the selected tab shows its icon and label, the others their label, or
  their icon when the labels do not fit), with a round search button beside it. Tablets keep the
  navigation rail, now with the search button at its top. Search itself is not built yet.
- Data layer for the chat list and conversations (no screens yet): chats with lists, folders, unread counts and
  mute state; users and their online status; paged chat history kept current by TDLib's updates, with at most
  three chats' messages in memory; files with download priorities; and a message model covering every content,
  formatting and service message type of the TDLib version in use. All of it starts listening before TDLib
  starts, so no update is missed.
- Groundwork for the chat list (no screen yet): typing indicators; verified, scam and fake badges for groups
  and channels; the chat actions behind swipes (pin, mute, archive, mark as read or unread, and delete or
  leave with the right steps for each kind of chat), with a clear message when the pinned-chat limit is
  reached; and avatars, which show the photo from TDLib's files through Coil 3 (which makes no network
  requests of its own) or else initials on an expressive Material shape in one of seven color pairs, with an
  online dot.

### Changed
- The Contacts placeholder no longer says that sign-in is coming.

## [0.1.1] - 2026-10-18

### Fixed
- The app could not connect to Telegram and stayed on "Connecting…": it did not request the internet
  permission.

## [0.1.0] - 2026-10-18

### Added
- Project scaffold, documentation, CI, TDLib and release workflows.
- Gradle project: version catalog, `build-logic` convention plugins, Gradle 9.8.1 wrapper and the
  `:app`, `:core:*` and `:feature:*` modules from `docs/ARCHITECTURE.md` (mostly empty for now).
  AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00; `foss`/`gms` flavors; app version and
  per-ABI version codes from `-Pversion`.
- Telegram API credentials come from the git-ignored `secrets.properties`; the build fails with a
  clear message (never echoing values) when the file or a key is missing or malformed.
- Committed debug keystore for consistent debug signatures; release signing from environment or
  `keystore.properties` (v2/v3/v4), and release packaging fails early when signing is not configured.

- Static analysis in `./gradlew check`: ktlint 1.8 (`./gradlew ktlintFormat`), detekt 1.23.8 with the
  `PingogramHardcodedToken` rule (no literal colors, text sizes, sizes, shapes or animation specs in
  `:feature:*` code), Android Lint with warnings as errors, and `./gradlew dependencyUpdates`.

- `:benchmark` module: Macrobenchmark `StartupBenchmark` and Baseline/Startup Profile generation
  (`./gradlew :app:generateBaselineProfile`); release builds ship the profile via profileinstaller.

- `PingogramTheme` on `MaterialExpressiveTheme`: six generated brand color schemes (seed `#E8457C`,
  standard/medium/high contrast), dynamic color on Android 12+, system contrast on Android 14+,
  AMOLED black, harmonized online green; the full Google Sans Flex type scale (15 styles + 15
  emphasized, per-style variable axes); the expressive shape scale with `MaterialShapes` helpers;
  spacing tokens; preview helpers; splash and window themes.
- Roborazzi screenshot tests (Robolectric, native graphics) verified by `./gradlew check`.

- Release builds produce one APK per ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`) plus a universal APK,
  with distinct version codes. Native code for other ABIs is left out, so devices that only support
  32-bit x86 cannot install the app.

- TDLib 1.8.68 for Android, built by the `Build TDLib` workflow and published as the `tdlib-a94b83d67a`
  release; the build downloads it and verifies its pinned SHA-256 (`pingogram.tdlib.aar` overrides it with
  a local AAR).
- `core:tdlib`: coroutine `TdlibClient` with typed authorization and connection state, automatic
  `SetTdlibParameters` (honest client identification), database key wrapped by the Android Keystore and
  kept out of backups; `FakeTdlibClient` and TdApi fixtures for tests.

- App shell: `MainActivity` (splash, edge-to-edge, predictive back) with type-safe navigation and an
  adaptive `NavigationSuiteScaffold` — short navigation bar on phones, navigation rail on larger
  screens — with Chats, Contacts and Settings placeholder tabs and Material Symbols Rounded icons.

- Sign-in flow (`feature:auth`): welcome, phone number with country picker and live formatting, login
  codes of every type with resend countdown, two-step verification password with recovery by email,
  registration with terms of service, QR code login, email address and email code, and an explanation when
  an account needs Telegram Premium to sign in (no purchase flow). Rate limits show a countdown and an
  offline banner appears while there is no connection; errors map to readable messages.

- The app opens on the sign-in flow when logged out and on the chats when logged in; the splash screen
  waits up to 300 ms for TDLib. Logging out (Settings → Log out, with confirmation), being logged out
  elsewhere, or a closed session returns to sign-in with nothing of the previous account left behind.

- Sign-in screens polished: real QR code for QR login (ZXing), password visibility toggle, adaptive layout
  for tablets, landscape and large fonts, shared-axis transitions and predictive back between steps,
  screen-reader friendly countdowns and banners; screenshot tests for every step in light, dark and
  dynamic color.

### Changed
- Package root and `applicationId` are `dev.kyralabs.pingogram` (debug: `dev.kyralabs.pingogram.debug`).
- `compileSdk` is 37 (needed by Compose 1.12); `targetSdk` stays 36.
- Uses the pre-release `androidx.compose.material3:material3:1.5.0-beta01` (allowed by CLAUDE.md rule 7):
  in the stable 1.4.0 the Material 3 Expressive APIs (`MotionScheme.expressive()`, `MaterialShapes`,
  the emphasized type styles, the increased shape sizes) are internal.
- Backups and device transfer are disabled for all app data.

[Unreleased]: https://github.com/kyra-labs/pingogram-android/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/kyra-labs/pingogram-android/compare/v0.1.2...v0.2.0
[0.1.2]: https://github.com/kyra-labs/pingogram-android/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/kyra-labs/pingogram-android/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/kyra-labs/pingogram-android/releases/tag/v0.1.0
