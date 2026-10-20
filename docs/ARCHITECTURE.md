# Architecture

## Overview

```
┌────────────────────────────────────────────────────────────────┐
│  :app  — Application, MainActivity, NavHost, DI graph, theme   │
├────────────────────────────────────────────────────────────────┤
│  :feature:*  — one module per user-facing area                 │
│   auth · chats · chat · contacts · profile · settings · media   │
│   Each: Screen composables + ViewModel + UiState + navigation  │
├────────────────────────────────────────────────────────────────┤
│  :core:ui        — PingogramTheme (M3 Expressive), shared       │
│                    components, previews, icons                 │
│  :core:data      — Repositories (chats, messages, users,       │
│                    files, storage), mappers TdApi → domain     │
│  :core:domain    — plain Kotlin models + use cases, no Android │
│  :core:tdlib     — TdlibClient (coroutine wrapper), TDLib AAR, │
│                    lifecycle, auth state machine               │
│  :core:common    — Result/Error types, dispatchers, utils      │
│  :core:datastore — app settings (Preferences DataStore)        │
│  :core:notifications — channels, MessagingStyle builder        │
│  :core:testing   — fakes, test rules, fixtures                 │
├────────────────────────────────────────────────────────────────┤
│  build-logic/   — Gradle convention plugins                    │
│  benchmark/     — Macrobenchmark + Baseline Profile generator  │
└────────────────────────────────────────────────────────────────┘
```

Dependency direction is strictly downward: `app → feature → core`, `feature ↛ feature`
(features talk only via navigation routes defined in `:core:navigation` — a tiny module holding
`@Serializable` route objects). `core:domain` depends on nothing Android.

## Layers and data flow

```
Composable ──events──▶ ViewModel ──▶ UseCase / Repository ──▶ TdlibClient ──▶ TDLib (JNI)
    ▲                     │                     ▲                   │
    └──── UiState (StateFlow) ◀─────────────────┴──── Flow<Update> ◀┘
```

- **TdlibClient** (`core:tdlib`, an interface; `DefaultTdlibClient` in production,
  `FakeTdlibClient` in tests) owns exactly one `org.drinkless.tdlib.Client` per logged-in
  account. It exposes (details in `docs/TDLIB.md`):
  - `suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): TdResult<T>` — wraps
    `Client.send` in `suspendCancellableCoroutine`; maps `TdApi.Error` to `TdError(code, message)`.
  - `val updates: SharedFlow<TdApi.Update>` — hot, no replay, buffered
    (`extraBufferCapacity = 4096`, `SUSPEND`, never dropping), collected by repositories on
    `Dispatchers.Default`.
  - `val authState: StateFlow<AuthState>` — typed mirror of `updateAuthorizationState`.
  - `val connectionState: StateFlow<ConnectionState>`.
  - `fun start()` — called once by `PingogramApplication`; creates the client in the background.
- **Repositories** (`core:data`) keep in-memory caches keyed by TDLib ids (chat, user, message)
  as `MutableStateFlow<Map<Long, Chat>>` and apply updates incrementally. They never expose
  `TdApi` types upward; they map to domain models (`core:domain`). TDLib already persists
  everything in its SQLite DB, so **we do not add Room**.
  TDLib introduces each user and chat only once and `updates` replays nothing, so caches subscribe
  at app start: a caching repository implements `TdlibUpdateCache`, keeps its state in an
  `UpdateStore` (subscribed before its constructor returns, one immutable snapshot reduced from
  every update, reset on `AuthorizationStateClosed`) and joins the `Set<TdlibUpdateCache>`
  (`@Binds @IntoSet` in `UpdateCacheModule`) that `PingogramApplication` injects before
  `TdlibClient.start()`. Every new caching repository must join that set.
- **Settings** (`core:datastore`) are the app's own, kept on the device and kept across logouts: one
  Preferences DataStore file (`filesDir/datastore/settings.preferences_pb`, `DataStoreModule`, one
  instance per process, IO dispatcher) behind the settings repositories of `core:domain`, e.g.
  `DataStoreAppearanceRepository` for `AppearanceRepository`. Preferences rather than Proto
  DataStore: Proto needs the protobuf Gradle plugin and `protobuf-javalite`, which the project does
  not use, for a handful of typed keys. Enums are stored by name; unreadable or unknown values read
  as the defaults, and a write that fails reaches the ViewModel as an `Outcome.Failure`.
