package dev.kyralabs.pingogram.feature.media.ui.zoom

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

// The geometry of zooming a photo in its page, pure so it is unit-tested. The photo is fitted in the page at scale 1
// (ContentScale.Fit), centered; zooming scales it around its center by `scale` and moves it by `pan` (in pixels, from
// the centered position). Points are relative to the page's center.

/** The page ([container]) and the photo fitted in it at scale 1 ([content]), in pixels. */
internal data class ZoomBounds(
    val container: Size,
    val content: Size,
)

/** A zoom: the photo scaled by [scale] (1 fits it in the page) and moved by [pan] from the center. */
internal data class Zoom(
    val scale: Float,
    val pan: Offset,
) {
    companion object {
        val Fit = Zoom(MIN_SCALE, Offset.Zero)
    }
}

/** No smaller than fitted in the page. */
internal const val MIN_SCALE = 1f

/** Five times the fitted size: Telegram's largest photos (2560 px) show their pixels on a phone well before that. */
internal const val MAX_SCALE = 5f

/** How far a double tap zooms in at least; further if that does not yet fill the page. */
internal const val DOUBLE_TAP_SCALE = 2.5f

/**
 * An image of [width] × [height] (any unit; only the aspect ratio counts) fitted in [container], keeping its aspect
 * ratio; the whole container while the size is unknown.
 */
internal fun fittedSize(
    width: Int,
    height: Int,
    container: Size,
): Size {
    if (width <= 0 || height <= 0) return container
    val scale = min(container.width / width, container.height / height)
    return Size(width * scale, height * scale)
}

/** The farthest the photo can move from the center at [scale] on each axis: half of what overflows the page. */
internal fun maxPan(
    bounds: ZoomBounds,
    scale: Float,
): Offset =
    Offset(
        x = max(0f, (bounds.content.width * scale - bounds.container.width) / 2),
        y = max(0f, (bounds.content.height * scale - bounds.container.height) / 2),
    )

/** [pan] kept within [maxPan] at [scale], so the photo never leaves a gap at an edge it could fill. */
internal fun clampPan(
    pan: Offset,
    bounds: ZoomBounds,
    scale: Float,
): Offset {
    val max = maxPan(bounds, scale)
    return Offset(pan.x.coerceIn(-max.x, max.x), pan.y.coerceIn(-max.y, max.y))
}

/**
 * The pan that keeps the point of the photo under [focus] (relative to the page's center) where it is while the scale
 * goes from [from] to [to], starting at [pan].
 */
internal fun panAround(
    pan: Offset,
    focus: Offset,
    from: Float,
    to: Float,
): Offset {
    val ratio = to / from
    return focus * (1 - ratio) + pan * ratio
}

/** The scale at which the photo fills the whole page, cropping the sides that overflow it. */
internal fun fillScale(bounds: ZoomBounds): Float {
    val content = bounds.content
    if (content.width <= 0f || content.height <= 0f) return MIN_SCALE
    return max(bounds.container.width / content.width, bounds.container.height / content.height)
}

/**
 * Where a double tap at [tap] (relative to the page's center) leads from [zoom]: back to fitted when zoomed in; else
 * in to [DOUBLE_TAP_SCALE], or as far as filling the page needs, around the tapped point, within bounds.
 */
internal fun doubleTapTarget(
    bounds: ZoomBounds,
    zoom: Zoom,
    tap: Offset,
): Zoom {
    if (zoom.scale > MIN_SCALE) return Zoom.Fit
    val scale = max(DOUBLE_TAP_SCALE, fillScale(bounds)).coerceAtMost(MAX_SCALE)
    return Zoom(scale, clampPan(panAround(zoom.pan, tap, zoom.scale, scale), bounds, scale))
}

/**
 * One step of a pinch or a drag: [zoom] scaled by [scaleChange] around [centroid] (relative to the page's center),
 * within [MIN_SCALE] and [MAX_SCALE], then moved by [panChange], within bounds.
 */
internal fun transformed(
    zoom: Zoom,
    bounds: ZoomBounds,
    centroid: Offset,
    scaleChange: Float,
    panChange: Offset,
): Zoom {
    val scale = (zoom.scale * scaleChange).coerceIn(MIN_SCALE, MAX_SCALE)
    val pan = panAround(zoom.pan, centroid, zoom.scale, scale) + panChange
    return Zoom(scale, clampPan(pan, bounds, scale))
}

/**
 * [zoom] scaled by [step] (above 1 in, below 1 out) around the page's center, within bounds: zooming in and out from
 * accessibility services.
 */
internal fun steppedZoom(
    zoom: Zoom,
    bounds: ZoomBounds,
    step: Float,
): Zoom = transformed(zoom, bounds, centroid = Offset.Zero, scaleChange = step, panChange = Offset.Zero)
