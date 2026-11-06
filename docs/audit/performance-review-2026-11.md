# Performance review, November 2026: the media viewer's container transform, and the rest

Date: 2026-11-06 · Scope: why opening and closing a photo or video from a conversation feels laggy on the owner's phone
(`docs/PERFORMANCE.md` §4, 2026-10-26 and 2026-11-06 notes), then the conversation and chat list, the main thread,
leaks, TDLib calls that outlive their screens, the hard rules of `CLAUDE.md` and correctness. Findings are ranked by
impact. Each names the code (`file:line` at `b38059d`), the evidence (a test's output, a JFR recording or the code
itself: where it says "reasoning", the device trace of §2 is what confirms it), the fix and the gain to expect. Nothing
here changes behaviour: this pull request adds only the measuring tools (§2); fixes come as separate pull requests once
the owner has read this.

## Summary

| # | Finding | Impact | Where |
|---|---|---|---|
| M1 | Every frame of the transform renders two blur effects and three to four full-window offscreen layers: the blurred minithumbnail under the viewer's photo and the one under the bubble's (both always drawn, loaded image or not, both resized every frame), the overlay's fade, the shared element's crossfade and the full photo's fade-in. The main thread is idle in those frames; the RenderThread and the GPU are not. | High: the transform's dropped frames at 120 Hz | `MediaPage.kt:203`, `MediaImages.kt:108`, `MessagePhoto.kt:113`, `MediaImage.kt:136`, `OverlayNavigator.kt:228`, `SharedMedia.kt:292-295` |
| M2 | The opening's first frames do everything on the main thread at once: the view model, the whole viewer (Scaffold, two bars, a floating toolbar with tooltips, three image requests), the bubble's side of the transform, and for a video ExoPlayer's construction; the closing's last frame releases ExoPlayer, which blocks until its playback thread released the codec. | High for videos, medium for photos: a hitch before the transform starts and at its end | `MediaViewerScreen.kt`, `ExoVideoPlayer.kt:135`, `ExoVideoPlayer.kt:127-129`, `MediaViewerViewModel.kt:295` |
| M3 | Each open and close changes window attributes (system bar appearance and behaviour, `FLAG_KEEP_SCREEN_ON` when a video starts, `FLAG_SECURE` in secret chats), each a window relayout: a binder round trip to the window manager on the main thread in the frames of the transform. | Medium: 1–5 ms on the main thread in the first and last frames | `ImmersiveSystemBars.kt:23-32`, `MediaPage.kt:106`, `MainActivity.kt:285` |
| M4 | The viewer's whole screen recomposes on every download progress update of the opened file (the file states are part of `Content`), so a photo that is still downloading recomposes the bars, the toolbar and the page during the transform. | Medium on a slow network, none for cached photos | `MediaViewerViewModel.kt:291`, `MediaPage.kt:208` |
| M5 | The full photo is decoded at its full pixel size (up to 2560 × 2560, 20 MB as a hardware bitmap) into a memory cache of 25% of the memory class; a few photos evict the conversation's bubble images, which decode again (placeholder, blur and fade each) as the viewer closes. The preview size is decoded a second time at a size the bubble did not use. | Medium: the way back draws into a conversation that is loading again | `MediaPage.kt:206-208`, `ImageLoaderModule.kt:65` |
| M6 | Closing the viewer cancels the opened file's unfinished download; the bubble's own image request of the same file (photos without a larger size) then fails for good and the bubble keeps its blurred placeholder. | Correctness, on a slow network | `MediaDownloads.kt:68-75`, `TdlibFileFetcher.kt:66` |
| M7 | With an accessibility service running, the overlay's `clearAndSetSemantics` over the conversation and the viewer's semantics make Compose diff the whole tree in the first frames, and every frame of the transform recomputes semantics bounds. The owner's startup trace of 2026-10-25 showed a service running. | Device-specific; check `device.txt` | `OverlayNavigator.kt:175`, `OverlayNavigator.kt:338` |
| W1 | Every photo, video and GIF bubble on screen keeps a live blur effect under its image, rendered in every frame whether or not the image has loaded (the placeholder is never faded out or skipped): a GPU cost per scroll frame the JVM tests cannot see. | Medium: conversation scroll on the device | `MessagePhoto.kt:113`, `MessageVideo.kt:174`, `MediaImage.kt:136` |
| W2 | Cold start: the account registry file is read under a lock the main thread can wait on, through Hilt's `ChatLockRepository` binding in `MainActivity`. | Low: a few ms of `main.onCreate` | `Accounts.kt:120-123`, `Accounts.kt:137-147` |
| W3 | Downloads that outlive their screen: the story viewer never cancels its downloads; a conversation's automatic prefetches keep going after it closes (documented, but unbounded). | Low: battery and data after leaving a media-heavy chat | `StoryViewerViewModel.kt:473-478`, `ChatAutoDownloads.kt:55` |
| W4 | Leaks and stale references across accounts: the widget keeps every account graph it ever saw; call notifications key avatars by slot, so a new account in a removed account's slot gets the removed account's file repository. | Low: rare (removing and adding accounts); the second is also a bug | `RecentChatsWidgetUpdater.kt:265-271`, `CallNotificationBuilder.kt:174-176` |
| W5 | Small: the raise-to-listen setting is read before it is loaded, so the first voice message of a process listens at the ear even when the setting is off; a `PackageManager` query in the share-location sheet's composition; TDLib's error message reaches logcat in release builds. | Low | `RaiseToListen.kt:59-63`, `LocationInputs.kt:61`, `DefaultTdlibClient.kt:251` |

No violation of `CLAUDE.md`'s hard rules was found (§4.5). The chat list has no draw-phase problem of its own (§4.1).

## 1. Opening and closing the media viewer

### 1.1 What happens, frame by frame

The path, as the code runs it (compact window, a photo whose bubble shows the `y` 1280 px size):

1. **The tap.** `MessagePhoto`'s click sends `ChatEvent.OpenMedia`; `ChatViewModel` answers with `ChatEffect.OpenMedia`
   in the next main-loop turn; `ChatRoute.handOver` (`ChatRoute.kt:383`) calls the app's navigation, which pushes
   `MediaViewerRoute` on `OverlayNavigator`.