- **Theme**: `MainActivityViewModel.appearance` (null until the settings are read) feeds
  `PingogramTheme` in `MainActivity`, which also styles the system bar icons for the theme it
  resolves to. The splash screen waits for the settings as for TDLib's first login step (at most
  300 ms, `docs/PERFORMANCE.md` §2), and nothing is composed before they are known, so the app never
  draws in a theme the user did not choose. A change made in Appearance reaches the theme through the
  same flow and cross-fades its colors (`docs/DESIGN_SYSTEM.md` §1).
- **ViewModels** expose a single `StateFlow<UiState>` (sealed: `Loading`, `Content`, `Error`,
  `Empty`) and accept events through plain functions. Side effects (navigation, snackbars) go
  through a `Channel<Effect>` consumed with `LaunchedEffect`. ViewModels are `@HiltViewModel`,
  take `SavedStateHandle` for route args, and use `viewModelScope` only.
- **Composables** are stateless where possible (`Screen(state, onEvent)`), with a thin
  `Route` composable that hoists the ViewModel. Every `Screen` has `@Preview`s for each state
  using `core:ui` preview parameter providers.

## Navigation

- Single-activity, `NavHost` in `:app`, type-safe routes with `kotlinx.serialization`.
- Graphs: `AuthGraph`, `MainGraph` (bottom nav / navigation rail: Chats, Contacts, Settings),
  nested `ChatRoute(chatId, messageId?)`, `ProfileRoute(userId)`, etc.
- Adaptive: on `WindowWidthSizeClass.Expanded` the Chats tab uses `ListDetailPaneScaffold`
  (`material3-adaptive`) so list + conversation show side by side; the same `ChatScreen`
  composable is reused in the detail pane. `:app` composes it (`ChatsListDetail`, a
  `NavigableListDetailPaneScaffold`) from `feature:chats`' `ChatListPane` and `feature:chat`'s
  `ChatPane`; the navigator holds the open chat. With one pane (compact, medium) a tap still pushes
  `ChatRoute`, and the archive pushes it on every window. New chat (`NewChatRoute`, `feature:chats`) is pushed above
  the Chats tab on every window too; a contact's chat takes its place on the back stack, so back from the chat
  returns to the chat list.
- Deep links: `tg://`, `https://t.me/...`, `https://telegram.me/...` resolved through
  TDLib `getInternalLinkType` in a `LinkRouter` and dispatched to routes.
- Predictive back: `PredictiveBackHandler` for the media viewer, attach sheet and selection
  mode; everything else relies on Navigation's built-in support, with the `NavHost`'s predictive
  pop transitions from `core:ui` (`docs/DESIGN_SYSTEM.md` §5).
- Shared elements: the `NavHost` sits in a `SharedTransitionLayout`, whose scope `core:ui` hands
  down as `LocalSharedTransitionScope` (`ProvideSharedTransitionScope`); destinations that share content
  provide their animated visibility scope (`ProvideNavAnimatedVisibilityScope`), and
  `Modifier.containerTransform` (a no-op without both) runs a container transform between the two
  destinations' content of one key: the Chats tab's "New chat" button grows into the New chat screen.
  `Modifier.sharedMediaBounds` (the bubble's side) and `Modifier.sharedMediaViewerBounds` (the viewer's)
  run that of a photo, video or GIF between the conversation (pushed or in the detail pane) and the media
  viewer, keyed by message id. Each bubble publishes its media's clip shape in `SharedMediaShapes`
  (`LocalSharedMediaShapes`, from a `Modifier.Node` while it is laid out), so the viewer morphs from and
  back to exactly those corners.
- "Show in chat" in the media viewer pops it and pushes `ChatRoute(chatId, messageId)` in place of the
  conversation of the same chat below it, if there is one.

## Threading

- TDLib callbacks arrive on TDLib's thread; `TdlibClient` immediately hops to
  `Dispatchers.Default`. Nothing TDLib-related touches `Main` except state emission.
- File IO (saving media) on `Dispatchers.IO`. Image decoding via Coil's own threads.
- A conversation's audio player (`ExoAudioPlayer`, Media3) runs on a thread of its own: the main
  thread posts to it and reads the position from the player's last report (`PlaybackClock`), so
  no call, `release()` included (which waits for the codec and audio track), ever blocks a frame.
