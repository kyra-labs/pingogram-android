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
│                    files, settings), mappers TdApi → domain    │
│  :core:domain    — plain Kotlin models + use cases, no Android │
│  :core:tdlib     — TdlibClient (coroutine wrapper), TDLib AAR, │
│                    lifecycle, auth state machine               │
│  :core:common    — Result/Error types, dispatchers, utils      │
│  :core:datastore — Preferences (DataStore Proto)               │
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
  (`@Binds @IntoSet` in `RepositoryModule`) that `PingogramApplication` injects before
  `TdlibClient.start()`. Every new caching repository must join that set.
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
  composable is reused in the detail pane.
- Deep links: `tg://`, `https://t.me/...`, `https://telegram.me/...` resolved through
  TDLib `getInternalLinkType` in a `LinkRouter` and dispatched to routes.
- Predictive back: `PredictiveBackHandler` for the media viewer, attach sheet and selection
  mode; everything else relies on Navigation's built-in support.

## Threading

- TDLib callbacks arrive on TDLib's thread; `TdlibClient` immediately hops to
  `Dispatchers.Default`. Nothing TDLib-related touches `Main` except state emission.
- File IO (saving media) on `Dispatchers.IO`. Image decoding via Coil's own threads.
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

## Notifications

- `core:notifications` listens to `updateNotificationGroup`/`updateNotification` from TDLib
  (TDLib's own notification engine, enabled via `setOption("notification_group_count_max")`),
  which already applies mute settings and exceptions. We only render.
- `MessagingStyle` with `Person` and `ShortcutInfo` for conversation notifications; reply
  action → `sendMessage` through a `BroadcastReceiver` + WorkManager; mark-read → `viewMessages`.

## Error handling

- `TdResult<T> = Success(value) | Failure(TdError)`; repositories convert to domain errors:
  `FloodWait(seconds)`, `Unauthorized`, `NotFound`, `Network`, `Unknown(code, msg)`.
- ViewModels map domain errors to user strings in `core:ui` (`ErrorText(error)`), never show
  raw TDLib messages except in debug builds.

## Build & modules

- `build-logic/convention` with plugins: `pingogram.android.application`,
  `pingogram.android.library`, `pingogram.android.feature`, `pingogram.android.compose`,
  `pingogram.android.hilt`, `pingogram.jvm.library`, `pingogram.android.lint`, `pingogram.ktlint`,
  `pingogram.detekt`, `pingogram.tdlib` (the TDLib AAR for `:core:tdlib`, `docs/TDLIB.md`).
  `build-logic/detekt-rules` holds our detekt rules (`PingogramHardcodedToken`).
- `gradle/libs.versions.toml` is the only place versions live. Compose BOM pins Compose.
- `secrets.properties` (git-ignored) → `BuildConfig.TELEGRAM_API_ID` (int) /
  `TELEGRAM_API_HASH` (String), parsed and validated in `build-logic` (`TelegramSecrets`, applied
  by `pingogram.android.application`). CI writes the file from GitHub secrets. A missing file or
  key, a non-integer id or a hash that is not 32 hex characters fails configuration with a
  readable message that names the key but never its value.
- Product flavors: `foss` (no Google services) and `gms` (FCM push). Default assembled flavor
  for GitHub releases: `foss`. Build types: `debug` (suffix `.debug`, debuggable), `release`
  (R8 full mode, resource shrinking, Baseline Profiles).

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
