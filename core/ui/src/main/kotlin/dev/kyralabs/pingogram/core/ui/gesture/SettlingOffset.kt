package dev.kyralabs.pingogram.core.ui.gesture

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * An offset that a finger drags and a spring settles (`docs/plans/motion-expressive-plan.md` §3.4): a drag cancels a
 * settle that is still running, so the finger takes over where the content is, and a settle starts at the velocity
 * the release gives it, so a flick carries on into the spring (`docs/DESIGN_SYSTEM.md` §5).
 *
 * [value] is snapshot state: read it in the layout or draw phase (`Modifier.offset { }`, `graphicsLayer { }`), so
 * dragging and settling recompose nothing (`docs/PERFORMANCE.md` §4). The spring runs in the scope given to [settle],
 * whose motion duration scale it follows: with animations off it is at its target in the next frame.
 */
@Stable
class SettlingOffset(
    initial: Float = 0f,
) {
    private var current by mutableFloatStateOf(initial)

    /** The offset, in pixels or whatever unit the caller drags in. */
    val value: Float get() = current

    /** Whether a [settle] is running. */
    var isSettling by mutableStateOf(false)
        private set

    private var settling: Job? = null

    /** Moves by [delta], kept within [range] if given. Cancels a running settle. */
    fun dragBy(
        delta: Float,
        range: ClosedFloatingPointRange<Float>? = null,
    ) {
        cancelSettle()
        val moved = current + delta
        current = range?.let { moved.coerceIn(it) } ?: moved
    }

    /** Sets the offset to [value] at once. Cancels a running settle. */
    fun snapTo(value: Float) {
        cancelSettle()
        current = value
    }

    /**
     * Springs to [target] on [spec], starting at [velocity] (units per second, the release's), in [scope]. [onSettled]
     * runs once the spring has ended, only if nothing (a drag, a snap, another settle) cancelled it first.
     */
    fun settle(
        scope: CoroutineScope,
        target: Float,
        spec: AnimationSpec<Float>,
        velocity: Float = 0f,
        onSettled: () -> Unit = {},
    ): Job {
        cancelSettle()
        isSettling = true
        val job =
            scope.launch {
                animate(current, target, initialVelocity = velocity, animationSpec = spec) { value, _ ->
                    current = value
                }
                current = target
                isSettling = false
                settling = null
                onSettled()
            }
        settling = job
        return job
    }

    private fun cancelSettle() {
        settling?.cancel()
        settling = null
        isSettling = false
    }
}
