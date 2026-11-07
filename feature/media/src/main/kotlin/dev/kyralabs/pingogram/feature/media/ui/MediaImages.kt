package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.image.largestImageKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import dev.kyralabs.pingogram.core.ui.image.BlurredMinithumbnail as CoreBlurredMinithumbnail

// The images of the viewer's pages: TDLib files loaded by Coil, and the blurred minithumbnail under them (core:ui's,
// shared with the conversation).

/**
 * TDLib file [fileId] as an image, decoded by Coil at [widthPx] × [heightPx] (`docs/PERFORMANCE.md` §4: explicit
 * sizes; the viewer's full sizes at most twice the window's larger side, [viewerDecodeSize]), cropped to fill its
 * box: the page fits the box to the media, so nothing is cut off there, and while the box moves between the bubble and
 * the page (the container transform) the photo uncrops as it grows. The fetcher downloads it at visible priority if it
 * is not on the device. Until it is decoded at that size, the largest copy of the file already in memory shows in its
 * place ([largestImageKey]): the bubble's photo, decoded at the bubble's size, so a photo the conversation showed is
 * there at once and only sharpens. With [fadeIn], it fades in on the motion scheme's default effects
 * spring once loaded from disk (Coil's own crossfade is a tween); it shows at once from Coil's memory cache, and
 * without [fadeIn]. With [fromMemory], a copy already in memory is shown as it is, at its size, without decoding the
 * file again. Decorative: the page describes it.
 */
@Composable
internal fun ViewerImage(
    fileId: Int,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
    fadeIn: Boolean = true,
    alpha: Animatable<Float, AnimationVector1D> = rememberViewerImageAlpha(fileId, fadeIn),
    fromMemory: Boolean = false,
    onShown: () -> Unit = {},
) {
    val context = LocalPlatformContext.current
    val request =
        remember(context, fileId, widthPx, heightPx, fromMemory) {
            val cached = SingletonImageLoader.get(context).memoryCache?.largestImageKey(fileId)
            ImageRequest
                .Builder(context)
                .data(TdlibFileImage(fileId))
                .size(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1))
                // What the conversation's bubble decoded, at its own size, shows until this size is decoded.
                .placeholderMemoryCacheKey(cached)
                .apply {
                    // That copy itself, rather than the same file decoded again at another size: the request's own
                    // size would miss the memory cache (docs/audit/performance-review-2026-11.md, M5).
                    if (fromMemory && cached != null) memoryCacheKey(cached)
                }.build()
        }
    val scope = rememberCoroutineScope()
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier.graphicsLayer { this.alpha = alpha.value },
        contentScale = ContentScale.Crop,
        onSuccess = { state ->
            val loaded = state.result.dataSource
            if (loaded == DataSource.DISK || loaded == DataSource.NETWORK) {
                scope.launch { alpha.animateTo(1f, fade) }
            } else {
                scope.launch(start = CoroutineStart.UNDISPATCHED) { alpha.snapTo(1f) }
            }
            onShown()
        },
    )
}

/**
 * The size to decode a full photo of [width] × [height] at in a window of [window]: its own, or scaled down so its
 * longer side is at most twice the window's longer side, which is as sharp as zooming shows it. Decoding a 2560 px
 * photo at its own size took 20 MB of memory and evicted the conversation's bitmaps from Coil's cache, which then
 * decoded again on the way back (`docs/audit/performance-review-2026-11.md`, M5).
 */
internal fun viewerDecodeSize(
    width: Int,
    height: Int,
    window: IntSize,
): IntSize {
    val limit = maxOf(window.width, window.height) * ZOOM_HEADROOM
    val longest = maxOf(width, height)
    if (limit <= 0 || longest <= limit) return IntSize(width, height)
    val scale = limit.toFloat() / longest
    return IntSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
}

/** How many times the window's size a full photo is decoded at, at most: the zoom it stays sharp to. */
private const val ZOOM_HEADROOM = 2

/**
 * How far [ViewerImage] of [fileId] has faded in: 0 until it loads, or 1 from the start without [fadeIn] (then what
 * shows while it loads, the bubble's copy, shows at full opacity too).
 */
@Composable
internal fun rememberViewerImageAlpha(
    fileId: Int,
    fadeIn: Boolean = true,
): Animatable<Float, AnimationVector1D> = remember(fileId) { Animatable(if (fadeIn) 0f else 1f) }

/** Whether the image fading in through [alpha] fully covers what is under it; read in the draw phase. */
internal fun coveredBy(alpha: Animatable<Float, AnimationVector1D>): () -> Boolean = { alpha.value >= 1f }

/**
 * What an item shows until its image is loaded, and instead of it while it is a hidden spoiler: the [minithumbnail]
 * blurred, cropped to fill its box; without one, a tonal placeholder in `surfaceContainerHigh`. Nothing is drawn while
 * [covered] (the image over it has faded in), as core:ui's [CoreBlurredMinithumbnail] says.
 */
@Composable
internal fun BlurredMinithumbnail(
    minithumbnail: Minithumbnail?,
    modifier: Modifier = Modifier,
    covered: () -> Boolean = { false },
) {
    CoreBlurredMinithumbnail(
        minithumbnail = minithumbnail,
        fallback = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
        covered = covered,
    )
}