2. **Frame A: the overlay composes hidden.** `OverlayHost` recomposes (the back stack changed) and puts the `Covered`
   modifier on the conversation (`OverlayNavigator.kt:175`). `OverlayEntry` composes the viewer at `PreEnter`, alpha 0:
   `MediaViewerRoute` creates `MediaViewerViewModel` (its flows, `files` with `SharingStarted.Eagerly`, the Hilt graph
   of the viewer), whose `init` sends `getMessage`; `MediaViewerScreen` composes in its `Loading` state (a `Scaffold`,
   the top bar with back, `DelayedLoadingIndicator`, `ImmersiveSystemBars`, `PredictiveBackHandler`,
   `rememberLauncherForActivityResult`). `ImmersiveSystemBars` sets the system bars' appearance and behaviour
   (`ImmersiveSystemBars.kt:27-29`): three window attribute changes, one relayout.
3. **Frame B: the content composes.** `getMessage` answered from TDLib's memory; `uiState` turns `Content`; the screen
   composes its pager, the page (`BoxWithConstraints`, the blurred minithumbnail, the preview and the full
   `AsyncImage`s, the download indicator), the top bar's title and position, the bottom bar with the caption and the
   floating toolbar (four or five `TooltipBox`es, a `DropdownMenuPopup`). The page's `sharedMediaViewerBounds` enters
   the viewer in `SharedMediaShapes` (a `DisposableEffect`). Coil starts two decodes on its threads. For a video whose
   file is on the device, `focus()` runs in the same turn and `playback.show` builds ExoPlayer on the main thread
   (`ExoVideoPlayer.kt:135`) and prepares it.
4. **Frame C: the bubble's side joins; the gate opens.** The bubble of the opened message recomposes once
   (`rememberViewerVisibility`, `SharedMedia.kt:219`: the derived state of the one message changed) with a child
   transition of the viewer's; `rememberEnterStarted` waited one frame for the gate (`PostponeOverlayEnter`), the
   transition's target turns `Visible`.
5. **Frames D…: the transform.** `OverlayTrace.ENTER`. The overlay's alpha animates in its `graphicsLayer`; the two
   `sharedBounds` move their bounds on the spatial spring in the overlay of the `SharedTransitionLayout`, each side
   measured to the moving bounds (`RemeasureToBounds`), clipped by `MorphingClip` (read in the draw phase), crossfading
   with `fadeIn`/`fadeOut`. Nothing composes (§1.2, question 1). The full photo's decode finishes on a Coil thread and
   its `AsyncImage` fades in (`MediaImages.kt:72`).
6. **Settled.** `OverlayEntry` marks the transition complete; Navigation resumes the entry.
7. **Back.** `DismissState.dismiss` → `onBack` → `popBackStack`: `OverlayHost` recomposes (`Covered` leaves the
   conversation at once, in the first frame of the exit), `OverlayEntry`'s target turns `PostExit` (`OverlayTrace.EXIT`),
   the bubble's side recomposes once more (its child transition inverts), the overlay fades out while the bounds shrink.
   `LifecycleStartEffect` stops the player.
8. **Gone.** The entry's content leaves the composition (`ImmersiveSystemBars` restores the bars: three attribute
   changes and `show`, a relayout; `SecureWindow` releases its request), Navigation destroys the entry, the view model
   is cleared: `playback.release()` releases ExoPlayer on the main thread (`MediaPlayback.kt:165`,
   `ExoVideoPlayer.kt:129`) and `downloads.cancelAll` cancels unfinished downloads (`MediaDownloads.kt:68`).

### 1.2 The questions, with evidence

**1. What composes, measures and draws in the first frames of opening and of closing?**

- *Composition.* `MediaViewerTransitionTest` (`:app`, §2.4) steps the opening and the closing frame by frame above a
  conversation of 31 messages and counts every composable of Pingogram's that composes again, with the main thread's
  CPU time per frame. The result is in §1.3 (M2): the viewer and its view model compose in two frames, the opened
  bubble composes once each way, and the frames of the transform compose nothing, in either screen. The conversation's
  `MessageList` does not run again; the `LazyColumn`'s items keep their compositions.
- *Layout.* The two `sharedBounds` nodes remeasure their own subtrees in every frame (`RemeasureToBounds`,
  `SharedMedia.kt:295`): the page's `Box` with its three images and the blurred minithumbnail, and the bubble's media
  box. Their parents are not remeasured: a shared-bounds node reports its lookahead size to its parent, so the
  `LazyColumn`, the bubble and the pager keep their layouts. The JFR recording of the test (§1.3, M2) shows no
  `LazyList` measure frames in the transform's samples.
- *Draw.* This is where the frames go, and the JVM cannot see it (Robolectric draws nothing): see M1.
- *Animated state in composition.* None found: the overlay's alpha (`OverlayNavigator.kt:228`), the clip's fraction
  (`SharedMedia.kt:243`), the dismiss gesture's progress and offset (`DismissState.kt`, `followingDismiss`), the chrome's
  alpha and the scrim (`MediaViewerScreen.kt`) are all read in `graphicsLayer` or `drawBehind` lambdas; the shared
  bounds are the animation framework's own layout-phase state. The test's zero recompositions per transform frame
  confirm it. The one exception is not animation but data: the file states (M4).

**2. Image decoding.**

- Nothing decodes on the main thread except the 40 px minithumbnail (`BitmapFactory.decodeByteArray` inside
  `remember`, `MediaImages.kt:115`, `MediaImage.kt:144`): about 0.1 ms, once per page or bubble; it could move to the
  mapper but it is not what the user feels.
- The full-size bitmap is decoded on Coil's threads at the photo's own pixel size (`MediaPage.kt:208`:
  `media.full.width × media.full.height`), as a hardware bitmap (`ImageLoaderModule.kt:67`, `allowHardware(true)`), so
  the upload to the GPU happens in the decode, off the main thread. Sizes are explicit everywhere (`ImageRequest.size`
  in `ViewerImage`, `TdlibImage` and `AvatarPhoto`).