- So does its voice recorder (`MediaVoiceRecorder`, the platform's `MediaRecorder`, behind `VoiceRecorder`): every
  call suspends while it runs on the recorder's thread, `cancel()` and `release()` are posted, as preparing, starting
  and stopping a recording wait for the audio input and the encoder (`docs/adr/0007-voice-recording.md`).
- Dispatchers are injected (`@Dispatcher(IO)`, `@Dispatcher(Default)`) so tests can use
  `StandardTestDispatcher`. The qualifier lives in `core:common`; `:app`'s `DispatchersModule`
  provides the bindings (`core:common` is a plain JVM module without Hilt's plugin).

## Files and media

- TDLib downloads to its `filesDirectory`; UI requests via `downloadFile(fileId, priority,
  offset, limit, synchronous=false)` and observes `updateFile`. A `FileRepository` dedups
  requests and exposes `Flow<FileState>` (`NotDownloaded`, `Downloading(progress)`,
  `Downloaded(path)`).
- Coil 3: custom `Fetcher` keyed by TDLib `fileId` that resolves the local path through
  `FileRepository`; thumbnails/minithumbnails shown while downloading (blurhash-like via
  `minithumbnail` bytes).
- Animated stickers (TGS = gzipped Lottie JSON): render with `rlottie` or Lottie Compose
  (decision in ROADMAP M3; prefer rlottie for performance, keep it behind an interface).
- Auto-download rules stored in DataStore and enforced in `MessageRepository` per network type.
- Voice messages (FR-C5) are recorded by `feature:chat`'s `ChatRecorder` (a part of `ChatViewModel`, its state kept
  out of `ChatUiState` as only the composer shows it) into a file `VoiceNoteRepository.newVoiceNoteFile`
  (`TdlibVoiceNoteRepository`, `core:data`) makes in the upload cache (`uploads/<uuid>/voice.ogg` or `.m4a`), and sent
  with `sendVoiceNote` (`inputMessageVoiceNote`); like
  picked files, the file is deleted once TDLib uploaded it (`UploadCleaner`), or when the recording is cancelled or
  refused.
- Files leave the app only on the user's request and only through `:app`'s `FileProvider`
  (`${applicationId}.files`, `res/xml/file_paths.xml`), which serves TDLib's files directories and
  never its database; `core:ui`'s `TdlibFiles.kt` builds the URIs and the share and view intents.
  The media viewer saves into the gallery through `MediaStore` (Android 10+) or the public
  directories with `WRITE_EXTERNAL_STORAGE` (Android 8–9 only), and the conversation saves files into Downloads the
  same way, both behind `SharedStorageRepository` (`core:domain`; `AndroidSharedStorageRepository` in `core:data`).

## Notifications

- TDLib's own notification engine decides what to show: it applies mute settings, exceptions and read messages,
  and removes what was read anywhere. `TdlibNotificationRepository` (`core:data`, a `TdlibUpdateCache`, as
  `updateActiveNotifications` comes only once after TDLib starts) turns it on at every login
  (`notification_group_count_max` 25, `notification_group_size_max` 10, `docs/TDLIB.md`) and reduces
  `updateActiveNotifications`, `updateNotificationGroup`, `updateNotification` and `updateHavePendingNotifications`
  into `ActiveNotifications` (`core:domain`), with the chats on screen (`OpenChats`, kept by `TdlibChatRepository` as it
  opens and closes chats). Logging out drops everything.
- `core:notifications` only renders: `NotificationRenderer` (started by `PingogramApplication` in every process,
  before TDLib) posts one `MessagingStyle` notification per TDLib group under one summary, with a `Person` per sender
  (avatars from `AvatarIcons`, initials on the placeholder colors without a photo) and the chat list's previews
  (`core:ui` `messagePreviewText`, or "New message" without `showPreview`). Only notifications not seen before alert;
  updates, silent messages and restored notifications set `GROUP_ALERT_SUMMARY` under a summary with
  `GROUP_ALERT_CHILDREN`, so they do not alert. Chats on screen get no notification. After a restart, notifications
  TDLib no longer knows are cancelled; logging out cancels everything and removes the conversation shortcuts.
- Channels (`PingogramChannel`, `NotificationChannels`): Private chats (secret chats too), Groups, Channels, Calls,
  Other, created at every start; the one place that decides channels, except the `foss` flavor's "Connection" channel
  of "Keep connected", which only that flavor creates, with its service.
- Android 11+: a long-lived conversation shortcut per chat (`ConversationShortcuts`), linked by `setShortcutId` and
  `LocusId`, so notifications show in the conversation section. No share target yet (sharing into a chat needs a chat
  picker and a prefilled composer); no bubbles yet (they need a resizable chat activity of their own).
- Tapping opens the chat: an explicit intent to the launcher activity (`NotificationIntents`), which `MainActivity`
  hands to `PingogramNavHost` (`chatToOpen`), which pushes `ChatRoute` above the chat list once logged in.
- Reply (`RemoteInput`, `SEMANTIC_ACTION_REPLY`), Mark as read (`SEMANTIC_ACTION_MARK_AS_READ`) and dismissing go
  through `NotificationActionReceiver` to `NotificationActionWorker`, expedited one-time work (WorkManager with
  `HiltWorkerFactory`, initialized on demand by `PingogramApplication`'s `Configuration.Provider`), which waits for
  TDLib (`AuthRepository.awaitLoggedIn`), then `sendMessage` (keeping the draft) + `viewMessages(force_read)`,
  `viewMessages(force_read)`, or `removeNotificationGroup`. TDLib's updates then remove the notification.
- Push (`gms` flavor only, `docs/adr/0004-push-delivery.md`): `TdlibPushRepository` (`core:data`, started by the
  `gms` `BackgroundConnection`, `FirebasePushConnection`) registers the Firebase Cloud Messaging token with `registerDevice` (encrypted) at every
  login and start while logged in and whenever FCM issues a new one, and deletes the token at logout. The token comes from `core:domain`'s `PushTokenSource`, bound only in `gms` (`FirebasePushTokenSource`,
  `app/src/gms`); `foss` binds none (`@BindsOptionalOf`) and the repository does nothing. `PingogramMessagingService`
  (`gms`) hands each push's data to `processPushNotification`, waiting a few seconds at most; TDLib's notification
  engine then produces the notifications above (`NewPushMessage` until it has synced), in the process FCM started.
- Android 13+: the chat list asks for `POST_NOTIFICATIONS` with a card above the list (`NotificationPromptHost`,
  `feature:chats`); "Not now" and the first request are kept in DataStore (`NotificationPromptRepository`), so it never
  nags, and after a final refusal it offers the app's notification settings.
- Notification settings (Settings → Notifications, `feature:settings`): `TdlibNotificationSettingsRepository`
  (`core:data`, no cache: TDLib keeps them) reads and sets the defaults of each `NotificationScope`
  (`getScopeNotificationSettings`, `setScopeNotificationSettings`, which replace every field, so it reads first and
  changes only what Pingogram shows), lists the exceptions (`getChatNotificationSettingsExceptions` without comparing
  sounds, then `getChat` for each), changes a chat's mute (`setChatNotificationSettings`, the time left of a limited
  mute turned into its end with the `WallClock`), resets everything (`resetAllNotificationSettings`) and follows
  `updateScopeNotificationSettings` and `updateChatNotificationSettings`. Sounds stay with Android: the page opens each
  scope's channel (`PingogramChannel.of(scope)`) in the system settings. It shares the chat list's
  `NotificationPromptRepository` to ask for the permission while Android still shows its dialog.
