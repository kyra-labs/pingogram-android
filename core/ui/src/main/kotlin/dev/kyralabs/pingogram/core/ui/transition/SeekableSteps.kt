package dev.kyralabs.pingogram.core.ui.transition

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A flow's steps on screen as a seekable transition between their screens ([key] of each step), so the predictive
 * back gesture drives the step transition (`docs/DESIGN_SYSTEM.md` §5 and §5.1, Q-08) rather than the step changing
 * once the gesture ends.
 *
 * A step [current] reports animates in. While a back gesture runs (only while [enabled]), the transition seeks
 * towards the screen [backTo] leads to with the gesture's progress. Completed, it sends [onBack]: the caller then
 * reports that step and the transition plays the rest from where the gesture left it. Cancelled, the transition
 * returns to the current step on the motion scheme's default effects spring (no overshoot past the start); a new
 * gesture within that return takes over from where it is. Without a step to go back to, and with animations off
 * ([isReducedMotion]: the gesture previews nothing, Q-19), the gesture only sends [onBack] once it completes.
 *
 * Show the steps with `steps.transition.AnimatedContent { key -> … }`, each screen with [SeekableSteps.stepOf]. Read
 * [SeekableSteps.transition] once per composition: two transitions cannot share its state, so a screen that animates
 * several places with it keeps the one it read.
 */
@Composable
fun <S : Any, K : Any> rememberSeekableSteps(
    current: S,
    backTo: S?,
    key: (S) -> K,
    enabled: Boolean = backTo != null,
    onBack: () -> Unit,
): SeekableSteps<S, K> {
    val steps = remember { SeekableSteps(current, key) }
    val scope = rememberCoroutineScope()
    val returnSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val currentStep by rememberUpdatedState(current)
    val currentBackTo by rememberUpdatedState(backTo)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentKey by rememberUpdatedState(key)
    val reducedMotion by rememberUpdatedState(isReducedMotion())
    val shownKey = key(current)
    SideEffect { steps.shown(current) }
    LaunchedEffect(shownKey) { if (!steps.isSeeking) steps.seekable.animateTo(shownKey) }
    PredictiveBackHandler(enabled = enabled) { events ->
        // A return from a cancelled gesture still running: this gesture takes over from where it is.
        steps.cancelReturn()
        val from = currentKey(currentStep)
        val to = currentBackTo?.takeIf { currentKey(it) != from && !reducedMotion }
        if (to != null) {
            steps.startSeeking(to)
        } else if (steps.isSeeking) {
            // Caught within a return, with nothing to preview now: the step shows as it is.
            steps.seekable.snapTo(from)
            steps.isSeeking = false
        }
        try {
            events.collect { event -> if (to != null) steps.seekable.seekTo(event.progress, currentKey(to)) }
            steps.isSeeking = false
            currentOnBack()
        } catch (cancelled: CancellationException) {
            if (to != null) {
                steps.returning = scope.launch { steps.returnTo(from, returnSpec) { currentKey(currentStep) } }
            }
            throw cancelled
        }
    }
    return steps
}

/** The state behind [rememberSeekableSteps]: the [transition] and the step each screen in it shows. */
@Stable
class SeekableSteps<S : Any, K : Any> internal constructor(
    initial: S,
    private val key: (S) -> K,
) {
    val seekable = SeekableTransitionState(key(initial))

    val transition: Transition<K>
        @Composable get() = rememberTransition(seekable, label = "SeekableSteps")

    /** Whether a back gesture drives [seekable]; steps reported meanwhile wait for its end. */
    var isSeeking = false
        internal set

    /** The return after a cancelled gesture, while it runs. */
    internal var returning: Job? = null

    // The last step of each screen: a leaving screen keeps showing it, and the one back leads to shows its preview.
    // Read only while composing screens that are not the current one, whose step no longer changes.
    private val last = mutableMapOf<K, S>(key(initial) to initial)

    /** The step to show for [screen]: [current] for its own screen, otherwise the screen's last (or previewed) one. */
    fun stepOf(
        screen: K,
        current: S,
    ): S = if (screen == key(current)) current else last[screen] ?: current

    /** Stops the return after a cancelled gesture where it is, for a new gesture to seek from there. */
    internal fun cancelReturn() {
        returning?.cancel()
        returning = null
    }

    internal fun shown(step: S) {
        last[key(step)] = step
    }

    internal fun startSeeking(backTo: S) {
        last[key(backTo)] = backTo
        isSeeking = true
    }

    /**
     * After a cancelled gesture: seeks back to the start on [spec], then settles on [from], or on the step reported
     * meanwhile ([currentKey]).
     */
    internal suspend fun returnTo(
        from: K,
        spec: FiniteAnimationSpec<Float>,
        currentKey: () -> K,
    ) {
        // As Navigation does after a cancelled predictive back: a frame's callback cannot suspend, so each seek is
        // launched on its own.
        coroutineScope {
            animate(seekable.fraction, 0f, animationSpec = spec) { value, _ ->
                if (value > 0f) launch { seekable.seekTo(value) }
            }
        }
        seekable.snapTo(from)
        isSeeking = false
        val key = currentKey()
        if (key != from) seekable.animateTo(key)
    }
}
