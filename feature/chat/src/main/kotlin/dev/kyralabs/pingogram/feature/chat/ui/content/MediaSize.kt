package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.min
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat

/**
 * The size a photo or video of [width] × [height] (any unit; only the aspect ratio counts) is shown at in a message:
 * the full [maxWidth], as tall as its aspect ratio makes it, within [minSize] (or [maxHeight] if smaller) and
 * [maxHeight]. The image is cropped to fill it, so a tall photo shows its middle and a panorama a strip, rather than
 * a narrow photo beside empty room. Without a known size it is as tall as wide, within [maxHeight].
 */
internal fun mediaDisplaySize(
    width: Int,
    height: Int,
    maxWidth: Dp,
    maxHeight: Dp,
    minSize: Dp,
): DpSize {
    if (width <= 0 || height <= 0) return DpSize(maxWidth, min(maxWidth, maxHeight))
    val fullHeight = Dp(maxWidth.value * height / width)
    return DpSize(maxWidth, fullHeight.coerceIn(min(minSize, maxHeight), maxHeight))
}

/**
 * [width] × [height] (any unit) scaled to fit a square of [side], keeping the aspect ratio; the whole square without a
 * known size. Stickers keep their shape this way.
 */
internal fun fitInSquare(
    width: Int,
    height: Int,
    side: Dp,
): DpSize {
    if (width <= 0 || height <= 0) return DpSize(side, side)
    val scale = side.value / maxOf(width, height)
    return DpSize(Dp(width * scale), Dp(height * scale))
}

/**
 * The size of [sizes] to load for a box of [widthPx] × [heightPx] that crops the photo to fill it
 * (`docs/PERFORMANCE.md` §7): the smallest one that covers the box without being scaled up, or the largest one if none
 * does. Sizes whose dimensions TDLib does not know are only taken when no size has them. Null without sizes.
 */
internal fun selectPhotoSize(
    sizes: List<PhotoSize>,
    widthPx: Int,
    heightPx: Int,
): PhotoSize? {
    val known = sizes.filter { it.width > 0 && it.height > 0 }
    if (known.isEmpty()) return sizes.lastOrNull()
    // Cropping scales by max(widthPx / width, heightPx / height): at most 1 exactly when both sides cover the box.
    return known
        .filter { it.width >= widthPx && it.height >= heightPx }
        .minByOrNull { it.width.toLong() * it.height }
        ?: known.maxBy { it.width.toLong() * it.height }
}

/** The largest of [sizes], whose dimensions give the photo's aspect ratio; null without sizes. */
internal fun largestPhotoSize(sizes: List<PhotoSize>): PhotoSize? = sizes.maxByOrNull { it.width.toLong() * it.height }

/** This thumbnail if Coil can show it as a picture; MPEG4, WebM and TGS thumbnails are videos and animations. */
internal fun Thumbnail.asPicture(): Thumbnail? =
    when (format) {
        ThumbnailFormat.Jpeg, ThumbnailFormat.Png, ThumbnailFormat.Webp, ThumbnailFormat.Gif -> this
        ThumbnailFormat.Mpeg4, ThumbnailFormat.Webm, ThumbnailFormat.Tgs -> null
    }