- Background delivery depends on the flavor (`PushDelivery`, bound in `app/src/foss` and `app/src/gms` with a
  `BackgroundConnection` that `PingogramApplication` starts). `foss`: the optional "Keep connected" foreground service
  (`KeepConnectedService`, type `specialUse`, its own low-importance "Connection" channel), run by
  `KeepConnectedController` from the setting (`KeepConnectedRepository`, DataStore), the login and the app being in
  the foreground, with a boot receiver enabled only while the setting is on
  (`docs/adr/0003-keep-connected-foreground-service.md`). `gms`: `FirebasePushConnection`, which starts
  `TdlibPushRepository` (Push, above).
- `core:notifications` depends on `core:domain`, `core:common` and `core:ui` (previews, names, avatar colors), never
  on `core:data` or TDLib.

## Error handling

- `TdResult<T> = Success(value) | Failure(TdError)`; repositories convert to domain errors:
  `FloodWait(seconds)`, `Unauthorized`, `NotFound`, `Network`, `Unknown(code, msg)`.
- ViewModels map domain errors to user strings in `core:ui` (`ErrorText(error)`), never show
  raw TDLib messages except in debug builds.

## Build & modules

- `build-logic/convention` with plugins: `pingogram.android.application`,
  `pingogram.android.library`, `pingogram.android.feature`, `pingogram.android.compose`,
  `pingogram.android.hilt`, `pingogram.jvm.library`, `pingogram.android.lint`, `pingogram.ktlint`,
  `pingogram.android.licenses` (the open-source licenses list of `:app`), `pingogram.android.gms` (Firebase for
  `:app`'s `gms` flavor, the `foss` Google-free check),
  `pingogram.detekt`, `pingogram.tdlib` (the TDLib AAR for `:core:tdlib`, `docs/TDLIB.md`).
  `build-logic/detekt-rules` holds our detekt rules (`PingogramHardcodedToken`).
- `gradle/libs.versions.toml` is the only place versions live. Compose BOM pins Compose.
- `secrets.properties` (git-ignored) → `BuildConfig.TELEGRAM_API_ID` (int) /
  `TELEGRAM_API_HASH` (String), parsed and validated in `build-logic` (`TelegramSecrets`, applied
  by `pingogram.android.application`). CI writes the file from GitHub secrets. A missing file or
  key, a non-integer id or a hash that is not 32 hex characters fails configuration with a
  readable message that names the key but never its value.
- Open-source licenses (About, FR-S1): `:app` applies `pingogram.android.licenses`, whose task
  `generate<Variant>OpenSourceLicenses` lists each variant's runtime dependencies at build time with
  the licenses of their POMs (parent POMs included, resolved through Gradle's own dependency
  resolution while the configuration cache entry is stored), mapped to SPDX ids (`SpdxLicenses`),
  completed by `config/licenses/extra.json` (TDLib's license, Google Sans Flex, Material Symbols)
  and `config/licenses/texts/` (every license's text, never downloaded), into the generated raw
  resource `open_source_licenses`. A dependency without a license it can name, or a license without
  a text, fails the build. `:app`'s `GeneratedOpenSourceLicensesRepository` reads it with the
  platform's `org.json` behind `core:domain`'s `OpenSourceLicensesRepository`; `feature:settings`
  draws the screens (`docs/adr/0002-open-source-licenses.md`).
- Product flavors: `foss` (no Google services) and `gms` (FCM push). Default assembled flavor
  for GitHub releases: `foss`. `gms` alone depends on Firebase Cloud Messaging (`gmsImplementation`); `foss` variants
  fail `check<Variant>GoogleFree` (run by `check` and by their builds) if any Firebase or Play services module reaches
  their runtime classpath. `pingogram.android.gms` applies the Google Services plugin only when the owner's
  git-ignored `app/src/gms/google-services.json` exists; without it, packaging a `gms` variant fails before anything
  runs (`docs/adr/0004-push-delivery.md`, `docs/OWNER_SETUP.md` §8b). Build types: `debug` (suffix `.debug`,
  debuggable), `release` (R8 full mode, resource shrinking, Baseline Profiles).

## Naming conventions

- Packages: `dev.kyralabs.pingogram.<module>.<layer>` e.g. `dev.kyralabs.pingogram.feature.chat.ui`,
  `dev.kyralabs.pingogram.core.data.repository`.
- Composables: `ChatListScreen`, `ChatListRoute`, `ChatListItem`; previews `ChatListScreenPreview`.
- ViewModel: `ChatListViewModel`; state `ChatListUiState`; events `ChatListEvent`; effects `ChatListEffect`.
- Repositories: interface `ChatRepository` in `core:domain`, impl `TdlibChatRepository` in `core:data`.
- Tests mirror source paths; test names `fun \`loads chats and applies position updates\`()`.

## Decision log (ADRs)

Add a file `docs/adr/NNNN-title.md` for each significant decision (TDLib vs fork, rlottie vs
Lottie, FCM flavor, etc.). Template: Context · Decision · Consequences.
