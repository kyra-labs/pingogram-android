# Performance & optimization

Goal: Pingogram should feel faster and lighter than the official Telegram app on the same
device, while using less battery and data. "Optimized" is measured, not claimed: every budget
below has a Macrobenchmark, `dumpsys` or Perfetto check (§9). There is no device in CI, so the
owner runs the benchmarks on their phone before each release (`docs/RELEASE.md`), and
`tools/benchmark/compare.py` fails the run when a budget is missed or a figure regresses by more
than 10% against that phone's baseline. Only the APK size budget is checked in CI, on every pull
request (§8).

## 1. Budgets (release build, `foss`, Pixel-class 2022 mid-range reference device)

| Metric | Budget | How measured |
|---|---|---|
| Cold start → chat list drawn | ≤ 1.2 s (`timeToFullDisplay`) | Macrobenchmark `StartupBenchmark`, `reportFullyDrawn()` after first chat list frame |
| Warm start | ≤ 400 ms | same |
| Chat list scroll (1000 chats) | P90 frame ≤ 8 ms @120 Hz, 0 frames > 32 ms, stutter ≤ 0.5% (frames > 16 ms late; `docs/adr/0042-stutter-instead-of-late-frames.md`) | `ChatListScrollBenchmark` (frame timing) |
| Conversation scroll (10k messages, mixed media) | P90 ≤ 10 ms, stutter ≤ 0.5% (frames > 16 ms late; ADR 0042) | same |
| Open chat (tap → first messages visible) | ≤ 150 ms with cached history | `OpenChatBenchmark`: `TraceSectionMetric("openChat")`, an async section from the tap in the chat list (`:app`) to the frame after the first messages (`feature:chat`) |
| Send text message (tap → bubble shown) | ≤ 50 ms (optimistic) | `SendMessageBenchmark`: `TraceSectionMetric("sendMessage")`, an async section from the composer's send (`feature:chat`'s `ChatComposer`) to the frame after the bubble on its way shows (`SendMessageTraceEffect`), sending to Saved Messages and deleting each message after |
| Memory, chat list idle | ≤ 120 MB PSS | `dumpsys meminfo` on the owner's phone before each release; `ChatListIdleMemoryBenchmark` follows its RSS and heap against the baseline (§9) |
| Memory, 10k-message media chat | ≤ 250 MB PSS; no OOM at 2× font scale | `dumpsys meminfo` on the owner's phone before each release |
| APK size (arm64-v8a) | ≤ 53 MB; universal ≤ 128 MB (raised from 35 / 60 MB for the call engine by the owner, 2026-10-22, `docs/adr/0015-call-media-engine.md`; set from the APKs with voice chats at their size plus a small margin, owner 2026-10-23, `docs/adr/0023-group-voice-chats-and-live-streams.md`; raised by 1 and 2 MB for TDLib 1.8.70 by the owner, 2026-11-11, `docs/adr/0038-apk-budget-for-tdlib-1-8-70.md`; raised by 2 and 3 MB for the features after v2.3.0 by the owner, 2026-11-20, `docs/adr/0040-apk-budget-after-v2-3-0.md`) | CI on every pull request and release (§8) |
| Battery, 8 h background with 50 active chats | ≤ 1.5% (Battery Historian) | manual weekly |
| Data, 24 h idle | ≤ 2 MB (TDLib keep-alive only) | TDLib `getNetworkStatistics` |
| Composition count per frame while scrolling | 0 recompositions of off-screen items | Layout Inspector / compiler reports |

The memory budgets are for one account. Each further account signed in (`docs/adr/0031-multiple-accounts.md`) keeps
its own TDLib client, connection and caches running; how much that adds is not measured yet, and its budget is set
from the first measurement on the owner's phone (`dumpsys meminfo` with one, two and three accounts idle).

## 2. Startup

- `SplashScreen` API; keep the splash until `TdlibClient` reaches `Ready`/`WaitPhoneNumber` and the
  Appearance settings are read from DataStore (a few ms), **or** 300 ms, whichever is first; then
  render cached chats immediately. The splash screen reads TDLib's current step (`AuthRepository.currentStep`), which
  the client sets as TDLib reports it, rather than waiting for the login steps' flow, which comes through the update
  router (§3) and `Default`, both busy at a cold start. Each state it waits for has an async trace section from the
  main view model's creation until it is known (`PG:loginKnown`, `PG:appearanceKnown`, `PG:lockKnown`), which
  `StartupBenchmark` reports. The splash's exit animation (`docs/DESIGN_SYSTEM.md` §8) runs over the app's first
  frame and holds nothing back; the splash theme for the next start is handed to the system off the main thread.
- Start-up binder calls the first frame does not need wait until the window drew it (`FirstFrameGate`; 3 s in a
  process that shows no activity): deleting the notification channels of accounts that left the device, and posting
  again, with its conversation shortcut, a restored notification Android no longer shows.
- Hilt: `@Singleton` only for `TdlibClient`, repositories and DataStore; everything else
  scoped. No heavy work in `Application.onCreate()`; use `App Startup` initializers that are
  lazy except TDLib. WorkManager starts on demand (its App Startup initializer is removed; `PingogramApplication` is
  its `Configuration.Provider`), when a notification action first enqueues work, or 5 s after the process started,
  when `StorageOptimization` schedules the weekly clean-up of the accounts logged in (§3; owner decision 2026-10-27),
  well after the first frame and the chat list's first page.
- Baseline Profiles + Startup Profiles generated by `benchmark/` and shipped (`profileinstaller`).
  Regenerate on every minor release from a clean install: uninstall Pingogram from the device, install
  `nonMinifiedRelease` and sign in again (§9). During collection the first speed-profile compile seeds the profile from
  the one Android keeps next to the installed app (`base.apk.prof`, from earlier installs and runs); clearing the app's
  profiles and reinstalling over it leave that file in place, and only an uninstall removes it. Without the clean
  install every regeneration carried the last profile forward: until v2.5.0 the Startup Profile held 98% of the
  Baseline Profile, conversation and settings code that no start runs, and dex layout optimization had nothing to order
  (the owner's runs of 2026-11-25). Regenerate with `tools/benchmark/generate-profiles.sh` (`generate-profiles.ps1` on
  Windows, §9), which also builds the APK under test without the committed profiles; the app's part of the new Startup
  Profile should be about what a cold start runs, and much smaller than the Baseline Profile's.
- R8 full mode, `-allowaccessmodification`, resource shrinking, `isDebuggable=false`,
  `android.enableR8.fullMode=true`; keep rules minimal and documented.
- TDLib: create the client on a background thread immediately; send `SetTdlibParameters` before
  the first frame; `getChats(limit = 20)` first, then `loadChats` in pages of 50 while idle.
- On the way to the first frame and the first login step (review of 2026-10-22, all off the main thread unless said):
  - `DefaultTdlibClient` builds TDLib's parameters while TDLib's library loads and its client starts. The database
    key's Keystore unwrap takes most of that time, and the two used to run one after the other.
  - `PingogramApplication` parses Google Sans Flex and makes the typefaces of every style on `Default`
    (`warmUpGoogleSansFlex`). The first frame used to make them while it measured its text.
  - Then it measures the floating navigation bar's labels with them (`NavigationLabelWidths.warmUp`, 2026-11-15), whose
    width decides whether the tabs show their names (`labelsFit`). The first frame used to measure the three labels in
    two styles, which made it slower without a baseline profile after #494 (`StartupBenchmark` with
    `CompilationMode.None`: cold +19%, the warm first frame +18–22%). The width is kept for the process, so a warm start
    measures nothing; a first frame that asks for other labels or styles (high contrast, another language before
    Android 13) measures them itself, the same way, so the tabs never switch between names and icons after. Both
    measure without emoji support (the labels hold none): Compose's emoji status makes its snapshot state on the first
    paragraph that asks for it, on that paragraph's thread, and a composition that took its snapshot before cannot read
    it, so the warm-up must make no snapshot state the first frame reads.
  - `TdlibNetworkTypeReporter` creates the network repository where it collects, on `Default`. Its binder calls used
    to run on the main thread in `Application.onCreate`.
  - The home screen widget's first redraw of a process waits 5 s (`RecentChatsWidgetUpdater.START_UPDATE_DELAY`).
    Glance composes it in a WorkManager worker, which would start WorkManager while the chat list loads. Since
    2026-11-16 the widget updaters (recent chats, Conversation) also wait that long before asking the launcher which
    widgets are placed (a binder call, then a Glance state read per widget) and loading their chats, unless a widget
    asks for its content first (a process started for it).
  - The chat list's folder tabs show no "Keyword alerts" tab until the matches are read (`ChatListViewModel`'s
    `keywordMatchCount` starts at 0), and nothing reads them while the tab is turned off, so its first content never
    waits for the account's `keyword_hits.txt`. Focus's header pill is a view model of its own over the settings file,
    which the splash screen already waits for; the Quick Settings tiles bind only when Quick Settings opens.
- Done after the v0.5.0 benchmarks (review of 2026-10-25), waiting for a trace before and after on the owner's phone
  (`tools/perfetto/startup.sh` or `startup.ps1`, below):
  - **Notifications at a cold start.** TDLib restores the active notifications at every start. `NotificationRenderer`
    posts a restored group again only when Android no longer shows it as the last process posted it: each posted group
    carries its fingerprint in the notification's extras (`GroupFingerprint`). Before, it built every avatar and
    conversation shortcut anew.
  - **Direct Share targets.** `ShareTargetShortcuts` leaves the targets Android already has unchanged: each pushed
    target carries its key (title, photo, rank) in the shortcut's extras, read once per process. Before, it pushed all
    8 with their avatars 5 s after every start.
  - **Field injection.** `PingogramApplication` injects only TDLib's client and the caching repositories (which must
    subscribe before TDLib starts). The rest is `dagger.Lazy`, made and started by one coroutine on `Default`
    (`startInBackground`, the trace section `app.starters`). Each of them follows a repository that keeps TDLib's
    state, so starting a few milliseconds after TDLib loses nothing.
  - **Firebase in `gms`.** The flavor's manifest removes `FirebaseInitProvider`, which ran on the main thread before
    `Application.onCreate` in every process. Firebase starts where it is first needed (`FirebaseStart`): the push
    token at login, on a background coroutine, and the messaging service's `onCreate`, before Firebase handles a push.
  - **The phone number setting.** `MainActivity` waits for "Hide phone numbers" with the Appearance settings, from
    the same file. It used to show the app with numbers hidden, then provide the setting's value as a static
    composition local, which composed the whole app again a frame or two after the first one.
