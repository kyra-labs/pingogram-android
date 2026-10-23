package dev.kyralabs.pingogram.feature.media.ui.zoom

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The zoom of the photo shown in the viewer: pinch to zoom, drag to pan while zoomed, double tap to zoom in and out
 * (`REQUIREMENTS.md` FR-C10), built on Compose's pointer input alone, with the geometry of `ZoomMath`. Snapshot state,
 * which the page reads in the draw phase ([zoom], through `graphicsLayer`), so following the fingers never
 * recomposes; only [isZoomed] turning on or off does, to lock the pager and the swipe to close.
 *
 * The page sets [bounds] from its layout. Double taps, accessibility actions and the keyboard's keys animate on the
 * spatial spring they are given, a double tap keeping the tapped point in place; a gesture stops a running animation
 * and takes over; a mouse's wheel with Ctrl zooms at once ([transform]).
 */
@Stable
internal class ZoomState {
    /** The current zoom; [Zoom.Fit] until the user zooms. */
    var zoom by mutableStateOf(Zoom.Fit)
        private set

    /** The page and the fitted photo, from the page's layout; null until it is laid out. */
    var bounds: ZoomBounds? = null

    /** Zoomed in beyond the fitted photo: drags pan it rather than turn the page or close the viewer. */
    val isZoomed: Boolean get() = zoom.scale > MIN_SCALE

    private var animation: Job? = null

    /** A gesture's step: scaled by [scaleChange] around [centroid] (relative to the center), moved by [panChange]. */
    fun transform(
        centroid: Offset,
        scaleChange: Float,
        panChange: Offset,
    ) {
        val bounds = bounds ?: return
        animation?.cancel()
        zoom = transformed(zoom, bounds, centroid, scaleChange, panChange)
    }

    /** A double tap at [tap] (relative to the center): in around it when fitted, back to fitted when zoomed. */
    fun toggle(
        tap: Offset,
        spec: AnimationSpec<Float>,
        scope: CoroutineScope,
    ) {
        val bounds = bounds ?: return
        animateTo(doubleTapTarget(bounds, zoom, tap), focus = tap, spec, scope)
    }

    /** Zooms by [step] around the center (above 1 in, below 1 out), as accessibility services ask. */
    fun step(
        step: Float,
        spec: AnimationSpec<Float>,
        scope: CoroutineScope,
    ) {
        val bounds = bounds ?: return
        animateTo(steppedZoom(zoom, bounds, step), focus = Offset.Zero, spec, scope)
    }

    /** Back to fitted on [spec], as the keyboard's 0 asks. */
    fun fit(
        spec: AnimationSpec<Float>,
        scope: CoroutineScope,
    ) {
        if (zoom != Zoom.Fit) animateTo(Zoom.Fit, focus = Offset.Zero, spec, scope)
    }

    /** Back to fitted at once, e.g. when the page is turned away. */
    fun reset() {
        animation?.cancel()
        zoom = Zoom.Fit
    }

    /**
     * Animates to [target], the scale on [spec] and the pan following it so that [focus] stays in place, eased into
     * [target]'s pan, which bounds may have moved.
     */
    private fun animateTo(
        target: Zoom,
        focus: Offset,
        spec: AnimationSpec<Float>,
        scope: CoroutineScope,
    ) {
        animation?.cancel()
        val start = zoom
        val anchoredEnd = panAround(start.pan, focus, start.scale, target.scale)
        animation =
            scope.launch {
                animate(0f, 1f, animationSpec = spec) { fraction, _ ->
                    val scale = lerp(start.scale, target.scale, fraction)
                    val anchored = panAround(start.pan, focus, start.scale, scale)
                    zoom = Zoom(scale, anchored + (target.pan - anchoredEnd) * fraction)
                }
            }
    }
}

/**
 * Pinch and pan for [state]: two fingers zoom and pan the photo; one finger pans it while zoomed. Movements it handles
 * are consumed, so the pager and the swipe to close leave them alone; one finger on a fitted photo is left to them.
 * A gesture another handler consumed first (the pager turning the page) is not followed.
 */
internal suspend fun PointerInputScope.detectZoomGestures(state: ZoomState) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.any { it.isConsumed }) break
            val fingers = event.changes.count { it.pressed }
            // None left pressed: the gesture ends, with no centroid to zoom around.
            if (fingers >= 2 || (fingers == 1 && state.isZoomed)) {
                val center = size.toSize().center
                state.transform(event.calculateCentroid() - center, event.calculateZoom(), event.calculatePan())
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
