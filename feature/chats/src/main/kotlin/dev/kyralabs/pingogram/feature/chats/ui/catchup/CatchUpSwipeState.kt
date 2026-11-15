package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.transition.isReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * A card's swipe (`docs/DESIGN_SYSTEM.md` §6 "Catch-up", owner decision 2026-11-15): toward the end (right; left in
 * right-to-left layouts) marks the chat as read, toward the start skips it. [offset] is how far the card is from its
 * place, toward the end, in pixels, read in the draw phase only, so following the finger recomposes nothing.
 *
 * - Past a third of the card's width the swipe is armed, with a `TextHandleMove` haptic (and again as it comes back).
 * - Released armed, or flung so it would land there, the card flies off on the default spatial spring from the
 *   finger's speed and then marks or skips ([onRead], [onSkip]); otherwise it springs back. With reduced motion it does
 *   not fly: the action happens at once, and the stage's crossfade shows the next card.
 */
@Stable
internal class CatchUpSwipeState(
    private val haptics: HapticFeedback,
    private val scope: CoroutineScope,
    private val spec: FiniteAnimationSpec<Float>,
    private val reducedMotion: () -> Boolean,
    private val onRead: () -> Unit,
    private val onSkip: () -> Unit,
) {
    var offset by mutableFloatStateOf(0f)
        private set

    /** The card's width, measured as it is laid out. */
    var width by mutableFloatStateOf(0f)

    /** How far toward an action the swipe is, -1 (skip) to 1 (read). */
    val progress: Float get() = if (width > 0f) (offset / threshold).coerceIn(-1f, 1f) else 0f

    private val threshold get() = width / THRESHOLD_DIVISOR
    private var armed = false
    private var settle: Job? = null

    val draggable =
        DraggableState { delta ->
            offset += delta
            arm(abs(offset) >= threshold && threshold > 0f)
        }

    val onStarted: suspend CoroutineScope.(androidx.compose.ui.geometry.Offset) -> Unit = { settle?.cancel() }

    val onStopped: suspend CoroutineScope.(Float) -> Unit = { velocity -> release(velocity) }

    private fun release(velocity: Float) {
        // Where a fling would land, as a card's settle would carry it.
        val landing = offset + velocity * FLING_SECONDS
        val toward = if (abs(landing) >= threshold && threshold > 0f) sign(landing) else 0f
        arm(false)
        if (toward == 0f) {
            animateTo(0f, velocity) {}
            return
        }
        val act = if (toward > 0f) onRead else onSkip
        if (reducedMotion()) {
            offset = 0f
            act()
        } else {
            animateTo(toward * width * FLY_FACTOR, velocity, act)
        }
    }

    private fun animateTo(
        target: Float,
        velocity: Float,
        then: () -> Unit,
    ) {
        val from = offset
        settle =
            scope.launch {
                animate(from, target, initialVelocity = velocity, animationSpec = spec) { value, _ -> offset = value }
                then()
            }
    }

    private fun arm(now: Boolean) {
        if (now == armed) return
        armed = now
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    private companion object {
        const val THRESHOLD_DIVISOR = 3f
        const val FLY_FACTOR = 1.5f
        const val FLING_SECONDS = 0.15f
    }
}

/** The swipe of the card of chat [chatId], new for each card. */
@Composable
internal fun rememberCatchUpSwipe(
    chatId: Long,
    onRead: () -> Unit,
    onSkip: () -> Unit,
): CatchUpSwipeState {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val reducedMotion by rememberUpdatedState(isReducedMotion())
    val read by rememberUpdatedState(onRead)
    val skip by rememberUpdatedState(onSkip)
    return remember(chatId, haptics, scope, spec) {
        CatchUpSwipeState(haptics, scope, spec, { reducedMotion }, { read() }, { skip() })
    }
}

/**
 * The card following [state]'s swipe, in its graphics layer: moved by the offset (mirrored right to left) and tilted
 * a little with it.
 */
@Composable
internal fun Modifier.catchUpSwipe(state: CatchUpSwipeState): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return this
        .onSizeChanged { state.width = it.width.toFloat() }
        .draggable(
            state = state.draggable,
            orientation = Orientation.Horizontal,
            reverseDirection = rtl,
            onDragStarted = state.onStarted,
            onDragStopped = state.onStopped,
        ).graphicsLayer {
            val direction = if (rtl) -1f else 1f
            translationX = state.offset * direction
            rotationZ = state.progress * MAX_TILT_DEGREES * direction
        }
}

private const val MAX_TILT_DEGREES = 6f