- The placeholder hits the memory cache only for the same file id: `largestImageKey(fileId)` (`TdlibFileImage.kt:46`)
  looks for the *full* file's other decodes. The bubble decoded the smallest size that covers its box
  (`selectPhotoSize`, `MediaSize.kt:50`): `y` (1280 px) for a bubble of about 1000 px, which is the viewer's full size
  when the photo has no `w` size, so the placeholder hits; with a `w` (2560 px) size the full file was never decoded
  and the placeholder is null. The second layer, `previewSize` (`MediaPage.kt:206`), covers that case: the largest
  downloaded size other than the full one, with its own placeholder from the bubble's decode. So the photo the
  conversation showed is on the page at once in both cases, as the 2026-11-06 note intended. But the preview layer's
  request asks for that file at the size's own pixels (`preview.width × preview.height`), not the bubble's box, so it
  is a cache miss (with an explicit `size`, Coil's automatic precision is exact, so a cached decode at another size
  does not count): the same JPEG is decoded a second time at a second size during the transform, and the full size a
  third time. Three decodes per photo, two of them during the animation on Coil's threads, which compete for the
  cores the RenderThread needs. (M5.)
- The cache is 25% of the memory class (`ImageLoaderModule.kt:65`); a 2560 × 1920 hardware bitmap is 19.7 MB. On a
  phone whose memory class is 256 MB the cache holds three such photos and nothing else. (M5.)

**3. Video.** ExoPlayer is built on the main thread, in `ExoVideoPlayer.play` (`ExoVideoPlayer.kt:135`), the first
time a video is shown: for a video already on the device that is the frame in which the viewer's content composes
(§1.1 step 3), i.e. the frame before the transform starts. Building it creates the renderers, the audio sink and its
capabilities query, the load control's allocator, the bandwidth meter (a `ConnectivityManager` query), three broadcast
receivers and the playback thread: Media3 documents it as "a few milliseconds"; on a mid-range phone it is 10–30 ms.
`prepare()` and the surface are asynchronous, on the playback thread, and the `TextureView` of `PlayerSurface`
(`MediaViewerRoute.kt:74-78`) is created in the same composition, its `SurfaceTexture` on the first draw; the texture
view is inside the shared bounds, so it is laid out again at every frame of the transform (cheap: a `TextureView`'s
size change only sets its buffer size). The release is worse: `ExoPlayer.release()` is called from `MediaPlayback.release`
when the view model is cleared, at the end of the exit transition (§1.1 step 8), on the main thread, and it blocks
until the playback thread has released the renderers and the codec (Media3's `releaseTimeoutMs`, 500 ms at most;
typically 10–60 ms): the last frame of closing a video is late by that much. `docs/ARCHITECTURE.md`'s rule that
`release()` never blocks a frame is kept for the audio player (its own thread) and not for the video player. (M2.)

**4. Window and effects.**

- *System bars.* `ImmersiveSystemBars` (`ImmersiveSystemBars.kt:23-32`) sets `isAppearanceLightStatusBars`,
  `isAppearanceLightNavigationBars` and `systemBarsBehavior` when the viewer composes and restores all three and
  calls `show(systemBars())` when it leaves. Each setter that changes a value marks the window's attributes changed
  and schedules a traversal with `relayoutWindow`, a synchronous binder call to the window manager on the main thread
  (1–5 ms, more while the system is busy), in the frame before the transform and in the frame after its end. In dark
  theme the appearance values are already false, so only the behaviour changes; in light theme all three. The
  `show` at the start is a no-op while the bars are shown; hiding them by a tap on the media animates the insets,
  which relays out every `Scaffold` in the window for the insets animation's frames, but that is the user's tap, not
  the transform. (M3.)
- *Keep screen on.* `Modifier.keepScreenOn()` (`MediaPage.kt:106`) turns on when a video starts playing: another
  window attribute change and relayout, in the transform's frames when the video's file is on the device. (M3.)
- *`FLAG_SECURE`.* Only in secret chats. `MainActivity.SecureWindowEffect` (`MainActivity.kt:285`) re-applies the flag
  in a `SideEffect` whenever its inputs recompose, and `SecureWindowRequests` counts requests, so the viewer's request
  (count 1 → 2) and its release (2 → 1) each recompose the effect and call `window.addFlags`, which relays out the window
  whether or not the flags changed (`Window.setFlags` does not compare). Two relayouts per open and close in secret
  chats, for nothing. (M3.)
- *Edge-to-edge insets.* Unchanged by the viewer: its `Scaffold` consumes no insets (`contentWindowInsets = 0`), the
  bars read their own. No relayout from insets during the transform.
- *Blur and `RenderEffect`.* Yes, in every frame: the blurred minithumbnail under the viewer's photo is a
  `Modifier.blur` (`MediaImages.kt:108`), a `RenderEffect` on a compositing layer, drawn under the preview and the full
  image whether or not they are loaded (`MediaPage.kt:203`: `PhotoLayers` always composes it; `VideoLayers` until the
  first frame). The bubble's side has the same (`MessagePhoto.kt:113`, `MediaImage.kt:136`), and nothing ever hides
  it: `PhotoImage` and `VideoThumbnail` compose the placeholder under the image without an alpha of their own
  (`fadingOutAs` exists in `MediaImage.kt` and is used by stickers, reactions and the GIF tab, not by photos and
  videos), so the blur is rendered under the loaded image too, covered but drawn. Both sides are measured to the moving
  bounds, so both blur layers change size every frame and are rendered anew. The message menu's blur (`MessageLift.kt:127`) is
  not involved in the viewer. (M1.)
- *The scrim.* `drawRect` behind the content (`MediaViewerScreen.kt`), cheap; but it is inside the overlay's
  `graphicsLayer { alpha }`, so the whole overlay, scrim included, is composited offscreen while it fades. (M1.)

**5. The clip-shape morph and `SharedMediaShapes`.** No. `published` is a plain `HashMap` written from a
`Modifier.Node`'s attach and detach (`SharedMedia.kt:165-178`), read in the draw phase by `viewerCounterpart`; it is
not snapshot state and invalidates nothing. `inViewer` is a `mutableStateMapOf` (`SharedMedia.kt:104`) read through a
`derivedStateOf` per media bubble (`SharedMedia.kt:219`): a change (the viewer entering or leaving one message)
re-evaluates every media bubble's derived state, but only the bubble whose value changed recomposes, as the test
shows (one bubble, once each way). `isInViewer` has no caller. The morph itself reads one animated float in
`getClipPath`, per side, per frame.

