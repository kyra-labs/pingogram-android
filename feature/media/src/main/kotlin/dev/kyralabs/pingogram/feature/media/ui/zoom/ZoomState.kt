package dev.kyralabs.pingogram.feature.media.ui.zoom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.splineBasedDecay
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toSize
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
 * spatial spring they are given, a double tap keeping the tapped point in place, and a second one while the first runs
 * carries its speed on; a gesture stops a running animation and takes over; a pan released while zoomed glides on
 * with its velocity until it slows or reaches the photo's edge ([fling]); a mouse's wheel with Ctrl zooms at once
 * ([transform]).
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

    /** The scale while an animation runs, so the next one carries its velocity on; out of date after a gesture. */
    private val scale = Animatable(MIN_SCALE)
    private var scaleIsCurrent = false

    /** A gesture's step: scaled by [scaleChange] around [centroid] (relative to the center), moved by [panChange]. */
    fun transform(
        centroid: Offset,
        scaleChange: Float,
        panChange: Offset,
    ) {
        val bounds = bounds ?: return
        stopAnimation()
        zoom = transformed(zoom, bounds, centroid, scaleChange, panChange)
    }

    /**
     * A pan released at [velocity] (pixels per second) while zoomed: the photo glides on along [decay], within its
     * bounds, until it slows down or reaches an edge in both directions. A gesture takes over.
     */
    fun fling(
        velocity: Velocity,
        decay: DecayAnimationSpec<Offset>,
        scope: CoroutineScope,
    ) {
        val bounds = bounds ?: return
        if (!isZoomed || velocity == Velocity.Zero) return
        stopAnimation()
        animation =
            scope.launch {
                var last = Offset.Zero
                val glide = AnimationState(Offset.VectorConverter, Offset.Zero, Offset(velocity.x, velocity.y))
                glide.animateDecay(decay) {
                    val moved = transformed(zoom, bounds, Offset.Zero, scaleChange = 1f, panChange = value - last)
                    last = value
                    if (moved == zoom) cancelAnimation() else zoom = moved
                }
            }
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
        stopAnimation()
        zoom = Zoom.Fit
    }

    /** Stops a running animation; the next one starts from the zoom as it is then. */
    private fun stopAnimation() {
        animation?.cancel()
        scaleIsCurrent = false
    }

    /**
     * Animates to [target], the scale on [spec] and the pan following it so that [focus] stays in place, eased into
     * [target]'s pan, which bounds may have moved. Called while another such animation runs, the scale goes on from
     * where it is with its velocity, retargeted rather than restarted.
     */
    private fun animateTo(
        target: Zoom,
        focus: Offset,
        spec: AnimationSpec<Float>,
        scope: CoroutineScope,
    ) {
        val carriesOn = scaleIsCurrent
        animation?.cancel()
        val start = zoom
        val anchoredEnd = panAround(start.pan, focus, start.scale, target.scale)
        val span = target.scale - start.scale
        scaleIsCurrent = true
        animation =
            scope.launch {
                if (!carriesOn) scale.snapTo(start.scale)
                if (span == 0f) {
                    // Only the pan moves, as when bounds moved it: no scale to follow.
                    animate(0f, 1f, animationSpec = spec) { fraction, _ ->
                        zoom = Zoom(start.scale, start.pan + (target.pan - start.pan) * fraction)
                    }
                } else {
                    scale.animateTo(target.scale, spec) {
                        val fraction = (value - start.scale) / span
                        val anchored = panAround(start.pan, focus, start.scale, value)
                        zoom = Zoom(value, anchored + (target.pan - anchoredEnd) * fraction)
                    }
                }
            }
    }
}

/**
 * Pinch and pan for [state]: two fingers zoom and pan the photo; one finger pans it while zoomed, and released, the
 * photo glides on with the finger's velocity ([ZoomState.fling], in [scope]). Movements it handles are consumed, so the
 * pager and the swipe to close leave them alone; one finger on a fitted photo is left to them. A gesture another
 * handler consumed first (the pager turning the page) is not followed.
 */
internal suspend fun PointerInputScope.detectZoomGestures(
    state: ZoomState,
    scope: CoroutineScope,
) {
    // A fling's decay, as scrollables glide: the one decay Compose has for a finger's release.
    @Suppress("PingogramMotionDefault")
    val decay = splineBasedDecay<Offset>(this)
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        var panned = false
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
            // Only a one-finger pan glides on: a pinch's last finger lifting is no fling.
            if (fingers == 1 && state.isZoomed) {
                event.changes.firstOrNull { it.pressed }?.let { tracker.addPointerInputChange(it) }
                panned = true
            } else if (fingers >= 2) {
                tracker.resetTracking()
                panned = false
            }
        } while (event.changes.any { it.pressed })
        if (panned) state.fling(tracker.calculateVelocity(), decay, scope)
    }
}
