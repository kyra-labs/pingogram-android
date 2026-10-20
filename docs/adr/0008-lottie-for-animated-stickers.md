# 0008 — Animated stickers: Lottie (pure Java) behind our own interface, rendered off the main thread

Date: 2026-10-20 · Status: accepted

## Context

Most of Telegram's stickers are animated TGS files: Lottie (Bodymovin) JSON, gzipped, 512 × 512, usually 60 frames per
second for up to 3 seconds (`REQUIREMENTS.md` FR-C4, FR-C5). Conversations show them in bubbles and the composer's new
sticker panel shows dozens at once. `docs/ARCHITECTURE.md` asked to prefer rlottie for performance and to keep the
renderer behind an interface; `docs/PERFORMANCE.md` §4 asks for a frame cache keyed by sticker and size, at most 8
animating stickers, nothing animating off screen or while the window has no focus.

Options:

- **rlottie** (Samsung's C++ renderer, MIT, which Telegram's own app uses through JNI): the fastest software renderer
  for this format, but native code. It is not published as an Android artifact; Pingogram would build it with the NDK
  for three ABIs (16 KB-aligned for arm64 and x86_64), add a JNI layer of its own, and a CI workflow like TDLib's
  (`docs/TDLIB.md`), with the same reproducibility and supply-chain questions. Its size could not be measured here
  without building it.
- **Lottie for Android** (`com.airbnb.android:lottie` 6.7.1, Apache 2.0): the reference renderer outside the web,
  pure Java (class files version 52, Java 8, within the JDK 17 build), widely used, drawing with `android.graphics` into
  any `Canvas`. Its only runtime dependencies are AppCompat (for its View, `LottieAnimationView`) and Okio (already in
  the app through Coil, resolved to the app's 3.18.1). `lottie-compose` would add a Compose wrapper that draws on the
  main thread in each frame; not needed.
- **Compose-multiplatform Lottie ports** (e.g. Compottie): pure Kotlin, younger, less proven on Telegram's stickers.
- **dotLottie / ThorVG**: native again.

Size, measured on the AAR's `classes.jar` (468 KB) with build-tools 36's D8 and R8, min SDK 26:

| | dex | compressed |
|---|---|---|
| D8, nothing shrunk (upper bound) | 351 KB | 156 KB |
| R8, keeping only the API Pingogram calls (`LottieCompositionFactory.fromJsonInputStreamSync`, `LottieDrawable`) | 144 KB | 76 KB |

The same in every ABI's APK (no native code), against the 35 MB arm64 budget (`docs/PERFORMANCE.md` §1). AppCompat is
not counted: it is excluded (below).

## Decision

- **Lottie for Android 6.7.1**, in `core:ui`, without its AppCompat dependency (`exclude(group = "androidx.appcompat",
  module = "appcompat")`): only `LottieAnimationView` needs it, which Pingogram never uses (no Views, `CLAUDE.md`
  rule 4) and R8 removes. `app/proguard-rules.pro` tells R8 that its superclass is missing on purpose. Lottie's own
  composition cache and network loading are not used.
- **Behind an interface** (`core:ui` `sticker/StickerAnimation.kt`): `StickerAnimationDecoder` decodes a file into a
  `StickerAnimation` (frame count, frame rate), whose `StickerFrameRenderer`s draw a frame into a bitmap.
  `LottieStickerDecoder` is the only implementation; `AnimatedStickers` and the composables know only the interface,
  so rlottie (or a newer Lottie) can replace it in one class.
- **Off the main thread**: `AnimatedStickers` (one per process, `:app`'s `AnimatedStickersModule`, handed to the
  composition as `LocalAnimatedStickers`) downloads the TGS file through `FileRepository` at visible priority, decodes it
  on two background threads, and each playing sticker renders its next frame there into one of two bitmaps while the
  other shows. `AnimatedSticker` is a `Modifier.Node` in its own graphics layer: a new frame invalidates only that
  layer's drawing, never composition or layout.
- **Frame cache by sticker and size**: the first frame of each sticker at each size, LRU up to 8 MB of bitmaps, so a
  sticker that scrolls back, a paused one and every one waiting for a slot show at once. Whole animations are not
  cached in memory: a 72 dp panel sticker at 3 px/dp and 30 frames per second takes about 17 MB for 3 seconds, so eight
  would exceed the memory budget. Decoded animations stay in an LRU of 48.
- **Limits**: at most 8 animate at once (a semaphore; the others show their first frame and queue), at most 30 frames
  per second (every other frame of 60 fps stickers), only while drawn (a frame not drawn within 250 ms gives the slot
  back: the sticker left the screen) and while the window has the focus, and not at all with animations off.
- **WebM video stickers** keep their thumbnail: playing them would need a video decoder per sticker.

## Consequences

- About 76 KB more per APK, no new native code, no CI or NDK steps, no new network access (Lottie loads nothing by
  itself from TGS files, which carry no external assets).
- Rendering costs CPU on the background threads, at most 8 stickers at 30 fps; the cost per frame on a phone is not
  measured yet. If the conversation scroll benchmark (`docs/PERFORMANCE.md` §9) or battery tests
  show this is too much, rlottie can replace Lottie behind `StickerAnimationDecoder`, or frames can be cached on disk.
- Lottie may draw some stickers slightly differently from Telegram's rlottie; none were seen to fail, but only a few
  files were tried (Robolectric renders the test animation in `core:ui`'s tests and goldens).
- Lottie is now a dependency to keep up to date; its POM license (Apache 2.0) is listed by the licenses task.
