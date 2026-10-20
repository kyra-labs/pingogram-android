# Changelog

All notable changes to Pingogram are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

The release workflow copies the section for the tagged version into the GitHub Release notes,
so keep headings exactly in the form `## [X.Y.Z] - YYYY-MM-DD`.

## [Unreleased]

### Added
- Select messages: "Select" in a message's actions starts selection mode. Tap or long-press other messages to select
  or deselect them (an album as a whole); selected messages turn a different color and show a check. The top bar
  shows how many are selected, and a toolbar in place of the composer copies their texts (several at once with who
  sent them and when, as the official apps do), forwards them, reports them or deletes them, offering only what
  Telegram allows for every selected message. Back or the close button leaves selection mode, which also survives
  turning the phone. With TalkBack, each message says whether it is selected and can be selected from its actions.
- Report messages: "Report" in a message's actions (and in the selection toolbar) where Telegram allows it asks the
  reasons Telegram offers, and a comment where it wants one, then thanks you once the report is sent.
- Save to Downloads: documents, music, videos, GIFs and photos can be saved to your phone's Downloads folder (in a
  Pingogram folder) from a message's actions, where the chat allows saving. A file that isn't on the phone yet
  downloads first. A snackbar confirms it, with a button that opens the file. On Android 8 and 9 Pingogram asks for
  storage access first.
- Notifications for new messages, mentions, new secret chats and calls, while Pingogram is running. Each chat gets one
  notification that shows its latest messages with who sent them and their photos; media show as "Photo", "Voice
  message" and so on, and chats whose previews you turned off show "New message". Muted chats and exceptions work as
  in your other apps, and a notification goes away once you read the chat anywhere. Only new messages make a sound:
  updates, messages sent silently and the chat you are looking at stay quiet.
- Reply right from a notification, or mark the chat as read; tap a notification to open its chat.
- Notification channels for private chats, groups, channels, calls and everything else, so you can set their sound
  and importance in your device's settings. On Android 11 and later, chats show in the conversations section of the
  notification shade, where you can make one a priority conversation.
- On Android 13 and later, the chat list explains why Pingogram asks to show notifications before it asks. Choose
  "Not now" and it won't ask again; if you refused before, it offers your device's notification settings instead.
- Settings → Notifications: turn notifications and message previews on or off for private chats, groups and
  channels, choose whether mentions and replies notify in muted groups and whether pinned messages notify, and open
  each kind's sound and vibration in your device's settings. These settings belong to your Telegram account, so they
  apply on your other devices too. Exceptions lists the chats with settings of their own and whether they are muted
  (and until when); tap one to mute it for an hour, 8 hours, a day or for good, unmute it or let it follow the default
  again. "Reset all notification settings" restores everything after asking. If your device blocks Pingogram's
  notifications, the page says so at the top and helps you turn them back on.
- "Keep connected" (GitHub and F-Droid builds, off by default): keeps Pingogram connected in the background, so new
  messages notify at once, at the cost of some battery. A quiet notification shows while it is on, with "Turn off".
  It starts again after your device restarts, but only if you turned it on, and stops when you log out.
- Sponsored messages: channels and chats with bots show Telegram's sponsored messages as a card labelled
  "Sponsored" (or "Recommended" for a recommended channel) below the newest posts, with the advertiser's button.
  Tap the button, a link or the picture to open it. The info button shows "About this ad": who placed it and more
  about it, and lets you report it, with the reasons Telegram asks for.
- A reactions picker: the + button at the end of a message's quick reactions shows every reaction you can add in
  that chat, the chat's most used ones first, then your recent ones and the popular ones, with those you added
  marked. Tap one to add it, or tap it again to take it back. The picker also shows every reaction already on the
  message with how often it was added. Reactions that need Telegram Premium only show if you have it. Custom emoji
  reactions show as a still picture where Telegram has one.
- Open-source licenses: About now lists the libraries, fonts and icons Pingogram is built with, each with its
  version and license. Tap one to read the full license text, which you can select and copy, and to open its
  website. The list is made from the app's own build, so it always matches what you installed, and it works
  offline.
- Push notifications for builds with Google Play services (the "gms" build, not yet published): new messages arrive
  through Firebase Cloud Messaging even when Pingogram is closed. Telegram encrypts each push for your device, so
  Google only sees that something arrived. The GitHub releases stay free of Google's services.
