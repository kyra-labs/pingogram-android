package dev.kyralabs.pingogram.feature.media.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import dev.kyralabs.pingogram.core.ui.theme.predictiveBackScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * How far the viewer is on its way out (`docs/DESIGN_SYSTEM.md` §5): a predictive back gesture's [backProgress]
 * (0–1), and a swipe down's (or up's) [dragOffset] in pixels. Snapshot state, read in the draw phase by the content
 * ([followingDismiss]) and the background ([fraction]), so following a gesture never recomposes.
 *
 * Once a predictive back gesture completes, its scale moves from the draw phase into the layout ([leftAtScale]), so
 * the media's bounds are where it shows as the container transform takes it back into its bubble. A swipe's offset
 * needs nothing of the kind: the transform starts from the media's position, offset included.
 */
@Stable
internal class DismissState {
    var backProgress by mutableFloatStateOf(0f)
        private set

    var dragOffset by mutableFloatStateOf(0f)
        private set

    /**
     * The scale the content is laid out at ([layoutFollowingDismiss]): 1 until a completed predictive back gesture
     * leaves the viewer, then the scale the gesture left it at, which the draw phase no longer applies.
     */
    var leftAtScale by mutableFloatStateOf(1f)
        private set

    /** Drives [dragOffset] from a vertical `draggable`. */
    val draggableState = DraggableState { delta -> dragOffset += delta }

    private var settling: Job? = null

    /** How far towards gone, 0–1, for a page [height] pixels tall: the background fades out with it. */
    fun fraction(height: Float): Float {
        val dragged = if (height > 0f) abs(dragOffset) / (height * DRAG_FADE_HEIGHT_FRACTION) else 0f
        return maxOf(backProgress, dragged).coerceIn(0f, 1f)
    }

    /** A drag started: it takes over from a content springing back. */
    fun onDragStarted() {
        settling?.cancel()
    }

    /**
     * A drag ended with [velocity] (pixels per second) on a page [height] pixels tall: [onDismiss] if it went past a
     * [DISMISS_HEIGHT_FRACTION] of the page or was flung on that way, else the content springs back on [spec].
     */
    fun onDragStopped(
        velocity: Float,
        height: Float,
        spec: AnimationSpec<Float>,
        scope: CoroutineScope,
        onDismiss: () -> Unit,
    ) {
        val farEnough = abs(dragOffset) > height * DISMISS_HEIGHT_FRACTION
        val flung = abs(velocity) > height * FLING_HEIGHTS_PER_SECOND && sign(velocity) == sign(dragOffset)
        if (farEnough || flung) {
            onDismiss()
        } else {
            settling =
                scope.launch {
                    animate(dragOffset, 0f, initialVelocity = velocity, animationSpec = spec) { value, _ ->
                        dragOffset = value
                    }
                }
        }
    }

    fun followBack(progress: Float) {
        backProgress = progress
    }

    /** A predictive back gesture completed: the content stays at its scale, now through its layout. */
    fun leaveAtBackScale() {
        leftAtScale = predictiveBackScale(backProgress)
    }

    private companion object {
        /** A drag this far (of the page's height) or more closes the viewer. */
        const val DISMISS_HEIGHT_FRACTION = 0.15f

        /** A fling this fast (page heights per second) or faster closes it from anywhere. */
        const val FLING_HEIGHTS_PER_SECOND = 1.5f

        /** The background is gone once the content is dragged this far (of the page's height). */
        const val DRAG_FADE_HEIGHT_FRACTION = 0.5f
    }
}

@Composable
internal fun rememberDismissState(): DismissState = remember { DismissState() }

/**
 * The content following [state]: down to 90% of its size with a predictive back gesture, along Material's
 * back-progress curve ([predictiveBackScale]), and moved with a swipe; read in the draw phase. Once the gesture left
 * the viewer, its scale is the layout's ([layoutFollowingDismiss]).
 */
internal fun Modifier.followingDismiss(state: DismissState): Modifier =
    graphicsLayer {
        val scale = if (state.leftAtScale < 1f) 1f else predictiveBackScale(state.backProgress)
        scaleX = scale
        scaleY = scale
        translationY = state.dragOffset
    }

/**
 * The content laid out at [DismissState.leftAtScale] of the space it is given, centered: what [followingDismiss]
 * draws, as layout, so the media's bounds (and with them the start of the container transform) match what shows.
 * Measured anew once, when the gesture completes; unchanged before that.
 */
internal fun Modifier.layoutFollowingDismiss(state: DismissState): Modifier =
    layout { measurable, constraints ->
        val scale = state.leftAtScale
        if (scale >= 1f || !constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            val width = (constraints.maxWidth * scale).roundToInt()
            val height = (constraints.maxHeight * scale).roundToInt()
            val placeable = measurable.measure(Constraints.fixed(width, height))
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place((constraints.maxWidth - width) / 2, (constraints.maxHeight - height) / 2)
            }
        }
    }

/**
 * Leaves the viewer with system back while [enabled] (`docs/ARCHITECTURE.md`, Navigation: `PredictiveBackHandler` for
 * the media viewer): the gesture's progress goes into [state], and [onBack] runs once it completes, the content
 * staying as small as the gesture left it while the screen leaves ([DismissState.leaveAtBackScale]), and the media
 * shrinking from there into its bubble. A cancelled gesture springs back on the motion scheme's fast spatial spring.
 */
@Composable
internal fun PredictiveBackDismiss(
    state: DismissState,
    onBack: () -> Unit,
    enabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val settle = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val currentOnBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event -> state.followBack(event.progress) }
            state.leaveAtBackScale()
            currentOnBack()
        } catch (cancelled: CancellationException) {
            // A cancelled gesture, after which this coroutine can no longer animate.
            scope.launch {
                animate(state.backProgress, 0f, animationSpec = settle) { value, _ -> state.followBack(value) }
            }
            throw cancelled
        }
    }
}