- The first trace (2026-10-25, the owner's Galaxy S24 Ultra on Android 17, a `benchmarkRelease` build of `main` after
  the changes above, 5 cold starts):
  - **To the first frame:** 165–175 ms, and 261 ms for the first start after installing.
  - **The splash screen waits for the settings file** (`main.awaitSettings`): 52–81 ms, 66 ms on average. The main
    thread sleeps for 50–60 ms of each cold start, so this is the wait, not work.
    - The lock state needs only the passcode settings, so nothing in it waits for TDLib. All of it is the file's
      first read in a cold process.
    - `PingogramApplication` now starts that read while the activity is created (`app.readSettings`), so the activity
      finds the settings in memory; the next trace shows how much of the wait goes.
  - **The app's sections are small:** `app.inject` 4.4 ms, `main.onCreate` 9 ms, `app.starters` 4.8 ms (in the
    background).
  - **The emoji font** (`androidx.emoji2`, which Compose brings): its initializer takes 0.5 ms on the main thread. The
    font loads about 600 ms after the start, 9 ms on a thread of its own, well after the first frame, and the
    paragraphs laid out again after it did not show among the long frames. It stays: removing it would change how new
    emoji draw on older Android versions for no measurable gain.
  - **Compose worked out its semantics in every frame:** about 1.2 s over the trace (`checkForSemanticsChanges`,
    `sendAccessibilitySemanticsStructureChangeEvents`). It does that only while an accessibility service runs, so one
    was on during this trace and the benchmarks of §9. The scripts now write the running services into `device.txt`.
  - **The warm starts did not happen:** Android reported them as hot. "Don't keep activities" did not destroy the
    activity on this phone, so the scripts now start warm starts with `NEW_TASK | CLEAR_TASK`.