**6. Predictive back and the overlay.** Seeking the gesture recomposes nothing: `backProgress` is read in
`followingDismiss` (a `graphicsLayer`), in the chrome's `graphicsLayer` and in the scrim's `drawBehind`. The gesture's
completion recomposes `DismissibleContent` and `PredictiveBackDismiss` once (`isDismissed`) and remeasures the content
once (`layoutFollowingDismiss`), so the shared bounds start from the scaled content. The overlay host recomposes once
when the back stack changes and once when the transition settles; the screen below it never (it skips: the same
`below` lambda, `OverlayNavigator.kt:175`). The exit is one transition for the overlay's alpha and the shared bounds
(`OverlayEntry`'s transition, the bubble's child transition of it), so nothing seeks the two apart.

### 1.3 Findings, ranked

#### M1. Per-frame offscreen layers and blur effects (high)

**Where.** During the transform each frame composites, on a 1440 × 3120 display:

| Layer | Why it is offscreen | Size | Where |
|---|---|---|---|
| The viewer's blurred minithumbnail | `Modifier.blur` = a `RenderEffect` on a compositing layer, always drawn under the photo, its layer resized every frame by `RemeasureToBounds` | the page's media box, growing to the window | `MediaPage.kt:203`, `MediaImages.kt:108` |
| The bubble's blurred minithumbnail | the same, always drawn (never faded out or skipped once the image loaded); resized every frame | the bubble's media, growing to the window | `MessagePhoto.kt:113`, `MediaImage.kt:136`, `SharedMedia.kt:295` |
| The overlay | `graphicsLayer { alpha }` on the whole viewer while it fades in and out: alpha < 1 makes HWUI render the layer to a buffer first (`saveLayer`) | the window | `OverlayNavigator.kt:228` |
| The shared element's two sides | `fadeIn` / `fadeOut` in `sharedBounds`: each side's layer drawn with alpha < 1 in the `SharedTransitionLayout`'s overlay | the moving bounds, up to the window | `SharedMedia.kt:292-293` |
| The full photo fading in | `graphicsLayer { alpha }` while it fades on the effects spring, once decoded from disk | the page | `MediaImages.kt:72` |
| The chrome fading in | `AnimatedVisibility(fadeIn)` on the top and bottom bars | two strips | `MediaViewerScreen.kt`, `Chrome` |

A full-window RGBA buffer is 18 MB at that resolution; each offscreen layer is written and read once per frame, and a
blur reads and writes its layer several times (Skia downsamples large sigmas; the sigma here is the scale that
stretches a 40 px picture to the page, times 1.5 px: about 50 px). Four full-window passes and two blurs at 120 Hz
are on the order of 20 GB/s of memory traffic, which is what the GPU of a 2024 flagship can sustain for a plain app;
the RenderThread then overruns the 8.3 ms frame and the transform drops to 60 Hz or below, which reads as "laggy"
while the main thread shows nothing wrong. This is reasoning from the code, not a measurement: the trace of §2.2
measures it (`render_frame_max_ms` and the RenderThread's `waitForGpuCompletion` and `DrawFrames` slices inside
`overlayEnter` / `overlayExit`).

**Fix.**