- Forward messages: "Forward" in a message's actions (where Telegram allows it; protected content can't be forwarded)
  opens a sheet with your chats, Saved Messages first, and a search. Pick one or more chats you can write in, add a
  comment if you like, and send. "Hide sender's name" sends copies without "Forwarded from", and for photos, videos
  or files with a caption "Hide captions" leaves the captions out. An album is forwarded whole. A snackbar confirms
  it, and when you forwarded to one other chat, opens that chat.

### Changed
- The login screens are clearer and fit wide and landscape screens: each step starts with a large title under a
  shaped icon, which turns into a warning sign when something needs fixing or Telegram asks you to wait. "Next" is a
  normal-sized button at the bottom right, with the other choice ("Log in with QR code", "Wrong number?", "Forgot
  password?") at the bottom left. On tablets and on phones held sideways the title sits beside the form, so "Next"
  is always on screen without scrolling.
- The chat list is denser, closer to Google's own apps: a slimmer top bar that no longer collapses, smaller avatars,
  a one-line preview of the last message, and a lower row of folder tabs. About nine chats fit on a typical phone
  instead of six or seven.
- Selected chats get rounded corners instead of turning into pills, and the buttons of the selection toolbar show
  their name on a long press.
- Folder tabs that don't fit scroll the selected folder into view and fade out at the edge, so no tab is cut off
  without a hint. A chat being swiped keeps its rounded shape as it slides.
- The "+N" badge under a message with many reactions now opens the reactions picker with all of them, instead of
  the message's actions. TalkBack offers the same as "All reactions".
- Chats without a photo show their initials in a circle in one of four calm colors, instead of six different shapes
  in seven colors, some of which stayed bright in dark theme. The chat open beside the list on a tablet keeps its
  avatar visible against the highlighted row.
- The app's own colors (when dynamic color is off or unavailable) are a more vivid blue, closer to the icon.
- Loading indicators show only when loading takes longer than a moment, so screens that load quickly no longer
  flash one.
- Opening a chat, the archive, search or a settings page now slides the new screen in from the side, and going
  back slides it out the same way, instead of fading. Switching tabs still fades.
- Phones in landscape show the navigation rail at the side instead of the floating bar, leaving more room for
  chats. On large tablets and desktop windows the rail is expanded, with each tab's name and a labelled Search
  button.
- On tablets and other wide windows, the chat list and the open chat each sit in their own rounded panel, and a
  handle between them resizes the list. With no chat open, the empty panel lines up with the list.

## [0.3.0-beta.2] - 2026-10-19

### Added
- Pingogram has its own icon, with a themed version that follows your wallpaper's colors on Android 13 and later. It
  also shows on the splash screen, on the Welcome screen and, with the full logo, at the top of About.

### Changed
- The app's own colors, used when dynamic color is off or unavailable (before Android 12), are now built from the
  icon's blue instead of a placeholder pink.

### Fixed
- "Wallpaper colors" now follow the wallpaper on phones that publish its colors only the older way, such as Samsung
  phones with One UI, where the app showed the system's stock blue instead.

## [0.3.0-beta.1] - 2026-10-19

### Added
- You can write and send messages. Format selected text as bold, italic, underlined, strikethrough, monospace,
  spoiler or a link (from the selection menu, or Ctrl+B, I, U and K on a keyboard). Send without sound, or
  schedule a message for later. Unsent text is kept as a draft that syncs with your other devices. Where you
  can't write in a chat, a short note says why; channels you only read show no composer.
- Message actions: long-press a message (or use TalkBack's actions) for quick reactions, reply, copy text, edit,
  pin or unpin (in private chats also only for yourself), delete for you or for everyone, and send a failed
  message again, each only where Telegram allows it. A reply shows a bar above the composer and is kept in the
  chat's draft on all your devices; editing puts the message's text or caption, with its formatting, back in the
  composer. Tap a reaction under a message to add or remove yours.
- Attach photos, videos, files, contacts and polls: the attach button beside the message field opens Gallery (up to
  10 at once), Camera, File, Contact and, in groups, channels, bots and Saved Messages, Poll. Picked items show in a
  preview first, where you can remove some, add a formatted caption, and send now, without sound or later. Photos are
  sent upright, at most 2560 px and without location data; photos and videos go as albums of up to 10, files as
  albums of files. Polls can be anonymous, allow several answers, or be a quiz with one correct answer. A photo, video
  or file shows its upload progress in the chat; tap X (or TalkBack's "Cancel upload") to stop it.
- Settings shows your account at the top (photo, name, phone number and username) and has new Appearance, Data
  and storage, and About pages; Log out moved to the end.
- Appearance: a System, Light or Dark theme, AMOLED black for the dark theme, dynamic color from your wallpaper
  (Android 12+) and contrast (on Android 14+ it follows your device's setting). Changes apply at once with a
  smooth color fade, and the app always opens in the theme you chose.
- Data and storage: how much space downloaded files and the database use, and "Clear cache" to remove downloaded
  photos, videos, voice messages, music and files. Your chats and secret chats are not affected; files download
  again when you open them.
- About: the app version, a link to the source code, the license and a note that Pingogram is an unofficial
  Telegram client.
- Edit your profile from Settings: set a new profile photo from the photo picker (with upload progress) or remove
  it, and change your first and last name, your username and your bio. Usernames are checked as you type
  (available, taken or for sale on Fragment), Save applies only what changed, and leaving with unsaved changes
  asks first.
- Privacy and security in Settings: choose who can see your phone number, last seen and online status and
  profile photos, whose forwards of your messages link to your account, and who can call you, add you to groups
  and channels or send you voice and video messages (limiting the last needs Telegram Premium). Exceptions set
  in other apps are shown and kept when you change a choice.
- Blocked users: everyone you blocked, with a button to unblock and Undo.
- Active sessions: the devices logged in to your account. Confirm new logins, or terminate one session or all
  other sessions.
- Whether two-step verification is on, and whether a recovery email is set.

### Changed
- Settings follow the Material 3 Expressive look of Google's own apps. Your account sits on a card at the top (photo,
  name, phone number, username, and a Premium label for Telegram Premium subscribers); tap it to edit your profile.
  Every settings page shows its options as connected, rounded groups whose corners round out when you press them.
- The app's font is stored uncompressed, so Android reads it straight from the app when the first text draws
  instead of unpacking all 4 MB into memory: a faster start, for about 1.7 MB more download.
- Text is set the way Google sets Google Sans Flex on Android: Material's weights (titles were one to three
  steps heavier), without the extra letter spacing meant for Roboto, and rounded letters for emphasized text
  only. Sizes are unchanged, but text reads lighter and takes less room.
- Going back from a chat is smoother: the floating navigation bar stays ready while it is hidden instead of being
  rebuilt, and keeps its tab selected while it slides away and back. Chat list rows do less work as they appear;
  screen-reader descriptions are built only when a screen reader asks for them.
- Message bubbles follow Material 3 Expressive: round corners (one-line messages are pills), and runs of messages from
  one sender sit close together with tight corners on the sender's side, without tails. A message of one to three
  emoji shows large, without a bubble. Reactions show as round badges on the bubble's corner, up to three and then
  "+N"; tap one to add or take back yours.
- Conversations open more smoothly: no loading spinner flashes while messages come from the device.

### Fixed
- A chat with unread messages opens at the first unread message instead of the newest, also when there are many.
  Quotes and search results far back load the messages around them at once, and the position in a conversation
  survives rotation and the app being closed in the background.
- The chat list shows its "Connecting…" status once, below the title, instead of twice while the top bar collapses.
- Leaving a chat after playing a voice message or song no longer holds up the screen for a moment: the audio
  player now runs on its own thread, and frees itself there.
- "Clear cache" and "Log out" keep their card while they run, and settings page titles fit at large font sizes
  instead of being cut off.

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

[Unreleased]: https://github.com/kyra-labs/pingogram-android/compare/v0.3.0-beta.2...HEAD
[0.3.0-beta.2]: https://github.com/kyra-labs/pingogram-android/compare/v0.3.0-beta.1...v0.3.0-beta.2
[0.3.0-beta.1]: https://github.com/kyra-labs/pingogram-android/compare/v0.2.0...v0.3.0-beta.1
[0.2.0]: https://github.com/kyra-labs/pingogram-android/compare/v0.1.2...v0.2.0
[0.1.2]: https://github.com/kyra-labs/pingogram-android/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/kyra-labs/pingogram-android/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/kyra-labs/pingogram-android/releases/tag/v0.1.0