- The warm start is back within 10% of the first baseline: 127 ms with the profile of v0.6.0 (#271), against 135 ms
  then and 171 ms in v0.5.0 (§9), as the start fixes above came in.
- A low-end phone (2026-10-28: Redmi Note 5A, Snapdragon 430 at 1.4 GHz, 2.7 GB, eMMC, 720 × 1280 at 60 Hz,
  LineageOS on Android 17; `benchmarkRelease` of `main` at 7a902d20, the v0.6.0 profile): cold start 980 ms to the
  first frame and 1,921 ms to full display (medians of 10), against 310 and 425 ms on the S24 Ultra. The budget of §1
  is for a 2022 mid-range phone, far above this one. Where the median start's time goes (iteration 4, from the launch):
  - **To the end of `Application.onCreate`**, about 590 ms: the process (190 ms), then opening the APK's compiled code
    and its app image (240 ms, 150 ms of it waiting for the storage), resources, the App Startup initializers and
    Hilt's injection (`app.inject`, 34 ms).
  - **`BOOT_COMPLETED`**, 82 ms on the main thread before the activity starts: a benchmark cost (above).
  - **The activity and the splash screen**, about 340 ms: creating and resuming the activity (140 ms, `main.onCreate`
    31 ms) and the wait for the settings (`main.awaitSettings`, 155 ms; median 175 ms).
  - **The first frame**, 400 ms: its composition 135 ms, then 229 ms waiting for the RenderThread, which compiles the
    shaders of its first draw. Time to initial display: 1,415 ms.
  - **The chat list's first content**, 265 ms on the main thread, 189 ms of it measuring the rows (their texts), then
    180 ms on the RenderThread, for which the main thread waits in the next frame. Time to full display: 1,923 ms.
  - Shader compiling takes about 300 ms of the RenderThread's time and `BOOT_COMPLETED` 82 ms of the main thread's,
    both paid for once in a user's starts (above), so a user's cold start there is about 1.5 s. The 8 cores spend
    about 2.5 s of CPU time in the app's background threads by full display, without trace sections to say on what
    (TDLib's first updates, the caches, the fonts); the main thread waits about 100 ms for a core.
- Measuring a start on a phone: `tools/perfetto/startup.sh` (`startup.ps1` in PowerShell) records one Perfetto trace
  (`startup.pbtxt`) of 5 cold and 5 warm starts of an installed, profileable build (`benchmarkRelease`), with what
  `am start -W` reports for each.
  A warm start keeps the process and starts the activity with `NEW_TASK | CLEAR_TASK`, which destroys it and creates it
  again. `startup.sql` summarizes the trace: each start's time to first frame
  and to full display, the app's sections (`app.inject`, `app.tdlibStart`, `app.starters`, `app.readSettings`,
  `main.onCreate`, `main.awaitSettings`), the main thread's longest framework slices and states, and the emoji font's load. The trace
  runs to hundreds of MB (300 MB on a Galaxy S24 Ultra before CPU idle states and binder transactions were left out),
  too large to send: `summarize.py` runs `startup.sql` on it with Perfetto's Python package (`pip install perfetto`)
  into `summary.txt`, a few KB of numbers, which the scripts write themselves where the package is installed.
  `StartupBenchmark` reports `app.inject`, `main.onCreate` and `main.awaitSettings` beside the start times.

## 3. TDLib usage patterns

- One `Client`; **never** block on `send`. All waits are suspend functions with 30 s timeouts
  and `FLOOD_WAIT` back-off (exponential, capped at the server-provided seconds). Exception:
  authentication requests wait 90 s, because TDLib itself retries them through `FLOOD_WAIT` for up
  to 60 s (`total_timeout_limit`) before answering, and giving up earlier makes a retry worsen the flood.
- `openChat` on enter / `closeChat` on leave so TDLib can prioritize; `viewMessages` batched
  (max once per 300 ms) with `forceRead=false`.
- History paging (the channel feed's first read too): `getChatHistory(limit=50, onlyLocal=true)` first, then network
  fill; prefetch the next page when the user is 20 items from the end, in both directions. A chat with unread
  messages opens around the first unread one (`from_message_id = last_read_inbox_message_id`,
  `offset = -30`), a jump to a message far back loads the page around it, never every page up to
  it; the conversation shows nothing until that page is there, so it never jumps. A history holds at most 300
  messages (six pages, `HistoryCache`'s `MAX_WINDOW_MESSAGES`): a page loaded past that drops as many from the other
  end, which loads again as pages when the list comes back to it.
- Files: `downloadFile(priority)` — 32 for visible media, 16 for prefetch, 1 for background;
  `cancelDownloadFile` when the item leaves the viewport by more than one screen. Pingogram does not limit how many
  downloads run at once: TDLib schedules them itself, by priority, over its own connections. Each observed file has
  a state of its own in `TdlibFileRepository`, so an `updateFile` wakes only that file's observers, and the last 1,000
  files seen downloaded in full show again without a `getFile`.
- Streaming (`docs/adr/0028-streaming-media.md`): a video that plays as it downloads downloads from where it plays at
  32, as visible media, and only while the player reads it: closing the viewer, turning the page or seeking elsewhere
  stops it (`cancelDownloadFile`, what came stays). Its buffer is sized as a file on the phone is (`PlayerBuffers.kt`):
  time first, at least a second ahead, then about 19 MB at most, where Media3 would size `tdlib-file://` as a server's
  stream, 50 s and up to 131 MB of video; it keeps no copy of the file. Each read that waits costs a `getFile` and,
  when it moves, a `downloadFile`. A video decoder that fails to start gives way to the device's next one.
- Auto-download rules per network type (the default network from `ConnectivityManager`'s callback: Wi-Fi, mobile,
  roaming, metered; `TdlibNetworkTypeReporter` sends each new network to TDLib with `setNetworkType`); respect Android
  Data Saver (nothing downloads by itself on a metered network while it is on) and Battery Saver. Only the media of
  messages on screen auto-download (`ChatAutoDownloads`, prefetch priority, once per file per screen), never the whole
  history; a started download is not cancelled when the network or the screen changes, as the viewer may show it,
  but those still on their way stop when the conversation closes (`ChatAutoDownloads.cancelStarted`), as do the
  stories viewer's (`docs/audit/performance-review-2026-11.md`, W3).
- Storage: `optimizeStorage` once a week per account (owner decision 2026-10-27), and "Clear cache" in Data and storage
  as it is, with `getStorageStatisticsFast`. The weekly clean-up (`core:notifications`' `StorageOptimizationWorker`,
  `StorageRepository.optimizeStorage`):
  - deletes the files "Clear cache" would delete that were not used for 30 days, then the oldest of them until they take
    at most 4 GB (§8), keeping the files of the chats pinned in the main list and the archive, and TDLib's default
    immunity of an hour for new files;
  - is unique periodic work per account slot (`storage_optimization_<slot>`, `ExistingPeriodicWorkPolicy.KEEP`), so a
    start does not move its next run, scheduled once the account is logged in and cancelled when it logs out or leaves
    the device (`StorageOptimization`, started by `PingogramApplication` 5 s after the process, §2);
  - runs only while the phone charges and is idle (`requiresCharging`, `requiresDeviceIdle`; not `requiresStorageNotLow`,
    as low storage is when it helps most);
  - never runs while the account's transfers or a chat export are on their way, whose files it could delete: it tries again
    the next time the phone charges and is idle.
- Online state: `setOption("online", true)` only while a visible Activity exists; false on
  `onStop` (`TdlibNetworkTypeReporter`, from `ProcessLifecycleOwner`). TDLib reconnects at once only while online:
  offline it backs off for longer and longer, so a process Android froze in the background came back "Connecting…".
  Coming back while TDLib is not connected also sends the network again (`setNetworkType`), so it reconnects at once.
- Updates fan-out: one collector per account, `core:data`'s `TdlibUpdateRouter`, takes `TdlibClient.updates` and
  hands each update only to the handlers and flows that asked for its type; repositories apply them to in-memory
  maps on `Dispatchers.Default` and emit **immutable snapshots** (persistent collections:
  `kotlinx.collections.immutable`). **Never collect `TdlibClient.updates` directly**: a collector of its own wakes for
  every update TDLib sends. Measured on a Galaxy S24 Ultra (2026-11-20, `main` at #752, against v2.3.0): with about
  35 always-on collectors, warm start was 23–32% slower and the chat list's scroll P90/P95 12–19%; `Dispatchers.Default`
  used 11.4 s of CPU in a 32 s scroll against the main thread's 1.2 s, and 85% of monitor contention was the shared
  flow's slot lock (`SharedFlowImpl.tryTakeValue`). A repository declares the update types it handles, a reducer all of
  them, and watches the session's end with `onSessionEnd`, not every update. `TdlibUpdatesSubscriptionTest` builds an
  account's whole graph on `FakeTdlibClient`, then collects every flow its repositories offer, and fails if anything but
  the router subscribes to `updates`.
- Chat lists: `ChatRepository.chatList(list)` is one shared flow per list (`shareIn`, while anyone collects it), so
  the main list is sorted once per change however many follow it: the selected tab, the Feed tab's count, the header's
  mention count, the channel feed, widgets and share shortcuts. Before, each sorted its own copy; the Feed tab's count
  (#696) added a second sort of the main list on every chat change.
- Always on in the background (audited 2026-11-20): the update caches and what is derived from them with
  `SharingStarted.Eagerly` (the user's id, transfers, custom emoji, the quick reaction, a frozen account, the chat
  lock: each a small map of one cache's state), keyword alerts, the upload cleaner, the network reporter, the share
  shortcuts (`ShareTargetShortcuts`) and the widgets while placed. The rest follows TDLib only while a screen collects
  it. The share shortcuts work out their targets at once on start, then
  only once their chats, users and groups have settled for 2 s (`settled`, owner decision 2026-11-20), and publish
  5 s after their targets last changed.
- Never keep more than the visible chat's messages in memory; evict other chats' message
  caches with an LRU of 3 chats; `onTrimMemory` (`PingogramApplication`, from `TRIM_MEMORY_UI_HIDDEN` on: the running
  levels are deprecated since Android 14 and no longer sent) drops the history of every chat no screen observes.

## 4. Compose performance

- Kotlin 2.x with strong skipping; all UI models are `@Immutable` data classes using
  `ImmutableList`/`ImmutableMap`; no `List<T>` in UI state.
- `LazyColumn`: stable `key`, `contentType` per message kind, `reverseLayout` for chat,
  `rememberLazyListState` saved in `SavedStateHandle`, `LazyListPrefetchStrategy` default. The chat list and the
  conversation take `core:ui`'s `rememberScrollingListState`, a list state with a cache window
  (`LazyLayoutCacheWindow`, half a viewport ahead and one behind): rows near the viewport compose and measure ahead in
  the frames' idle time instead of one at a time in the frame that shows them, and rows prefetched there stay composed
  for a screen behind, so a fling back does not compose them again. On the JVM (`*ScrollCostTest`, §9) it took the
  P90 of a scroll frame's CPU time 2–7% and its P99 2–9% lower in the chat list, and 11–12% and 15–21% lower in the
  conversation, for 5–9% more at the median, where it prefetches; Robolectric runs that prefetch inside the frames,
  so a phone, where it runs between them, gains more.
- Per-item rules: no `LaunchedEffect`/`collectAsState` inside items; derive everything in the
  ViewModel. Timestamps formatted once in the mapper (recompute on minute tick through a single
  shared `StateFlow<Instant>` consumed by the ViewModel, not by composables).
- Animations use `graphicsLayer { }`, `Modifier.offset { }`, `drawWithCache` lambdas — read
  animated state in draw/layout phase, not composition.
- Text: `BasicText` with precomputed `AnnotatedString` from entities (cached per message id);
  `TextLayoutResult` cache for bubble sizing; avoid `AnnotatedString` rebuilds on recomposition.
- Images: Coil 3 `AsyncImage` with explicit `size`, `crossfade` via motion scheme, hardware
  bitmaps, memory cache 25% of app memory class, disk cache delegated to TDLib (no second copy). Never
  `SubcomposeAsyncImage`, which subcomposes each image's states in layout (detekt `PingogramSubcomposeAsyncImage`).
- The media viewer's first and last frames (`docs/audit/performance-review-2026-11.md`, M2): the video player is built
  off the main thread and freed a second after the viewer closes, its bottom bar (caption, toolbar and tooltips)
  composes once the opening transform settled, and the overlay's entry is destroyed (its view models cleared) a frame
  after its content left the composition. `MediaViewerTransitionTest` checks the bottom bar's timing.
- No blur effects on media: a placeholder's minithumbnail is blurred once on its 40 px bitmap and drawn scaled up
  (`core:ui`'s `BlurredMinithumbnail`), and is not drawn under an image that has faded in. A `Modifier.blur` there
  re-rendered a layer the size of the media in every frame, under every media bubble while scrolling and on both sides
  of the media viewer's container transform (`docs/audit/performance-review-2026-11.md`, M1 and W1). Fades of a whole
  screen use `CompositingStrategy.ModulateAlpha` rather than an offscreen layer (the overlay of the media viewer), and
  the bubble's side of the viewer's transform is scaled to its bounds, not measured anew in every frame.
- Stickers/animations: Lottie behind `StickerAnimationDecoder` (`docs/adr/0008-lottie-for-animated-stickers.md`)
  rendering off the main thread, with a frame cache keyed by (sticker, size) that holds each sticker's first frame
  (8 MB LRU; whole animations would take ~17 MB each) and two bitmaps per playing sticker; max 8 concurrently
  animating items at ≤ 30 fps; pause everything off-screen (a frame not drawn within 250 ms) and when the window
  loses focus; a new frame invalidates only the sticker's own layer. There is no GIF or animated WebP decoder:
  Telegram sends GIFs as MP4 animations, shown in the bubble as a still with a GIF badge and played by Media3 in the
  media viewer, and WebP stickers are still images that Coil decodes with the platform's decoder.
- Custom emoji (`docs/adr/0022-custom-emoji-rendering.md`) go through the same pipeline: animated ones are decoded,
  cached (first frames by sticker and size) and played by `AnimatedStickers`, and each custom emoji that plays counts
  as one of the 8 animating items, sharing them with stickers (the same id twice in one text is one); still ones come
  from Coil's memory cache at the emoji's size, also kept by the screen's `CustomEmojiRenderer` (128 bitmaps, the same
  objects) so a text scrolling in shows them in its first frame. Nothing composes or lays out for them: the text keeps
  its fallback emoji and its cached layout, `Modifier.customEmoji` finds the glyph boxes in it once per layout, clips
  them out and draws the pictures in the text's own graphics layer, syncing its pictures when the text is placed or
  the loaded custom emoji change (`observeReads`), not in composition; loading one recomposes no bubble
  (`ChatRecompositionTest`). Only the ids of messages on screen are requested, at most every 100 ms while scrolling
  (`ChatCustomEmoji`). Scroll cost on the JVM (`ChatScrollCostTest`, a third of 2,000 messages with two still custom
  emoji, their pictures shown against the same texts with their fallback emoji only, three alternating pairs,
  2026-10-23, the shared 4-core machine): P50 1.00–1.17 ms against 1.15–1.29 ms, P90 4.5–5.2 ms against 5.1–5.6 ms,
  P99 7.5–8.7 ms against 8.7–9.6 ms, within the machine's noise. Syncing on every placement (`onPlaced`, every frame
  of a scroll) had cost 11% at P90; the modifier syncs when the text is measured instead. Robolectric does not draw,
  so the clip and the pictures' draw are measured on a phone only.
- Composition tracing enabled in benchmark builds; compiler metrics (`-P …reportsDestination`)
  reviewed in PRs that touch `feature:chat`; any unstable class in UI state is a build error
  (custom `compose_compiler_config.conf` stability file).
- Use `Modifier.Node` for custom modifiers, never `composed {}` (detekt `PingogramComposedModifier`).
- Avoid `Box`-with-many-children layouts for bubbles; one custom `Layout` per bubble type. A text bubble and a media
  bubble are each one multi-content `Layout` (`feature:chat` `BubbleLayouts.kt`: header, content, text, link preview,
  meta; a media bubble's media and overlay meta between its header and caption) with a measure policy remembered once
  per bubble, which measures every piece once, without intrinsics or subcomposition (`BubbleLayoutsTest`); the meta
  follows the text's last line from the text's cached `TextLayoutResult`.
- Build what only screen readers need (a row's or bubble's spoken description, custom actions) inside
  the `semantics { }` block, which runs only when something reads the semantics tree, not in the
  composition of every item.
- Coming back from a pushed screen composes the tab's screen anew (Navigation keeps only its saved
  state), in the first frame of the back transition: keep that composition lean, and keep app chrome
  that only hides (the floating navigation bar) composed rather than composing it again with the
  screen. A full-window screen that content grows out of and back into (the media viewer) is an overlay destination
  instead (`core:ui`'s `OverlayNavigator`, drawn by the shell's `OverlayHost`): the screen below stays composed under
  it, so the way back composes nothing. As a destination of its own, closing the viewer composed the whole
  conversation in the first frames of the photo's way back, which then moved only every 40 ms or so (a screen
  recording on the owner's phone, 2026-10-26). The way in waits for the overlay's content (`PostponeOverlayEnter`, at
  most 500 ms), as the media viewer's first page did: a search of the chat's media (`searchChatMessages`) could take
  long enough that the photo faded in over a black screen and then popped, and the bubble's media followed a transition
  of its own, so the way back snapped in one frame (a screen recording on the owner's phone, 2026-11-06). The viewer now
  opens on the message alone (`getMessage`), and both sides move on the viewer's transition.
- A `Scaffold` composes its content lambda again whenever it measures it anew, e.g. in every frame of a
  collapsing top app bar: put the body in its own skippable composable, so those frames stop there.
- Opening a screen: nothing composes a loading indicator for the first 150 ms (`DelayedLoadingIndicator`,
  `core:ui`); TDLib's local database usually answers sooner, and the Expressive `LoadingIndicator` builds
  its morphing polygons in the frame that first composes it. A view model created while its screen first
  composes starts its work (loads, collectors) in a dispatched coroutine, after that frame, not in its
  constructor.
- A lazy list that opens away from its start (the conversation at the unread divider or at a found
  message) takes its position in its first measure (`requestScrollToItem` with a negative offset, from a
  layout modifier that knows the viewport, `anchorPlacement` in `feature:chat`), then corrects by the
  entry's measured height: it never composes and measures the newest items first and then scrolls away.

## 5. Rendering

- 120 Hz: no work on the main thread > 4 ms per frame; emoji and avatar initial rendering use
  cached `ImageBitmap`s. The emoji panel's cells draw each emoji from one kept layout of it (the tab's
  `rememberTextMeasurer`, 512 kept), composing no text of their own, so a cell that scrolls in again lays out nothing.
- `WindowInsets` handled by `Scaffold` once; no per-item inset reads.
- Blur (reactions sheet, media viewer) via `RenderEffect` on API 31+, tonal scrim below. A blur is drawn again
  whenever what it covers changes: endless animations under one hold still while it shows (`LocalAnimationsPaused`,
  `core:ui`; the conversation behind the floating message menu), stickers and custom emoji on their frame.
- No path clips on scrolling content: the GPU clips rects and round rects analytically but turns a path into a mask
  on every draw. A group's, channel's, Saved Messages' or community's avatar photo is cut to its polygon once, off the
  main thread, and kept in Coil's memory cache (`AvatarMask`), then drawn unclipped; circles and forums' rounded
  squares keep their clip. Custom emoji are cut out of their text with one rect clip per glyph.
- Shared elements only where something moves: a media bubble sets up its side of the container transform to the media
  viewer (`sharedMediaBounds`) only once its media is asked to open (`SharedMediaShapes.opening`, before the viewer
  composes) and while a viewer's page shows it; the others only publish their corners. A still sticker's emoji leaves
  the composition once its picture has faded in over it.

### Overdraw

- One opaque full-screen layer per screen. The window paints the theme's `background`
  (`MainActivity`); the navigation scaffold is transparent. A page adds a full-screen color only
  where it differs from what is under it, and a layer that another opaque one covers completely is
  transparent or goes.
- Targets in "Debug GPU overdraw": an empty page's background at most 2× (blue), rows and cards one
  layer above it (at most 3×), the system bars' areas no deeper than the page.
- Over a photo wallpaper the photo is the page's layer, painted once per pixel: its dark-theme dim
  and the top bar's wash are a color filter in its draw (`dimAndWashMatrix`), never rects over it, so
  the messages' area and the bar are each the window plus one layer (blue). The bar's blurred copy
  shows only behind the bar and the sheet's two rounded corners; under the opaque sheet it is clipped
  out with rect clips (never a path, which the GPU turns into a mask every frame).
- Nothing hidden keeps room or work: no empty slot laid out, no node at alpha 0, zero size or behind
  an opaque surface that still composes, measures, draws or animates.
- Making a layer transparent keeps the pixels: goldens do not change. If one does, the layer showed
  somewhere; find where instead of re-recording.

## 6. Battery & background

- No polling; TDLib's connection is the only wakeup source. No `AlarmManager`, no periodic
  WorkManager under 15 min. The one periodic job is the weekly storage clean-up (§3), once a week per account, only
  while the phone charges and is idle, so it never wakes the phone on battery.
- Foreground service (`dataSync`) only while an upload/download the user started is in progress;
  stopped within 5 s of completion (`TransfersWorker`: 2 s after the last transfer, plus at most the 1 s between
  notification updates). Automatic downloads never start it (`docs/adr/0009-background-transfers.md`).
- `gms` flavor: FCM push (`registerDevice`, encrypted) so TDLib can sleep. A push wakes the process for at most
  6 s + 3 s (`TdlibPushRepository`: `processPushNotification`, then held-back notifications), not a foreground
  service. The token is registered at login and at each start, which TDLib answers locally when it is unchanged; no
  periodic re-registration (`docs/adr/0004-push-delivery.md`). `foss` has only TDLib's connection, which lives as
  long as Android keeps the process: by default notifications arrive while the app runs. "Keep connected" (Settings →
  Notifications, off by default, `docs/adr/0003-keep-connected-foreground-service.md`) keeps the process and the
  connection alive with a `specialUse` foreground service; the switch says under it that this uses more battery, and
  the background battery budget of §1 is measured with it off. It adds no polling or alarm: the wakeups are TDLib's
  keep-alive and the updates Telegram sends.
- The home screen widget has no timer (`updatePeriodMillis` 0): it is redrawn when the chats change while the process
  runs and a widget is placed, 1 s after the last change (`docs/adr/0017-home-screen-widget.md`).
- Respect Doze and App Standby buckets; never request battery-optimization exemptions by default.
- Power saving (Settings → Power saving, with Battery Saver by default): while on, animated stickers and custom emoji
  hold their first frame (no rendering thread work, no frame draws), videos and GIFs in the media viewer wait for a tap,
  and the call screens' ringing avatar and speaking rings stand still. Each part can be left on.

## 7. Network & data

- Auto-download defaults: Wi-Fi — photos, videos ≤ 10 MB, files ≤ 3 MB, voice and video messages; mobile — photos
  only; roaming — nothing. User-configurable per network in Settings → Data and storage, sizes with the M3 Expressive
  slider. Offline, the last network's rules stay. Stickers, avatars and thumbnails always download (a few KB each).
- Image sizes: request the smallest TDLib `PhotoSize` ≥ the target view size; full size only in
  the viewer.
- Prefetch only the next page of history and visible-row avatars; nothing speculative on mobile.
- Expose TDLib network statistics in Settings → Data & Storage.

## 8. Storage & size

- ABI splits + universal; `androidResources.localeFilters` to shipped locales; vector icons
  only (no PNG except launcher); WebP for any raster asset; no bundled emoji or CJK fonts; one
  variable Google Sans Flex TTF, keep `OFL.txt`. It is stored uncompressed
  (`androidResources.noCompress += "ttf"`, about +1.7 MB per APK), so the platform maps it from the
  APK at the first text instead of inflating all 4 MB into memory. Subsetting would not shrink it:
  the file covers Latin only, and 3.6 MB of it is variation data (`gvar`).
- `android:extractNativeLibs="false"`; uncompressed 16 KB-aligned `.so`.
- The budget of §1 is checked twice, with the same limits. Each pull request runs the `apk size` job of `ci.yml`
  (`:app:checkFossBenchmarkReleaseApkSize`, build-logic's `ApkSize`), so a change that grows the APK past its budget
  fails before it merges. Each release checks the APKs it publishes, both flavors (`release.yml`, "Check APK sizes").
  A change to a budget changes both. The job builds `fossBenchmarkRelease`, which shrinks as `release` does, signed
  with the debug key. A megabyte is 1,048,576 bytes, as GitHub's release pages count it.
  - Measured 2026-10-23 with voice chats and live streams (`foss`, #183): arm64-v8a 48.2 MB, universal 121.1 MB,
    armeabi-v7a 39.4 MB, x86_64 55.4 MB (2026-10-22, before them: 47.8, 120.7, 39.0 and 55.0 MB). `gms` APKs are
    0.2 MB larger than `foss` ones (v0.5.0's release: arm64-v8a 48.1 and 48.2 MB, universal 121.0 and 121.2 MB).
  - What takes the space: the native libraries, stored uncompressed: `libntgcalls.so` (the call engine, 20.5 MB on
    arm64-v8a) and `libtdjni.so` (16.7 MB), both already stripped. Then the code (5.5 MB of dex) and the resources
    (4.4 MB, Google Sans Flex's 4 MB among them).
  - Compressing the native libraries (`useLegacyPackaging = true`) would bring arm64-v8a to about 28 MB. Android
    would then extract them at install, about 37 MB more on the device, and load them more slowly; `docs/TDLIB.md`
    keeps them uncompressed.
  - arm64-v8a had 1.8 MB left and universal 1.9 MB, about 1.6 MB for `gms`. The owner set the universal
    budget from these sizes (2026-10-23); the next addition that grows the native libraries (group video,
    `docs/adr/0019-video-and-group-calls-scope.md`) needs the budget looked at again.
  - TDLib 1.8.70 (2026-11-11, `docs/adr/0038-apk-budget-for-tdlib-1-8-70.md`) grows `libtdjni.so` by 0.36 MB on
    arm64-v8a, 0.33 MB on armeabi-v7a and 0.43 MB on x86_64. `main` before it measured arm64-v8a about 49.5 MB,
    armeabi-v7a 40.7 MB, universal 122.5 MB and x86_64 56.8 MB; with it, 49.9, 41.1, 123.6 and 57.2 MB. The owner
    raised the budgets to 51 MB (arm64-v8a) and 125 MB (universal), leaving about 1.1 and 1.4 MB.
  - The features after v2.3.0 (backup, the Channel feed, the settings redesign, wallpapers, story albums; 2026-11-20,
    `docs/adr/0040-apk-budget-after-v2-3-0.md`) took arm64-v8a to 51.0 MB and universal to 124.7 MB, with no native
    library changed. The owner raised the budgets to 53 MB and 128 MB, leaving about 2 and 3.3 MB.
  - The size audit ADR 0040 asks for (2026-11-20), arm64-v8a `fossBenchmarkRelease`, v2.3.0 against `main` at #792:
    53,185,363 → 53,487,291 bytes (50.72 → 51.01 MB, +0.29 MB). In the APK: dex +255 KB (8.17 → 8.42 MB),
    `resources.arsc` +19 KB, resource files +18 KB; the native libraries, the font and the assets did not change.
    - Dex by package, before compression (R8 inlines and merges across packages, so these are approximate): the app's
      own modules +550 KB (7.06 → 7.61 MB), the libraries −106 KB. `feature:settings` +214 KB (Chats page #739,
      Backup & restore #703, snippets #671), `feature:chat` +141 KB (playback speed #676, round videos #670, links and
      dates in message actions #654 and #667), `core:data` +93 KB (encrypted backups #683, snippets #659, the update
      router #768 and #772), `feature:chats` +43 KB (the Channel feed's menu, reactions, cards and search, #705, #730,
      #685 and #735); `feature:calls` −125 KB, kotlinx.coroutines −52 KB. Of the libraries, Compose UI grew by
      104 KB and Guava by 84 KB (Media3's own use of it); no dependency changed, more of them is reached.
    - Looked at and kept: the 287 drawables (164 KB) are all vectors, the largest 4.5 KB (the wordmark), and
      byte-identical resource files add up to 9 KB, most of them Glance's. 886 of the 912 layouts are Glance's
      (289 KB), which the home-screen widget needs. `raw/open_source_licenses` (54 KB) already stores each license
      text once. The license files under `META-INF` stay.
    - Keep rules: `app/proguard-rules.pro` has a single `-dontwarn`. The broad ones come with the AARs, and JNI needs
      what they keep: TDLib's `-keep class org.drinkless.tdlib.** { *; }` (847 KB of dex, every `TdApi` class with
      its fields and constructors) and the call engine's `org.webrtc.**` (247 KB).
    - Trim: the minified variants leave out the library files only tools read (`META-INF/*.version`,
      `DebugProbesKt.bin`, `kotlin/**.kotlin_builtins`, 114 files), build-logic's `DEBUG_ONLY_METADATA`: −34,365
      bytes on each APK, arm64-v8a 53,487,291 → 53,452,926.
  - Round video messages (2026-10-25, `docs/adr/0032-round-video-messages.md`) add CameraX's `camera-video` (Java
    code only, no native library; CameraX's core was in the app already, for the QR code scanner). R8 keeps a part of
    its classes, expected well under 0.5 MB of dex. Not measured on the build machine, whose disk was too full for two
    release builds; CI's `apk size` job checks it on the pull request.
- TDLib database and files live in `filesDir` (excluded from backup), a 4 GB limit on the
  cache, enforced by the weekly `optimizeStorage` (§3); there is no setting for it yet.

## 9. Measurement & tooling

- `benchmark/` module: `StartupBenchmark`, `ChatListScrollBenchmark`,
  `ConversationScrollBenchmark`, `OpenChatBenchmark`, `SendMessageBenchmark`, `OpenMediaBenchmark`,
  `CatchUpBenchmark`, `BookmarksScrollBenchmark`, `CallsAndStoriesBenchmark`, `ChatListIdleMemoryBenchmark`;
  generates Baseline Profiles.
  It drives `:app`'s `foss` flavor in two build types the Baseline Profile plugin adds:
  `benchmarkRelease` (R8 like `release`) for benchmarks and `nonMinifiedRelease` for profile
  generation. Both are non-debuggable and profileable, and are signed with the debug key, so they
  need no release credentials. They keep the release `applicationId` but not its signature:
  uninstall a release-signed Pingogram from the device first.
- Regenerate the Baseline + Startup Profile with `tools/benchmark/generate-profiles.sh` (or
  `powershell -ExecutionPolicy Bypass -File tools\benchmark\generate-profiles.ps1`; device on API 33+, or rooted on
  API 28+) and commit `app/src/main/generated/baselineProfiles/`; both flavors ship it. Do this before benchmarking:
  the `*BaselineProfile` benchmarks require a profile. The script deletes the committed profiles, so the APK under
  test embeds none, and the plugin's per-test files (`benchmark/build/intermediates/baselineprofiles/`,
  `benchmark/build/outputs/connected_android_test_additional_output/`), which `mergeIntoMain` would otherwise merge
  into the new profile; then it runs `./gradlew :app:generateBaselineProfile` with the arguments given (below), and
  restores the committed profiles if the run fails.
- The journeys (`benchmark/.../Journeys.kt`) need a signed-in app: past the start they scroll the
  chat list and open and scroll Saved Messages. They open no other chat, because opening one marks its
  messages as read, so give Saved Messages a few screens of messages with media. Before each regeneration (§2), start
  from a clean install: `adb uninstall dev.kyralabs.pingogram`, then `./gradlew :app:installFossNonMinifiedRelease`,
  open Pingogram on the device, sign in and wait for the chats (restore a backup if the journeys need its bookmarks). Then pass `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` to the
  generate and benchmark commands; without it Gradle uninstalls the app after the run and the next
  one starts signed out. `nonMinifiedRelease` and `benchmarkRelease` share their signature, so
  installing one over the other keeps the session. The screens are found by test tags, which
  `MainActivity` shows as resource ids (`testTagsAsResourceId`). Whatever state the app was left in, the journeys
  run the same: they show Saved Messages on "All" (its "All" tab and tag chip, `SavedJourneys.kt`) before they
  scroll it or tap its Bookmarks card, fling a conversation only where its messages show (clear of the composer
  floating over the list's end), tap the card below the tabs and tags that grow in above it, and never press back
  while the chat list shows.
- Catch-up and the Bookmarks page (`benchmark/.../ToolJourneys.kt`, 2026-11-16) read no chat either: Catch-up shows
  the unread chats without marking them, and the journeys step through its cards with "Skip" only (never "Mark as
  read", "Reply in place" or "Open chat"); the Bookmarks page opens from Saved Messages' card. The Baseline Profile's
  `catchUpAndBookmarks` takes each where it shows and skips it otherwise. The benchmarks need them: Catch-up's chip
  shows from five unread chats that are not muted, so leave as many unread on the account (the benchmark fails
  otherwise, saying so), and Saved Messages' card shows once a message is bookmarked, so bookmark a few screens of
  messages, some with notes and tags. `CatchUpBenchmark` measures the frames of three cards skipped one after
  another, `BookmarksScrollBenchmark` those of the page's cards flung down and back up; no budget yet: the first run
  on the owner's phone sets one.
- Recent calls and the story rings (`benchmark/.../CallStoryJourneys.kt`, 2026-11-25) place no call and post no
  story. Recent calls opens from the Contacts tab's top bar and is only scrolled, as a tap on a row calls back. The
  story rings above the chat list are scrolled sideways, and the only stories opened are the user's own (from their
  ring, while they have one): no one is told they were seen, and the viewer is closed as soon as it is idle, before
  the first story ends and well before it goes on to another chat's stories. The Baseline Profile's `callsAndStories`
  takes each where it shows and skips it otherwise; on a window with the navigation rail, the journeys find no
  Contacts tab and skip Recent calls. `CallsAndStoriesBenchmark` measures the frames of Recent calls flung down and
  back up and of the rings flung to their end and back. It needs calls on the account, and stories from its
  contacts or channels; each test fails saying so where they are missing. No budget yet: the first run on the
  owner's phone sets one.
- Each run skips the other kind of test (Macrobenchmark's rules check `androidx.benchmark.enabledRules`), and the
  connected test task counts those skips as failures. So run only the kind each command is for:
  `./gradlew :app:generateBaselineProfile -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
  -Pandroid.testInstrumentationRunnerArguments.class=dev.kyralabs.pingogram.benchmark.BaselineProfileGenerator`, and
  `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
  -Pandroid.testInstrumentationRunnerArguments.notClass=dev.kyralabs.pingogram.benchmark.BaselineProfileGenerator`
  (one line each). Results: `benchmark/build/outputs/connected_android_test_additional_output/`.
- `SendMessageBenchmark` sends 10 texts to Saved Messages ("Pingogram benchmark" and a number) per compilation
  mode and deletes each once its bubble shows, through its actions, so Saved Messages is left as it was; the other
  devices of the account see them come and go. To run it alone, in PowerShell from the repository (each `-P` quoted,
  as PowerShell splits arguments at their dots otherwise):

  ```powershell
  .\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true" "-Pandroid.testInstrumentationRunnerArguments.class=dev.kyralabs.pingogram.benchmark.SendMessageBenchmark"
  py tools\benchmark\compare.py benchmark\build\outputs\connected_android_test_additional_output
  ```

  Send back the `*-benchmarkData.json` file under that folder and what `compare.py` printed. The budget is
  `sendMessageSumMs.median` ≤ 50 ms (`benchmark/budgets.json`).
- Baseline (2026-10-22, `benchmarkRelease`, Galaxy S24 Ultra on Android 17 at 120 Hz, clocks not locked; medians of
  10 starts, frame percentiles over 5 runs; "profile" is `BaselineProfileMode.Require` with the shipped profile, "none"
  is `CompilationMode.None`). The phone is far faster than §1's reference device, so meeting a budget here is not
  meeting it there; missing one here is missing it there.

  | Benchmark | Profile | None | Budget (§1) |
  |---|---|---|---|
  | Cold start, `timeToFullDisplay` (`timeToInitialDisplay`) | 487 ms (341 ms) | 668 ms (414 ms) | ≤ 1.2 s |
  | Warm start, `timeToFullDisplay` (`timeToInitialDisplay`) | 135 ms (88 ms) | 174 ms (107 ms) | ≤ 400 ms |
  | Chat list scroll, `frameDurationCpuMs` P50 / P90 / P99 | 6.4 / 10.4 / 14.6 ms | 6.6 / 10.8 / 18.6 ms | P90 ≤ 8 ms |
  | Chat list scroll, `frameOverrunMs` P90 / P99 | 4.3 / 10.7 ms | 6.7 / 13.9 ms | (> 0: late) |
  | Conversation scroll, `frameDurationCpuMs` P50 / P90 / P99 | 5.1 / 9.1 / 15.8 ms | 4.9 / 8.7 / 12.1 ms | P90 ≤ 10 ms |
  | Conversation scroll, `frameOverrunMs` P90 / P99 | 8.7 / 10.7 ms | 3.4 / 9.6 ms | < 1% late |

  Startup has ample room. Both lists drop more than 10% of their frames at 120 Hz: the chat list misses its P90, the
  conversation its jank rate. The profile helps startup (−27% cold) but not scrolling, whose code the JIT compiles
  within a run.
- v0.5.0 (2026-10-23, same phone and method, with the profile regenerated in #175: 72,428 baseline / 71,664 startup
  rules). Late frames: share of frames with `frameOverrunMs` > 0; stutter: > 16 ms late.

  | Benchmark | Profile | None | Change vs 2026-10-22 (profile) |
  |---|---|---|---|
  | Cold start, `timeToFullDisplay` (`timeToInitialDisplay`) | 517 ms (372 ms) | 752 ms (487 ms) | +6% (none +13%) |
  | Warm start, `timeToFullDisplay` (`timeToInitialDisplay`) | 171 ms (100 ms) | 226 ms (130 ms) | +26% (none +30%) |
  | Chat list scroll, `frameDurationCpuMs` P50 / P90 / P99 | 6.0 / 10.0 / 14.4 ms | 6.4 / 9.6 / 13.0 ms | about the same |
  | Chat list scroll, `frameOverrunMs` P90 / P99; late; stutter | 4.7 / 11.0 ms; 65%; 0.36% | 3.9 / 8.7 ms; 76%; 0.34% | about the same |
  | Conversation scroll, `frameDurationCpuMs` P50 / P90 / P99 | 5.9 / 8.5 / 11.7 ms | 4.7 / 7.6 / 11.5 ms | P99 −26% |
  | Conversation scroll, `frameOverrunMs` P90 / P99; late; stutter | 2.5 / 6.0 ms; 59%; 0.05% | 1.5 / 9.0 ms; 37%; 0% | P90 −71% |

  Startup stays far inside its budget but grew past the 10% regression line, warm start most: M4–M6 added work to
  the start (calls, themes, stories, the passcode check); to be traced. The conversation now meets its CPU budget and
  rarely stutters, though most frames still finish slightly late at 120 Hz. The chat list is unchanged and still misses
  its P90 (10.0 against 8 ms): the scroll work of M6 helped the conversation only. Clocks were not locked, so changes
  under ~10% may be thermal.
- Opening and closing media (`docs/audit/performance-review-2026-11.md`): the container transform of a photo, video
  or GIF between its bubble and the media viewer. Four async trace sections: `openMedia` (the tap, handed over by
  `feature:chat`'s route, until the viewer has drawn the page of that media, `MediaViewerTrace`), `overlayEnter` (the
  frames of the transform in: the overlay's transition from the frame it starts until it settled, `OverlayTrace`),
  `closeMedia` (back, a swipe or the back button until the viewer has left the composition) and `overlayExit` (the
  transform out). `OpenMediaBenchmark` opens the newest photo of Saved Messages 10 times per compilation mode, with the
  frame times and the four sections (`openMediaSumMs.median` and the others in `compare.py`'s terms; no budget yet: the
  first run on the owner's phone sets one). Each closing presses back and waits until the way out has ended
  (`OverlayHost`'s `overlay` tag is gone), not only until the viewer left the screen readers' view, which it does in
  the way out's first frame: a trace keeps only the sections that ended before the iteration did. It needs a photo
  without a caption among Saved Messages' newest messages, sent by yourself. To run it alone, as `SendMessageBenchmark`
  above, with
  `-Pandroid.testInstrumentationRunnerArguments.class=dev.kyralabs.pingogram.benchmark.OpenMediaBenchmark`.
  `tools/perfetto/media-viewer.sh` (`media-viewer.ps1` in PowerShell) records one Perfetto trace
  (`media-viewer.pbtxt`: the frames of the main thread and the RenderThread, SurfaceFlinger's frame timeline, the
  window relayouts and binder calls) while it opens the photo on screen and presses back 10 times, and
  `media-viewer.sql` summarizes it: each section's frames, the longest and the 90th percentile on both threads, the
  frames SurfaceFlinger presented late, the main thread's longest slices inside the sections (relayouts, semantics,
  Compose's phases), the RenderThread's (time waiting for the GPU is the cost of offscreen layers and blur effects) and
  the CPU time per thread. On the JVM, `:app`'s `MediaViewerTransitionTest` steps the opening and the closing frame by
  frame above a conversation and prints what composed in each with the main thread's CPU time per frame; with
  `-Ppingogram.jfr` its printed times place the recording's samples, and the system property
  `pingogram.mediaTransitionRepeats` (set through a Gradle init script's `systemProperty` on the test task) repeats
  the opening for more of them.
  - **Before and after the review's fixes, on the JVM** (2026-11-07, `MediaViewerTransitionTest`, six openings and
    closings in one JVM, the mean of repeats 2–6 over two runs each, the shared build machine). Main thread CPU per
    frame:

    | Frame | M1 (#333) | M2 (#336) |
    |---|---|---|
    | Opening, frame 0 (the loading state, the view model) | 24.3 ms | 23.6 ms |
    | Opening, frame 1 (the viewer's content) | 22.5 ms | 16.4 ms |
    | Opening settled (the bottom bar since M2) | 4.5 ms | 13.4 ms |
    | Closing, the frame the overlay leaves | 15.0 ms | 10.7 ms |
    | Closing, the next frame (the entry destroyed since M2) | 4.9 ms | 8.9 ms |

    M2 moves work out of the frames that start and end the transform into frames where nothing moves. M1 changed
    drawing only (no blur layers, no placeholder under a loaded image, one fade, the bubble's side scaled), which
    Robolectric does not do: the frames of the transform composed nothing before or after it. Neither shows ExoPlayer,
    whose build (10–30 ms) and release left those frames with M2.
  - **Before and after on the owner's phone** (2026-11-07, Galaxy S24 Ultra SM-S928B, Android 17, 120 Hz,
    `benchmarkRelease`; before: `b4abfa18^`, the review's build; after: `main` with M1, M2 and M6). Microsoft Phone
    Link's screen mirroring ran as an accessibility service in both, so Compose worked out its semantics in every
    frame (about 16 ms over each closing), and the frame timeline marked nearly every frame late in both ("Buffer
    Stuffing"): compare the two, not each against the display's 8.3 ms. `OpenMediaBenchmark`, 10 iterations per
    compilation mode, the profile's first:

    | Metric | Before | After |
    |---|---|---|
    | `frameDurationCpuMs` P50 / P90 / P95 / P99 | 8.6 / 36.3 / 66.7 / 231.3 ms | 7.3 / 25.7 / 35.7 / 51.1 ms |
    | `frameOverrunMs` P50 / P90 / P95 / P99 | 4.0 / 47.2 / 98.6 / 273.8 ms | 2.3 / 30.7 / 43.3 / 113.1 ms |
    | Frames drawn per opening and closing (median) | 108 | 133.5 |
    | `openMediaSumMs` (median) | 43.0 ms | 40.6 ms |
    | Without the profile: `frameDurationCpuMs` P90 / P99 | 34.9 / 207.6 ms | 23.9 / 99.9 ms |

    The journey presses back as soon as the page shows, before the way in settles, so `overlayEnter` never ends there
    and has no count; `closeMedia` (about 816 ms) is the exit spring's length, not work. `media-viewer.ps1`, 10
    openings and closings, each waiting for the way in to settle; the main thread's frames per transform, from
    `summary.txt`:

    | Transform | Before | After |
    |---|---|---|
    | In: frames drawn (of about 97 in 813 ms) | 11–76 | 81–92 |
    | In: longest frame | 47–284 ms | 14–110 ms (8 of 10 under 33 ms) |
    | In: 90th percentile | 4.9–81.2 ms | 4.9–9.6 ms |
    | Out: frames drawn | 63–81 | 76–94 |
    | Out: longest frame | 48–207 ms | 16–158 ms (9 of 10 under 26 ms) |

    One opening before took 2.0 s instead of 0.8 s. The CPU of `Dispatchers.Default` (Coil's decodes, the view
    models) during the ways in fell from 12.8 s to 4.3 s over the 10; what remains is mostly the full photo's decode,
    which M5 caps. The RenderThread still spends about 5 ms on each frame of the way out ("Drawing", "flush
    commands", "dequeueBuffer"). `media-viewer.sql` counted no RenderThread draws on this phone, which names them
    with their vsync id ("DrawFrames 162342341"); it matches those now.
- Measure first (the review of 2026-11-23, v2.6.0): three costs the review suspected but no one has measured are
  traced, to be fixed only where the owner's numbers show them slow. Sync trace sections, on the thread that does the
  work: `passcodeHash` (one PBKDF2-HMAC-SHA512 hash of the passcode lock at 210,000 rounds, `PasscodeTrace`, when a
  passcode is set, changed or entered), `speechPcm` (one decoded chunk of a voice or video message mixed to one
  channel and resampled to 16 kHz for the speech recognizer, `TranscriptionTrace`) and `qrDecode` (one camera frame,
  or a picked image, searched for a QR code as it is and inverted, `ScannerTrace`). Each costs a check of a flag
  while no trace records the app, and records only on Android 10 and later.
  - **To measure on the phone**: install `fossBenchmarkRelease` (profileable) and sign in, open Pingogram, then run
    `tools/perfetto/measure-first.sh` (`powershell -ExecutionPolicy Bypass -File tools\perfetto\measure-first.ps1`).
    It records one Perfetto trace for 120 s (`measure-first.pbtxt`; `-Seconds` or a third argument changes it) and
    prints what to do meanwhile: with Auto-lock "Immediately", leave the app and enter the passcode 3 times;
    transcribe a voice message of 30 s or more; scan a QR code from Contacts' Scan with the camera for 10 s, then one
    from an image. Send back `device.txt` and `summary.txt` (`measure-first.sql`: each section's count, median, 90th
    percentile, longest and total, the threads it ran on, and how long it waited for a core).
  - **What would be slow**: a `passcodeHash` over about 500 ms (the lock screen waits for it; the hash runs on a
    background dispatcher, so it stalls no frame), a voice message whose `speechPcm` total is more than a tenth of its
    length (the recognizer waits for it), and a `qrDecode` median over 200 ms (the scanner reads at most five frames
    a second, and skips frames while one decodes). Any of them on the main thread is a bug whatever its time.
- Checking a run: `tools/benchmark/compare.py <results>` reads the run's `*-benchmarkData.json` files and fails on a
  budget of §1 missed (`benchmark/budgets.json`, which mirrors the table) or a time or a memory figure more than 10%
  above the last baseline of the device (`benchmark/baselines/`, one file per device, such as `galaxy-s24-ultra.json`; its `commit`
  names the release it was recorded on). Times are the start times' medians, the frame time percentiles and the trace
  sections' medians; memory figures are `ChatListIdleMemoryBenchmark`'s medians in kB (`memoryRssAnonLastKb`,
  `memoryRssFileLastKb`, `memoryHeapSizeLastKb`: `MemoryUsageMetric` reads RSS, not PSS, so the PSS budget is read
  with `adb shell dumpsys meminfo dev.kyralabs.pingogram` on the idle chat list); a time the baseline holds as 0 (a section the run did not record) is not compared; the share of
  frames more than 16 ms late (`frameOverrunMs.stutterPercent`) is checked against its budget only, and the share of
  late frames (`latePercent`, any overrun at all) is printed for each scroll benchmark but has no budget (ADR 0042). `--write-baseline` records a run as the new baseline. `docs/RELEASE.md` runs it before each release, on the owner's phone, as there is no device in CI.
- Scroll benchmarks start the app cold in their setup block (`coldStart`: `killProcess()`, then the launcher
  activity), never with `StartupMode.COLD`, which kills the process again after setup, so the measured block would
  find the app gone.
- Startup benchmarks wait for the chat list and an idle UI after `startActivityAndWait()`, so the trace holds the
  frame after `reportFullyDrawn`, which time to full display ends with. On a slow phone that frame was still drawing
  when the trace stopped, and the iteration failed ("No Choreographer#doFrame ends after reportFullyDrawn").
- Two costs of every Macrobenchmark cold start that users rarely pay: Macrobenchmark drops the app's shader cache
  before each one, so the first frames compile their shaders again, and it force-stops the app, which Android 15+
  then sends `BOOT_COMPLETED` as it leaves the stopped state (WorkManager's `RescheduleReceiver` takes it on the main
  thread, before the activity starts). On a fast phone both take a few milliseconds; on a slow one, hundreds (below).
- Judging smoothness by hand: a sideloaded APK runs JIT-compiled at first. The APK carries the
  libraries' baseline profiles (and the app's, once generated) for `profileinstaller`, but the
  platform compiles them only in its background dexopt, when the phone is idle and charging. Before
  judging a fresh install, compile it the way Play's installs would be: install with its `.dm` file
  (`adb install-multiple <apk> <dm>`, both from `app/build/outputs/apk/<flavor>/<type>/`, the `.dm` under
  `baselineProfiles/`), or open it once for a few seconds (`profileinstaller` writes the profile then)
  and run `adb shell cmd package compile -f -m speed-profile dev.kyralabs.pingogram` (the
  `.debug` suffix for debug builds, which are never representative: debuggable, no R8).
- Recomposition tests on the JVM: `RecompositionCounter` (`feature:chats` tests) counts the composables
  that compose again, through the Compose runtime's composition observer, while a Robolectric test
  steps the test clock frame by frame. `ChatListRecompositionTest` keeps §4 true for the chat list
  (scrolling, dragging it under its top bar, coming back to it), `ChatRecompositionTest` for the conversation
  (scrolling, a new message); copy the pattern for other screens. `feature:chat`'s copy of the counter
  starts before the screen first composes and leaves out calls that skip (they enter their restart group
  too), so it counts exactly the composables that run again. To see
  where a frame's time goes without a device, record the test JVM with JFR: `-Ppingogram.jfr=<directory>` starts a
  recording in every unit test JVM (1 ms method sampling, stack depth 2048, as Compose stacks are deep;
  `AndroidRoborazziConventionPlugin`), read with `jfr print --events jdk.ExecutionSample`. Only relative costs carry
  over to a phone, and Robolectric neither draws nor runs Compose's prefetch between frames.
- Scroll cost on the JVM: `ChatListScrollCostTest` (`feature:chats`) and `ChatScrollCostTest` (`feature:chat`) scroll
  the list by 40 px a frame and print the main thread's CPU time per frame (the scroll, which measures and composes
  what comes into view, and the frame), as P50 / P90 / P99, with the screen's own list state and Compose's default.
  They skip unless asked for: `./gradlew :feature:chats:testDebugUnitTest -Ppingogram.scrollCost --tests
  '*ScrollCostTest*own*' --rerun`, one test per run (the JIT carries over), on a quiet machine, comparing runs of the
  same machine only. First numbers (2026-10-22, the shared 4-core build machine, default state): chat list P50 1.2 ms,
  P90 10.2 ms, P99 13.2 ms (a frame that brings in a row: its composition 14%, its nodes 11%, its measuring 20%, mostly
  the layout of its five texts, of the scroll's time); conversation P50 1.1 ms, P90 4.8 ms, P99 8.7 ms.
- Forum rows naming their topics (2026-11-15, `docs/DESIGN_SYSTEM.md` §6 "Chat list row"): `ChatListScrollCostTest`
  with every tenth row a forum that names three topics (its preview data's "Book club"), against the same rows without
  the topic line, three alternating pairs on the shared 4-core machine: without P50 0.96–1.00 ms, P90 10.0–10.2 ms,
  P99 15.3–16.4 ms, mean 2.74–2.85 ms; with P50 1.04–1.13 ms, P90 9.2–9.8 ms, P99 13.7–14.6 ms, mean 2.62–2.85 ms.
  A forum's row composes and measures more (one layout of a tab, the other topics and the pill, each measured once),
  but its third line leaves fewer rows on screen, so a scroll frame brings in fewer of them.
- Story rings (2026-10-23, the same machine under other agents' builds, so only runs side by side compare):
  `ChatListScrollCostTest` with the rings row as the list's first item and without it, interleaved three times each:
  without P50 1.30 ms, P90 9.9–10.0 ms, P99 13.3–17.0 ms; with P50 1.38–1.46 ms, P90 10.0–11.1 ms, P99 12.5–15.9 ms.
  The rings scroll away in the first frames, after which the row costs nothing, as `ChatListRecompositionTest` checks
  (a ring turning seen recomposes the row alone; scrolling past it recomposes nothing). Before the change, on a quieter
  machine: P50 1.2 ms, P90 9.3–9.5 ms, P99 11.6–13.9 ms.
- Chat list scrolling (2026-10-23, after the v0.5.0 device run missed the P90 budget, 10.0 against 8 ms):
  - **What the JVM shows.** `ChatListScrollCostTest` now gives four rows in five a profile photo, loaded by Coil through
    a fake fetcher and kept in its memory cache, as on a phone. It also prints the mean, over 8 runs instead of 4. A JFR
    profile of its frames: composing a row that comes into view takes about a fifth of the scroll's time, creating its
    nodes a seventh, and the layout of its texts (name, preview, time, initials) about a quarter, all spread over many
    small parts. Removing whole parts in trials (the `SwipeToDismissBox`, Material's clickable `ListItem` for its
    plain one, the initials) saved too little each to see within the machine's noise (±1 ms at P90), and 7–15% all
    together.
  - **What changed.** The avatar lays out its initials only while they show (`lazyInitials`), so a photo from Coil's
    memory cache saves a text layout. It also builds the selection check only once the avatar is first selected. A
    row's swipe feedback starts its watch only while the row is swiped. Interleaved four times each: mean
    3.66 → 3.44 ms (−6%), P90 12.8 → 11.6 ms (−9%), P99 19.2 → 17.5 ms (−9%).
  - **What only a phone shows.** A row's resting container is now transparent. It was the sheet's own `surface`
    drawn again under every row, a full extra layer of pixels in every scroll frame. A row being swiped draws its
    container at once (`draggedContainer`). The JVM draws nothing, so this needs the device benchmark. On the
    phone, frames that bring no new row already cost 6.0 ms at P50, against about 1.5 ms of composing and measuring on
    the JVM: most of a phone's frame is drawing and the RenderThread, which only a Perfetto trace of the benchmark can
    break down.
- Share button beside channel posts (2026-10-24, `ChatScrollCostTest`'s channel pair: 2,000 posts, each with the button
  or without it, interleaved, the shared 4-core machine under other sessions' builds): the button adds about a tenth to
  a scroll frame's P90 (5.5–6.2 ms against 4.8–5.6 ms over three pairs), little to P50 and P99, in channels only. A
  clickable circle without Material's button and a button without its press morph cost about as much, within the
  machine's noise, so the button stays Material's: the cost is a second control per post. Every button shares one
  painter and its colors (`ShareButtonStyle`). The conversation benchmark scrolls Saved Messages, which has none; a
  channel's scroll on the owner's phone tells whether it holds there.
- Mention chips (2026-11-07, the experimental look, `docs/DESIGN_SYSTEM.md` §13.6): `ChatScrollCostTest`'s group of
  2,000 messages in the experimental look, every eleventh mentioning the user, with its mention as a chip and without,
  three alternating pairs on the shared 4-core machine: without P50 1.22–1.42 ms, P90 5.4–6.0 ms, P99 11.1–12.1 ms; with
  P50 1.19–1.31 ms, P90 5.1–5.5 ms, P99 10.1–10.7 ms, the same within the machine's noise. A chip is a block drawn
  behind the text from its layout, added over the cached text of the bubbles that mention the user only. Measuring it
  found a crash on `main` (vivid bubbles with their name above them, text drawn with blocks), fixed by `WithNameAbove`
  and kept by `VividBubbleScrollTest`.
- Voice chat comments (2026-11-10, `docs/DESIGN_SYSTEM.md` §6 "Voice chat comments"): `feature:calls`'
  `GroupCallFrameCostTest` steps the voice chat's screen frame by frame, the timer and the speaker changing every frame
  (a stress case: on a phone they change once a second), and prints the main thread's CPU time per frame and how many
  of Pingogram's composables recompose per frame, with `-Ppingogram.scrollCost`. Its checks always run: a comment
  recomposes the comments pane alone (`GroupCallCommentsViewModel` keeps them out of `GroupCallUiState`, and the screen
  reads only the pane's derived visibility), a comment while the pane is hidden recomposes nothing, and a new comment
  shows at the bottom. Three runs each on the shared 4-core machine:
  - **Before** (`main` at fe6cac5, the same scenario): P50 12.7–13.0 ms, P90 15.4–16.6 ms, P99 20.3–21.6 ms, 14.0
    composables recomposed per frame.
  - **After, comments hidden or unavailable**: P50 12.4–13.3 ms, P90 15.6–16.9 ms, P99 18.6–21.8 ms, 14.0 per frame:
    unchanged.
  - **After, comments shown, 10 a second**: P50 8.5–9.0 ms, P90 18.0–18.7 ms, P99 26.2–28.4 ms, 16.7 per frame. The
    participants' tiles cost less than the rows at most frames; a frame with a new comment composes and places one row
    and moves the list, which the P90 and P99 show. The rows already on screen skip; the pane's field and floating
    reactions take only what they show, so they skip too.
  - **On the owner's phone**: install `fossBenchmarkRelease` (§9 above), join a busy voice chat with comments on (or
    have a few people comment and react from Telegram's apps), keep the voice chat's screen in front with the comments
    showing, and run `tools/perfetto/video-call.sh` (it records the main thread's and the RenderThread's frames, `gfx` and
    `view`, for 5 minutes); then once more with the comments hidden. Send back both folders' `device.txt` and
    `video-call.perfetto-trace`. The frames' durations against the display's period tell whether the pane holds.
- Live stories (2026-11-10, board 12): watching one adds a way into the voice chat screen and a layout of its own for
  it, and changes nothing the voice chat's frames do. `GroupCallFrameCostTest` on the branch, twice on the shared
  4-core machine, against the numbers above: comments hidden P50 12.3–12.6 ms, P90 15.0–15.4 ms, P99 18.8–20.2 ms,
  14.0 composables recomposed per frame; comments shown P50 7.4–8.4 ms, P90 15.0–17.1 ms, P99 20.6–21.6 ms, 16.7 per
  frame: the same within the machine's noise. On the owner's phone: install `fossBenchmarkRelease` (§9), open a live
  story with "Watch live" and keep its screen in front with the comments showing, run
  `tools/perfetto/video-call.sh` (5 minutes), and send back its folder's `device.txt` and `video-call.perfetto-trace`.
- Folder slide (2026-11-07, owner decision: chat folders slide like a pager, the neighbouring folders' first page loaded
  ahead): `ChatListScrollCostTest` with the list's own state, three runs each on the shared 4-core machine, before
  P50 0.92–1.03 ms, P90 8.7–9.6 ms, P99 12.3–12.4 ms, mean 2.40–2.62 ms; after P50 0.90–1.02 ms, P90 8.5–9.7 ms, P99
  11.5–15.2 ms, mean 2.33–2.71 ms: the same within the machine's noise, as scrolling composes nothing more (the
  neighbour composes only while a swipe or a slide shows it). `ChatListRecompositionTest` checks that a swipe recomposes
  no row of the shown list. The neighbours' lists are mapped only while "Swipe to change folders" is on, each by a mapper
  of its own, on the view model's dispatcher; their cost while the chats change is not measured on a phone yet.
- Landing a folder switch (2026-11-07, the owner saw a stutter as a swipe landed, and more under repeated swipes):
  the shown list and the one beside it swapped their contents as the swipe landed, so both lists composed every
  visible row again in that frame, and the rows' animations were turned off and on again around it, recomposing them
  once more. Each folder's list is now movable content with its own list state (`ChatListScreen`'s `FolderLists`,
  `FolderListStates`): landing a swipe or tapping a tab moves the two compositions between the shown place and the
  companion's, recomposing no row (`ChatListRecompositionTest`), and a folder's list keeps its scroll position by
  itself.
- The floating folder bar (2026-11-14). Since v2.1.0, `ChatListScrollBenchmark.scrollBaselineProfile` on the owner's
  Galaxy S24 Ultra (two runs, accessibility off):
  - `frameDurationCpuMs`: P90 8.8 → 10.7 / 11.9 ms, P95 10.7 → 12.6 / 15.9 ms, P50 6.4 → 5.5–6.0 ms;
  - frames over 32 ms: 0 → 5.

  Two changes touch the screen: the folder tabs floating in a bar over the list (#496) and the navigation bar's labels
  (#494).
  - **The JVM.** `ChatListScrollCostTest`, four interleaved runs at each of v2.1.0, the commit before #496, #496 and
    #494, shows the same main thread: P90 9.3–10.3, 9.4–11.0, 9.3–11.1 and 9.2–10.2 ms, mean 2.5–2.9 ms in each. So
    neither the bar's layout (its height reaches the lists in the layout phase, `FolderBarSpace`) nor its composition
    reaches the scroll frames, and `ChatListRecompositionTest` checks that scrolling under it recomposes neither the bar
    nor the rows on screen. The navigation bar is measured once, not per frame.
  - **The cost is drawing.** The bar floats over the moving list inside the sheet's rounded clip, with a level-3 shadow
    and a clip of its own. Its chips faded at the edges through an offscreen layer with a `DstOut` mask, and the list
    now draws under the bar too. The RenderThread draws all of it in every frame.
  - **A first fix that did not help** (#501): the fade paints the bar's own opaque color over the chips' edges instead
    of masking them in an offscreen layer (`FolderBar.kt`'s `fadeIntoBar`). P90 stayed at 11.4 ms on `main`
    (10a389e9), against 7.6 ms for v2.1.0 on the same phone the same day: a regression of about 50%.
  - **The owner's traces** (`chat-list-scroll.sql`, v2.1.0 → `main`, per frame):
    - RenderThread P50 3.4 → 5.4 ms, P90 6.4 → 9.5 ms;
    - `dequeueBuffer` 0.72 → 1.55 ms, "flush commands" 1.5 → 2.0 ms;
    - main thread P90 1.5 → 4.6 ms, mostly `postAndWait`, waiting for the RenderThread;
    - "Texture upload(130) 1390x195" on 622 of 800 RenderThread frames on `main`, none in v2.1.0: about 397 × 56 dp,
      the floating folder bar;
    - "Texture upload 1440x2513" (1440x2309 in v2.1.0): the whole list area, grown by the old tab row.
  - **The cause, from Compose's code.** `ContentSheet` clipped the sheet to a rect rounded at the top only. Compose
    hands Android a round rect only where all four corners match (`RoundRect.isSimple`), and anything else as a path
    (`GraphicsLayer.setPathOutline`). Android 13+ clips the layer to that path, and the GPU cannot clip to a path
    analytically. So every draw across the sheet's rounded top corners made Skia rasterize a clip mask on the CPU and
    upload it, in every frame:
    - the folder bar, whose pill and level-3 shadow sit across both corners `spacing.small` below the sheet's top;
    - the list's layer, whose mask is the whole list area.

    The floating navigation bar's shadow is outside that clip, which is why v2.1.0 had no bar-sized upload.
  - **The fix.** The sheet now clips to `SheetShape`: a round rect with `extraLarge` all round that reaches its radius
    below the sheet, so the bottom corners fall outside it. Android gets a round rect, which the GPU clips without a
    mask. The pixels are the same; the goldens did not change.
  - **To check on the phone**:
    - Run `ChatListScrollBenchmark` alone (`-Pandroid.testInstrumentationRunnerArguments.class=dev.kyralabs.pingogram.benchmark.ChatListScrollBenchmark`).
    - Then summarize one of its iterations' traces with `tools/perfetto/chat-list-scroll.sql` (`summarize.py`). It
      gives the main thread's and the RenderThread's frames side by side, the RenderThread's slices, and what each late
      frame spent its longest slice on. It also lists the texture uploads by size with the slices they ran under, and
      what each main thread frame was for (input, animation, recompose, or an invalidated draw).
  - **After #502** (the owner's trace of 2026-11-15): the main thread's P90 was 2.2 ms against 1.5 ms in v2.1.0, and
    901 of its 1194 frames read as "recompose" frames. That count was the summary's mistake, not recompositions:
    - Compose wraps its frame clock's dispatch in `Recomposer:animation` and the recomposer's per-frame loop in
      `Recomposer:recompose`, whether anything was invalid or not (Compose runtime 1.12.1). A fling's animation runs
      through that clock, so every frame of a fling carried both. The summary counted any `Recomposer:*` slice as a
      recomposition.
    - It now counts a frame as "recompose" only where a composition applied changes (`Compose:applyChanges`), calls the
      rest "animation (a fling, no recomposition)", and lists Compose's slices per frame.
    - On the JVM, `ChatListRecompositionTest` flings the list with touch input, through the header's nested scroll as
      the benchmark's flings do: 4 of 204 frames recompose (Material's `Scaffold` content and the header's
      `TwoRowsTopAppBar` as the header collapses), nothing of Pingogram's in any of them.
    - What is left of the 0.7 ms needs the next trace, read with the corrected summary: frames that apply changes would
      point at the app's shell or at pictures loading into rows, which the JVM test leaves out; otherwise the main
      thread waits for the RenderThread (`postAndWait`), as before #502.
- The cache window's one long frame (2026-11-15). The owner's trace of `main` after #502 and #520
  (`ChatListScrollBenchmark`, profile) met the budget: P90 7.1 ms, against 8.6 ms in the 2.2.0 baseline.
  - **The main thread's remaining time** is waiting for the RenderThread (`postAndWait`, 0.67 ms a frame): drawing,
    not app code. Only 30 of its 789 frames applied changes.
  - **One frame of 39 ms** held a 36.1 ms `compose:lazy:cache_window:keepAroundItems`. In Compose 1.12.1 that section
    calls `compose(index)` in the measure pass for every row of the cache window (half a screen ahead, one behind)
    outside the viewport, so rows no prefetch composed yet are composed there, all in that frame.
  - **The JVM does not reproduce it.** `ChatListScrollCostTest`'s "first scroll frames with each cache window"
    composes the list anew and scrolls it, seven runs per state after one warming the JIT. Longest frame and all 20
    frames, medians:

    | List state | Longest frame | All 20 frames |
    |---|---|---|
    | Compose's default | 8.4 ms | 40.8 ms |
    | ahead 0.5, behind 1 (Pingogram's) | 7.8 ms | 35.8 ms |
    | ahead 0.25, behind 1 | 7.4 ms | 35.6 ms |
    | ahead 0.5, behind 0.5 | 7.1 ms | 33.8 ms |
    | ahead 0, behind 1 | 6.9 ms | 32.8 ms |

    The cache window makes no first scroll frame longer there. The spike needs what only a phone pays the first time
    (code not compiled yet, first text layouts, shaders), so the window stays as it is until a trace says which rows
    it composed. `chat-list-scroll.sql` now lists every `keepAroundItems` over 2 ms with when it ran and what ran
    inside it.
- `androidx.tracing` sections: `openChat`, `applyUpdates`, `buildBubble`, `decodeSticker`.
- Perfetto config committed under `tools/perfetto/`; `docs/adr/` records every budget change.
- Video calls (`docs/adr/0019-video-and-group-calls-scope.md`): every remote frame crosses JNI as a new `byte[]`, about
  1.4 MB at 720p, 30 a second, and is copied once more into a direct buffer for WebRTC's renderer. The frame path has
  trace sections and counters (`feature:calls`' `CallTrace`): `callFrames` (a batch handed over from NTgCalls' thread
  to the sinks), `callFrameCopy` (one frame copied and queued for its `EglRenderer`), and the frame rates `Remote.fps`
  and `Self.fps`, set once a second; the renderers draw on their own threads (`RemoteEglRenderer`,
  `SelfEglRenderer`) and are set up, given their surface and taken down on `CallVideoSetup` (`RendererSetup`): making
  the EGL context and waiting for a renderer's thread never block the main thread, where the call screen composes. `tools/perfetto/video-call.pbtxt` records them for 5 minutes with every thread's CPU time,
  Pingogram's memory (RSS, anonymous, swap) once a second and the battery's current; `tools/perfetto/video-call.sh`
  runs it and adds `dumpsys meminfo` (PSS) once a minute; `tools/perfetto/video-call.sql` summarizes the trace with
  Perfetto's trace processor or in https://ui.perfetto.dev, which reads the file in the browser. The trace holds
  thread names, timings, memory and counters: no message, name, number or picture. On the owner's phone:
  1. `./gradlew :app:installFossBenchmarkRelease` (R8 as `release`, profileable; uninstall a release-signed Pingogram
     first, as above), open it and sign in.
  2. Call someone on another phone with Telegram's app, both on Wi-Fi, both cameras on, until each sees the other's
     720p video. Keep Pingogram's call screen in front with the screen on, the phone unplugged.
  3. From the repository, run `tools/perfetto/video-call.sh` and keep the call going until it prints "Done" (5 minutes).
  4. Send back its folder: `device.txt`, `meminfo.txt` and `video-call.perfetto-trace`.

  A voice chat with video (`docs/adr/0024-video-in-voice-chats.md`) is recorded the same way: join a group's voice
  chat where four others send their cameras (from Telegram's apps), turn Pingogram's camera on, keep the voice chat's
  screen in front with the four videos and the self-view showing, and run step 3. Each video's frames go to their own
  renderer (`EglRenderer` threads named "Participant …"), so the trace shows what each costs; the four tell whether
  the cap of four holds on that phone.

  **Conference calls** (`docs/adr/0025-conference-calls.md`) wait for TDLib on WebRTC's threads for every frame they send
  (`callEncrypt`) and receive (`callDecrypt`), at most 100 ms each before the frame is dropped.
  `tools/perfetto/conference-call.pbtxt` records those sections for 3 minutes, with every thread's CPU time (TDLib's
  included), Pingogram's memory once a second and the battery's current. `conference-call.sh` (or `conference-call.ps1`
  in PowerShell) runs it, and `conference-call.sql` summarizes the trace into `summary.txt`:
  - each section's count per second;
  - its P50, P90, P99 and longest trip;
  - how many trips took 20 ms (an audio frame) or more, and how many reached 100 ms and were dropped.

  On the owner's phone:
  1. Install `fossBenchmarkRelease` and sign in, as for video calls (step 1 above).
  2. In Telegram's app on another phone, start a group call and copy its link (`t.me/call/…`). Send it to the
     account on this phone, have two more people join, and turn one camera on.
  3. On this phone, tap the link, join, unmute, and speak now and then. Keep the call's screen in front with the
     screen on, the phone unplugged.
  4. From the repository, run `tools/perfetto/conference-call.sh`, or in PowerShell
     `powershell -ExecutionPolicy Bypass -File tools\perfetto\conference-call.ps1`. Keep the call going until it
     prints "Done" (3 minutes).
  5. Send back `device.txt` and `summary.txt` from its folder. Without Perfetto's Python package, run the line the
     script prints first; the trace itself (`conference-call.perfetto-trace`) stays on the computer.

  The P90 against an audio frame's 20 ms and the dropped trips tell whether the round trip holds on that phone.

  §1 has no budget for calls yet: the first results go here and set one.
- LeakCanary in debug builds (owner approval 2026-10-27: about 1 MB, no network): `:app`'s `debugImplementation`, so
  `release`, `benchmarkRelease` and `nonMinifiedRelease` never have it, which `check<Variant>NoDebugTools` (build-logic's
  `DebugOnly`, run by `check` and before each of those variants builds) enforces. It watches destroyed activities,
  fragments, view models and services, dumps the heap once some stay reachable after they should be gone (5 while the
  app is visible, LeakCanary's default), and shows what holds them in its own "Leaks" launcher icon and notification. To read it on a phone: install `fossDebug`, use the app (open and close
  chats, the media viewer, calls, Settings pages), then open "Leaks"; each leak's trace can be shared from there as text.
  `adb logcat -s LeakCanary` prints the same. A leak would fail the nightly, which does not exist yet (below).
- Nightly workflow (to add in M2) runs benchmarks on an emulator for trend only (the plugin skips
  them on emulators until `benchmark/build.gradle.kts` sets
  `baselineProfile { skipBenchmarksOnEmulator = false }`); real-device
  numbers come from the owner's phone with `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest`.

## 10. Checklist for PRs touching UI or data

- [ ] No new unstable classes in UI state (compiler report)
- [ ] `LazyColumn` items have `key` + `contentType`; no effects inside items
- [ ] Animations read state in layout/draw phase; a recomposition test for scrolling screens
- [ ] Accessibility-only strings built in `semantics { }`, not in composition
- [ ] Images request explicit sizes; stickers respect the concurrency cap
- [ ] TDLib calls are suspendable, batched, and cancel when the screen leaves
- [ ] Macrobenchmark for the affected screen unchanged or improved
- [ ] No new full-screen opaque layer under another one (§5, "Overdraw")