1. *Stop blurring with a `RenderEffect`.* Blur the 40 px minithumbnail once, at its own size, when the message is
   mapped (a box blur of 1.5 px on 1,200 pixels, microseconds, on the mapper's dispatcher; Telegram's apps do the same
   with a native stack blur on the tiny bitmap) and draw the result scaled up with bilinear filtering: the look is the
   same (a blur before upscaling and after it differ only in the upscaling's own interpolation), and no layer, no
   effect and no per-frame work remain, in the viewer, in every bubble (W1) and in the stories viewer. Keep
   `BlurredEdgeTreatment.Rectangle`'s clipping through the existing clip. One place: a `BlurredMinithumbnail` bitmap
   cached per `Minithumbnail` (they are immutable and small), in `core:ui`, used by `feature:chat` and `feature:media`.
2. *Do not draw what is covered.* Once the image over the placeholder has loaded (its alpha is 1), skip the
   placeholder in the draw phase (`drawWithContent { if (alpha.value < 1f) drawContent() }` placed before the blur in
   the placeholder's modifier chain, read in draw, no recomposition), in `PhotoImage`, `VideoThumbnail`,
   `PhotoLayers` and `VideoLayers`. Today nothing hides it: the placeholder's blur is rendered under every loaded
   photo and video in the conversation (W1). With fix 1 this is a plain bitmap draw, so it matters less, but it also
   removes the overdraw.
3. *One fade, not three.* The overlay's alpha and the shared element's crossfade both fade the same pixels. Keep the
   shared element's crossfade (it needs both sides) and fade only what is not shared: draw the scrim with alpha in
   `drawBehind` (already there: `1 - dismiss.fraction`) from the overlay's transition instead of wrapping the whole
   overlay in a `graphicsLayer { alpha }`, and fade the chrome through its own `AnimatedVisibility` (already there).
   That removes a full-window `saveLayer` per frame. If the overlay's fade must stay for other overlays, set
   `CompositingStrategy.ModulateAlpha` on that layer: HWUI then multiplies each draw's alpha instead of compositing the
   layer offscreen, which is right for a scrim plus one opaque image.
4. *The bubble's side need not remeasure.* `ScaleToBounds` for the bubble's side (keep `RemeasureToBounds` for the
   viewer's, which is what uncrops the photo) with the crossfade finishing in the first third of the transform
   (`fadeOut` on `fastEffectsSpec`): the bubble's layer is then drawn scaled, not re-laid out and re-rasterised every
   frame, and it is gone from the compositing before the bounds reach the window's size.

**Expected gain.** Per frame of the transform at QHD: from about 4 full-window offscreen passes plus 2 blurs to 1
pass (the shared element's crossfade) and no blur. On the S24 Ultra that should bring the RenderThread's frames inside
the 8.3 ms budget; on the low-end phone of `docs/PERFORMANCE.md` §2 it is the difference between a transform and a
slideshow. Fix 1 also takes a blur per media bubble out of every scroll frame of the conversation (W1).

#### M2. The main thread in the first and last frames (high for videos, medium for photos)

**Evidence.** `MediaViewerTransitionTest`'s table (§2.4; the numbers below are from this machine's JVM and are
relative, not a phone's):

MEASURED_TABLE_PLACEHOLDER

**Where.**

- *Opening, frames A–C.* The view model's construction (`MediaViewerViewModel.kt`: nine repositories, `MediaSharing`,
  `MediaDeletion`, `MediaDownloads`, `MediaPlayback`, five `stateIn` / `combine` chains, `files` eager), the screen in
  its `Loading` state, then the screen in its `Content` state: `HorizontalPager` and the page's `BoxWithConstraints`
  subcomposition, three `AsyncImage`s, `ViewerTopBar` (`TopAppBar`), `ViewerBottomBar` (the caption's
  `toAnnotatedString`, the video controls, `HorizontalFloatingToolbar` with a `TooltipBox` and a `PlainTooltip` state per
  button and a `DropdownMenuPopup`), the predictive back handler, the activity result launcher, the system bars. All
  of it in one or two frames, on the main thread, before the transform may start (`PostponeOverlayEnter` holds it
  until `Content`). On the JVM those frames cost MEASURED_OPEN_PLACEHOLDER of main-thread CPU against
  MEASURED_TRANSFORM_PLACEHOLDER for a frame of the transform.
- *Opening a video on the device.* `ExoPlayer.Builder.build()` on the main thread (`ExoVideoPlayer.kt:135`) inside
  the frame that composes the content (§1.2, question 3).
- *Closing, the last frame.* `ExoPlayer.release()` on the main thread (`ExoVideoPlayer.kt:129`), blocking until the
  playback thread has released the codec and the audio track, when the view model is cleared at the end of the exit
  transition (`MediaViewerViewModel.kt:295`).
- *Closing, the first frame.* `OverlayHost` recomposes and the bubble's side recomposes once (cheap), the system bars
  are restored (M3).

**Fix.**

1. *Build the player ahead and off the main thread.* `ExoPlayer.Builder(context).setLooper(Looper.getMainLooper())`
   may be built on any thread (its handlers are made for the looper given); build it in the view model's `init` on the
   `Default` dispatcher as soon as the opened item is known to be a video or GIF (`loadOpened`'s answer), so it is
   ready before the content composes. Or keep one `VideoPlayer` per process (as Telegram's apps do), built once on a
   background thread and reused by the viewer, the stories viewer and picture-in-picture, with `stop()` between uses
   (asynchronous) and `release()` only on `onTrimMemory` or minutes of disuse.
2. *Release after the transition, not in its last frame.* Until a shared player exists: in `MediaPlayback.release`,
   `stop()` at once (asynchronous) and post the `release()` to the main looper with a delay (`Handler.postDelayed`,
   about a second), or run it from the `@ApplicationScope` after `delay`, so the blocking call lands in an idle frame.
   `ExoPlayer.release()` must be called on the player's looper, so it cannot move to a worker thread; it can only move in
   time.
3. *Compose less in the content's first frame.* The bottom bar's toolbar composes its tooltips' state and popups for
   every button before anything shows; `TooltipBox` is the heaviest part of a toolbar whose buttons are all
   icon-only (`MediaToolbar.kt`). Compose the bottom bar one frame after the pages (a `LaunchedEffect` + `withFrameNanos`
   flag, as `DelayedLoadingIndicator` delays), or at least after the transform (`OverlayEntry`'s transition settles):
   the photo is what the user looks at during the transform; the bars fade in anyway.
4. *The view model's construction* is fine as it is once the player is out of it; `files`' `SharingStarted.Eagerly`
   subscribes TDLib file observers before the first frame, which is wanted.

**Expected gain.** On the phone: the frame before the transform shrinks by the player's construction (10–30 ms where
it applies) and by the bottom bar's composition (a few ms); the last frame of closing a video no longer blocks on the
codec (10–60 ms). The tap-to-motion latency drops by about that; the transform's own frames are M1's.

#### M3. Window relayouts in the transform's frames (medium)

**Where.** `ImmersiveSystemBars.kt:27-29` and `:33-36`, `MediaPage.kt:106` (`keepScreenOn` when a video plays),
`MainActivity.kt:285` (`FLAG_SECURE` re-applied on every change of the request count). Each is a `WindowManager`
relayout: `relayoutWindow` is a binder call that holds the window manager's lock, 1–5 ms on the main thread and
sometimes much more while the system is busy (the first frame of an animation is when it is busy), in the first frame
of the opening and the first and last of the closing.

**Evidence.** The code; on the device `media-viewer.sql`'s "longest main-thread slices inside the sections" lists
`relayoutWindow` (the `wm` and `view` categories) with its count and total per section.

**Fix.** Set each window attribute only when it differs (`if (controller.isAppearanceLightStatusBars) … = false`;
`systemBarsBehavior` only if it differs), restore only what was changed; in `SecureWindowEffect` call `addFlags` /
`clearFlags` only when `secure` changed (remember the last value), and expose `isRequested` as a derived boolean so a
count change from 1 to 2 recomposes nothing. `keepScreenOn` is right as it is (a video must keep the screen on) but it
could be set once the transform settled rather than when the first frame renders.

**Expected gain.** 1–3 relayouts fewer per open and close: a few ms off the first and last frames, more on a busy
system. Small on its own, worth it because those frames are the ones M2 already loads.

#### M4. The viewer recomposes on every download progress update (medium on a slow network)

**Where.** `MediaViewerViewModel.kt:291`: `uiState` combines `files` (the states of the current item's and its
neighbours' files, every `updateFile` of a download in progress) into `Content`, and every `Content` recomposes
`MediaViewerScreen`: the `Scaffold`'s slots, `ViewerTopBar`, `ViewerBottomBar` and its toolbar (which reads the file
state for its pending indicator), `MediaPager` and the page, whose `remember(media, files)` recomputes `previewSize`.
TDLib reports progress many times a second for a file of a few MB: on a slow network the opening transform runs
while the viewer recomposes its whole screen several times a second.

**Evidence.** The code; `ChatRecompositionTest`'s approach (counting recompositions while file states change) applied to
the viewer would show it, as would the device trace's `Compose:recompose` slices inside `overlayEnter` for a photo not
yet downloaded.

**Fix.** Keep per-file progress out of `Content`: hand the page a `State<FileState?>` (or a lambda) per file read only
by `DownloadProgress`'s indicator (`progress = { … }` is already a lambda) and by the toolbar's pending indicator, as
`VideoPlayback.progress` already does for playback. `Content` then changes only when the items, the current item, the
playback session or the actions change.

**Expected gain.** No recomposition of the viewer during a download; the first-open-on-mobile-data case stops
competing with the transform.

#### M5. Decoding and memory: three decodes per photo, full-size bitmaps evict the conversation's (medium)

**Where.** `MediaPage.kt:206-208`: the preview layer decodes the bubble's file again at the size's own pixels (a
cache miss, §1.2 question 2) and the full layer decodes the largest size at its full pixels; `ImageLoaderModule.kt:65`:
the memory cache is 25% of the memory class. A `w` photo (2560 × 1920) is 19.7 MB as a hardware bitmap; three of them
fill a 64 MB cache, and Coil evicts least recently used: the conversation's bubble images. When the viewer closes,
the bubbles behind it decode again: each one's placeholder shows (with its blur, M1/W1), its image fades in (an alpha
layer per bubble for the fade), on top of the exit transform. Paging through a chat's photos makes it certain.

**Evidence.** The sizes in the code and Coil's cache arithmetic; on the device, `adb shell dumpsys meminfo` before and
after opening three photos, or Coil's `MemoryCache.size` logged in a debug build, shows the eviction. The symptom to
look for on the phone: after closing the viewer, the photos around the opened one flash their blurred placeholders.

**Fix.**

1. Decode the viewer's full image at the window's size (the larger side, times 2 for zoom headroom, which is the
   most a zoom shows sharply), not at the photo's pixels; decode the true full size only when the user zooms past 2×
   (replace the request then, with the current bitmap as its placeholder). Telegram's apps decode the viewer's image
   at about the screen's resolution for the same reason.
2. Request the preview layer at the size the bubble decoded it at (the bubble's box; `selectPhotoSize` and
   `mediaDisplaySize` are deterministic from the photo's sizes, or pass the bubble's size along with the message id in
   `SharedMediaShapes`), or drop the preview layer where `largestImageKey` found a placeholder: the placeholder is that
   bitmap.
3. Consider a larger memory cache share for a media app (Coil's default is 25%; 40% is reasonable with hardware
   bitmaps, which do not live in the Java heap), after measuring.

**Expected gain.** One decode per photo opened instead of three; the conversation's bitmaps stay cached through a
viewer session, so the way back draws a conversation that is already there, as the 2026-10-26 overlay change intended.

#### M6. Closing the viewer cancels a download the bubble is waiting for (correctness)

**Where.** `MediaDownloads.cancelAll` (`MediaDownloads.kt:68-75`) cancels, when the view model is cleared, every
download it asked for that is not complete, the opened item's included (`CancelDownloadFile`). The bubble's own image
request of the same file (`TdlibImage` → `TdlibFileFetcher`) is still waiting for it when the photo's bubble size and
the viewer's full size are the same file (every photo without a `w` size: `selectPhotoSize` picks `y` for a bubble of
about 1000 px, and `ViewerMedia.Image.full` is the largest size), and `TdlibFileFetcher.kt:66` fails the request for
good when a download it saw active stops: the bubble keeps its blurred placeholder until it leaves the screen and
comes back. The path: a photo not yet downloaded (mobile data, a large photo), tap, back before it finished. The same
cancellation happens to a neighbour's file when paging more than two pages away (`KEEP_DISTANCE`).

**Fix.** On close, cancel only the neighbours' prefetches, never the file of an item that was shown (TDLib finishes it
and the bubble shows it); and make `TdlibFileFetcher` tolerant of a download that stops without an error while the
image still asks for it: request it again once (its `download` is idempotent) instead of failing.

**Expected gain.** No bubble stuck on its placeholder after a quick open and close.

#### M7. Semantics while an accessibility service runs (device-specific)

**Where.** `OverlayNavigator.kt:175` puts `clearAndSetSemantics {}` over the conversation while an overlay is on
the back stack and removes it when the back stack empties (the first frame of the exit), and the viewer's pages,
bars and tooltips add their own nodes. With an accessibility service connected, `AndroidComposeView` recomputes
semantics changes after every frame that changed layout, and a subtree appearing or disappearing is a full diff of
it. The owner's startup trace of 2026-10-25 (`docs/PERFORMANCE.md` §2) found a service running on the phone
("Compose worked out its semantics in every frame: about 1.2 s over the trace"); if it still is, every frame of the
transform pays for the semantics of the moving nodes, and the first frames for the conversation's whole subtree.

**Evidence.** `device.txt` of the trace (§2.2) lists the enabled services; `media-viewer.sql` lists
`checkForSemanticsChanges` / `sendAccessibilitySemanticsStructureChangeEvents` among the main thread's slices inside
the sections if they are there.

**Fix.** None in the app beyond keeping the trees small; the measurement tells whether the owner's phone pays it. If it
does, run the trace once more with the service off to separate it from M1–M3.

## 2. The measuring tools in this pull request

### 2.1 Trace sections

`core:ui`'s `MediaViewerTrace` and `OverlayTrace` (`core/ui/.../trace/MediaViewerTrace.kt`): four async sections.
`openMedia` begins where `ChatRoute` hands the tap to the app's navigation (`ChatRoute.kt`, `handOver`) and ends at
the start of the frame after the viewer composed its pages (`MediaViewerScreen`, `OpenMediaTraceEffect`); `closeMedia`
begins in `DismissState.dismiss` (back, a swipe, the button) and ends when `MediaViewerRoute` leaves the composition;
`overlayEnter` and `overlayExit` span the overlay's transitions (`OverlayEntry`, `OverlayTransitionTrace`): every frame
of the container transform is inside one of them. `MediaViewerScreen`'s root carries the test tag `media_viewer`,
which UiAutomator sees as a resource id (`MainActivity` shows test tags as ids).

### 2.2 The Perfetto trace of the owner's phone

`tools/perfetto/media-viewer.sh` (Linux and macOS) and `tools/perfetto/media-viewer.ps1` (Windows PowerShell), with
`media-viewer.pbtxt` and `media-viewer.sql`. Steps, on the owner's phone:

1. Build and install the profileable release build, then sign in:

   ```bash
   ./gradlew :app:installFossBenchmarkRelease
   ```

   On Windows:

   ```powershell
   .\gradlew.bat :app:installFossBenchmarkRelease
   ```

   (Uninstall a release-signed Pingogram first, as `docs/PERFORMANCE.md` §9 says.)
2. In Pingogram, open Saved Messages (or any chat) so that a photo you sent, without a caption, is among the newest
   messages on screen. Keep that conversation on screen, the phone unlocked, the screen on, and plug it out.
3. From the repository, run the script. It finds the photo's bubble on screen, records one trace while it taps the
   photo and presses back 10 times (about a minute), and writes the trace, `device.txt` and, where Perfetto's Python
   package is installed (`pip install perfetto`), `summary.txt`:

   ```bash
   tools/perfetto/media-viewer.sh
   ```

   On Windows:

   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\perfetto\media-viewer.ps1
   ```

   Options: the output directory, the package, the number of openings, and the tap's inset from the bubble's end in
   dp (`-TapInsetDp` on Windows; raise it for a wide bubble whose photo is not where the tap lands).
4. Send back `device.txt` and `summary.txt` (a few KB of numbers; the trace itself stays on your computer and can be
   opened in https://ui.perfetto.dev, which reads it in the browser). If the package is missing, the script prints the
   one command that makes `summary.txt`.

What the summary answers: each section's length and the frames in it on the main thread and the RenderThread (count,
longest, 90th percentile), the frames SurfaceFlinger presented late and the jank type it blamed, the main thread's
longest slices inside the sections (`relayoutWindow` for M3, `checkForSemanticsChanges` for M7, `Compose:recompose`
for M4), the RenderThread's slices (`waitForGpuCompletion`, `queueBuffer` and long `DrawFrames` for M1), the CPU time
per thread (Coil's decodes for M5, the player's threads for M2) and the main thread's states. Then once more after
each fix.

### 2.3 The Macrobenchmark journey

`benchmark/.../OpenMediaBenchmark.kt`: `openMediaCompilationNone` and `openMediaBaselineProfile`, 10 iterations each,
`FrameTimingMetric` and the four sections. Each iteration opens Saved Messages (whose newest photo it taps,
`Journeys.openNewestPhoto`), waits for the viewer (`media_viewer`), presses back and waits for it to go. Needs a photo
without a caption, sent by yourself, among Saved Messages' newest messages. Run it alone, with the app signed in:

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.class=dev.kyralabs.pingogram.benchmark.OpenMediaBenchmark
python3 tools/benchmark/compare.py benchmark/build/outputs/connected_android_test_additional_output
```

On Windows, each `-P` quoted, as `docs/PERFORMANCE.md` §9 shows for `SendMessageBenchmark`:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true" "-Pandroid.testInstrumentationRunnerArguments.class=dev.kyralabs.pingogram.benchmark.OpenMediaBenchmark"
py tools\benchmark\compare.py benchmark\build\outputs\connected_android_test_additional_output
```

Send back the `*-benchmarkData.json` file and what `compare.py` printed. `budgets.json` has no budget for it yet: the
first run sets one (a proposal: `openMediaSumMs.median` ≤ 100 ms with the history cached, `frameOverrunMs.latePercent`
≤ 1 over the iteration, as the conversation's), and `--write-baseline` records it as the device's baseline.

### 2.4 The JVM test

`app/src/test/.../MediaViewerTransitionTest.kt` (with a copy of `feature:chat`'s `RecompositionCounter` in `:app`'s
tests): the whole shell on Robolectric above Ada's conversation (31 messages), the photo opened through its bubble's
action, 60 frames of the test clock, back, 60 more; per frame the composables that composed again and the main
thread's CPU time, printed as a table, and asserted: the frames of the transform compose nothing, the bubbles compose
at most once each way, the list never. With `-Ppingogram.jfr=<directory>` (`docs/PERFORMANCE.md` §9) the printed
times place the recording's samples.

```bash
./gradlew :app:testFossDebugUnitTest --tests '*MediaViewerTransitionTest*' -Ppingogram.jfr=/tmp/jfr
```

## 3. The wider review

### 3.1 Jank and recomposition in the conversation and the chat list

- **W1, the conversation's blur layers** (medium, device only). `MinithumbnailPlaceholder` (`MediaImage.kt:136`) is a
  `Modifier.blur` under every photo (`MessagePhoto.kt:113`), video and GIF (`MessageVideo.kt:174`), album item and
  video message on screen, and nothing hides it once the image has loaded (`PhotoImage` and `VideoThumbnail` give the
  placeholder no alpha; `fadingOutAs` is used by stickers, reactions and the GIF tab only): each is a `RenderEffect`
  layer the size of the bubble's media, re-rendered in every scroll frame, under the loaded image too. With four
  media bubbles on screen that is four blurs of about 1000 × 750 px per frame on a QHD phone. `ChatScrollCostTest`
  cannot see it: Robolectric draws nothing, and `docs/PERFORMANCE.md` §9 already notes that most of a phone's
  conversation frame is drawing and the RenderThread. Fix: M1's fix 1 (blur the 40 px picture once, draw a bitmap) and
  fix 2 (do not draw the placeholder under a loaded image). Expected gain: a blur per visible media bubble out of each
  scroll frame on the device; measurable with `ConversationScrollBenchmark` on a chat with photos, before and after.
- **Recomposition.** `ChatRecompositionTest` and `ChatListRecompositionTest` hold; `MediaViewerTransitionTest` adds the
  overlay case (§1.2). Nothing new found in composition: the bubbles' layouts are single-pass (`BubbleLayoutsTest`),
  times are mapped, semantics are built in `semantics {}`.
- **The chat list.** No draw-phase layer per row beyond the avatar's fade (`Avatar.kt:194`, a 48 dp layer with alpha
  while a photo fades in, then alpha 1) and the selected or swiped row's container; the swipe background returns early
  while settled (`ChatRow.kt:253`). The prior measurements (`docs/PERFORMANCE.md` §9) stand; no new finding.
- **Message menu blur** (`MessageLift.kt:127`): a full-screen `RenderEffect` whose radius animates with the menu's
  spring, so every frame of the menu's opening and closing re-blurs the whole conversation. By design (Material's
  blur), at the same cost class as M1 for the menu's frames; if the menu feels heavy on the owner's phone, the same
  trace script with a long press instead of a tap would show it. Not changed here.

### 3.2 Main-thread blocking

- **W2, the account registry at cold start** (low). `Accounts.active` (`Accounts.kt:120-123`) reads the registry file
  and the demo marker under `synchronized(this)` when the registry is not loaded yet; `start()` launches the read on
  the IO dispatcher (`Accounts.kt:133`), and `MainActivity`'s injection of `ChatLockRepository`
  (`ActiveAccountModule.kt:105` → `accounts.active`) happens while that read is in flight, so the main thread blocks on
  the lock for the file read (`AccountRegistry.kt:38`, `readText()`) and the marker's `exists()`. A few ms on a fast
  phone, part of `main.onCreate`'s 31 ms on the low-end one. Fix: make `active` wait on `loaded` only when needed, and
  give `MainActivity` the lock repository lazily, after the settings it already awaits.
- Minor: `LocationInputs.kt:61` resolves `geo:` handlers with the `PackageManager` in composition, once per context
  (the share-location sheet, not a scrolled screen); the minithumbnail decode in composition (§1.2, question 2).
- Checked and fine: `runBlocking` only on the call engine's and the player's own threads (`TdlibConferenceEncryption`,
  `TdlibDataSource`); every file, contacts, Keystore and PBKDF2 use is on IO or Default; `PingogramApplication.onCreate`
  keeps to injection and launching; no `Thread.sleep`, no `commit()`.

### 3.3 Leaks: lifecycles, coroutines outliving screens, Coil and ExoPlayer references

- **W4a, `WidgetAccounts.made`** (`RecentChatsWidgetUpdater.kt:265-271`): a `@Singleton` map from every
  `AccountRepositories` ever seen to its `WidgetAccount` (four repositories and an `AvatarIcons`), never pruned; a
  removed account's whole graph stays reachable for the process. `NotificationRenderer` and `StorageOptimization`
  prune theirs. Fix: drop entries whose slot left `AccountGraphs.accounts`.
- **W4b, `CallNotificationBuilder.accountAvatars`** (`CallNotificationBuilder.kt:174-176`): keyed by slot number, never
  pruned; `Accounts.add` reuses the lowest free slot (`Accounts.kt:177`), so after removing an account and adding
  another the new account's call notifications fetch caller avatars through the removed account's `FileRepository`, on a
  closed client (a wrong or missing avatar, and the old graph kept alive). Also a correctness bug. Fix: key by the
  account's repositories (identity) or clear on removal.
- Minor: `ShareInbox` keeps a delivered share per chat until that chat's conversation claims it (`content://` grants
  that end with the process anyway); per-chat `Mutex` maps in `HistoryLoads` and `TdlibForumTopicRepository` grow by
  one small object per chat opened, per account's life.
- Checked and fine: both ExoPlayer owners release (`MediaPlayback.release`, `StoryTimeline.release`,
  `VideoPipViewModel.onCleared`), picture-in-picture hands over data, not a player; the only `ImageLoader` is the
  singleton with the application context; every receiver, sensor, content observer and lifecycle callback has its
  removal; no `Activity` or `Context` in view models or singletons beyond `@ApplicationContext`.

### 3.4 TDLib calls not cancelled when screens leave

- **W3a, the story viewer** (`StoryViewerViewModel.kt:473-478`): downloads at visible and prefetch priority through
  `viewModelScope.launch { fileRepository.download(…) }`, which TDLib answers at once; nothing cancels them when the
  viewer closes (`onCleared` releases the timeline and closes the story only). The media viewer does cancel
  (`MediaDownloads.cancelAll`). Fix: the same `cancelAll` on clear, keeping what came.
- **W3b, a conversation's automatic prefetches** (`ChatAutoDownloads.kt:55`): the files of every message that passed
  the screen download at prefetch priority and keep downloading after the conversation closes; the class says nothing
  is cancelled when a message leaves the screen (the viewer may want it), which is right, but leaving the chat is not
  that case: scrolling a media-heavy chat and leaving it leaves tens of downloads to finish on the connection the next
  chat needs. Fix: on the view model's clear, cancel the prefetches TDLib has not finished (what came stays; the viewer
  requests its own at visible priority).
- The viewer's cancellation on close is right in intent and wrong in one case (M6).
- Checked and fine: `send` is cancellable and every repository call runs in a screen's scope or in the documented
  application-scope cases (forwarding, sharing, uploads, deletions, `closeStory`, folder grants); `WhileSubscribed`
  stops the call timers, the location search and the viewer's state in the background.

### 3.5 The hard rules of `CLAUDE.md`

Nothing broken: no Material 2 or View widgets (the one `AndroidView` wraps WebRTC's `TextureView`), no `GlobalScope`,
no literal tokens in feature code beyond `0.dp`, "Telegram" only in descriptive text and link hosts, no Java outside
`org.drinkless.tdlib`, no analytics, crash or ad SDK (Firebase Messaging alone, `gms` only, with its collection
turned off), no network outside TDLib and the call engine. One note: `DefaultTdlibClient.kt:251` logs TDLib's error
message (`"$what failed: ${error.code} ${error.message}"`) with `Log.w` in release builds too (`TdlibNative.kt:64`);
TDLib's messages are codes and short texts, but some echo input (`"Can't parse entities: …"` with the text's
fragment), so the release log should keep the code only, as every other `warn` keeps to class names.

### 3.6 Correctness

- M6 (a cancelled download the bubble waits for) and W4b (avatars through a removed account's repository), above.
- **W5a, raise to listen before its setting is read** (`RaiseToListen.kt:59-63`): `start()` launches the settings
  collector and reads `current` in the same call; on the first voice message of a process `current` is still the
  default (`RaiseToListenSettings()`, enabled), so a user who turned raise to listen off still gets the proximity
  sensor and the earpiece for that first message. Fix: read the setting once (`first()`) before listening, or start
  listening from the collector.
- Checked and fine: the viewer's dismissal is idempotent, its page keys survive paging in front, its deletion of the
  last item closes it once; the overlay completes Navigation's transitions on every path including a replaced back
  stack; `PostponeOverlayEnter` times out.

## 4. What to do next, in order

1. Run §2.2 and §2.3 on the owner's phone (with and without the accessibility service, if one runs) and attach the
   summaries to this file.
2. Separate pull requests, smallest first, each with its measurement:
   - M3: window attributes set only when they differ (one screen, a test of `ImmersiveSystemBars`' calls).
   - M6: do not cancel a shown item's download on close; a tolerant fetcher (`TdlibFileFetcherTest`).
   - M1: the minithumbnail blurred once as a bitmap, placeholders not drawn under loaded images, one fade; the trace of
     §2.2 before and after.
   - M2: the player built ahead and released after the transition; the bottom bar after the pages.
   - M5: the viewer's decode sizes and the cache share; `dumpsys meminfo` and the way back's look.
   - M4: file states out of `Content`.
   - W1–W5 as they fit.
3. Add a budget for `OpenMediaBenchmark` to `benchmark/budgets.json` from the first run, and its numbers to the device's
   baseline.
