package dev.kyralabs.pingogram.feature.auth.ui

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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/** A login step by its screen: the transition switches screens, not the states of one screen. */
internal typealias StepKey = KClass<out AuthStepUiState>

/**
 * The login steps on screen, as a seekable transition between their screens ([StepKey]), so the predictive back
 * gesture can drive the shared axis Y transition (`docs/DESIGN_SYSTEM.md` §5) instead of only shrinking the step.
 *
 * A step the view model reports animates in. While a back gesture runs, the transition seeks towards the screen back
 * leads to ([AuthUiState.backStep]) with the gesture's progress. Completed, it sends [onBack]: the view model then
 * reports that screen and the transition plays the rest from where the gesture left it. Cancelled, the transition
 * returns to the current step on the effects spring of `MaterialTheme.motionScheme` (no overshoot past the start).
 * Without a known back screen the gesture only sends [onBack] once it completes.
 */
@Composable
internal fun rememberAuthStepTransition(
    state: AuthUiState,
    onBack: () -> Unit,
): AuthStepTransition {
    val steps = remember { AuthStepTransition(state.step) }
    val scope = rememberCoroutineScope()
    val returnSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val currentState by rememberUpdatedState(state)
    val currentOnBack by rememberUpdatedState(onBack)
    val key = state.step::class
    SideEffect { steps.shown(state.step) }
    LaunchedEffect(key) { if (!steps.isSeeking) steps.seekable.animateTo(key) }
    PredictiveBackHandler(enabled = state.canGoBack) { events ->
        val from = currentState.step::class
        val to = currentState.backStep?.takeIf { it::class != from }
        if (to != null) steps.startSeeking(to)
        try {
            events.collect { event -> if (to != null) steps.seekable.seekTo(event.progress, to::class) }
            steps.isSeeking = false
            currentOnBack()
        } catch (cancelled: CancellationException) {
            if (to != null) scope.launch { steps.returnTo(from, returnSpec) { currentState.step::class } }
            throw cancelled
        }
    }
    return steps
}

/** The state behind [rememberAuthStepTransition]: the [transition] and the state each screen in it shows. */
@Stable
internal class AuthStepTransition(
    initial: AuthStepUiState,
) {
    val seekable = SeekableTransitionState<StepKey>(initial::class)

    val transition: Transition<StepKey>
        @Composable get() = rememberTransition(seekable, label = "AuthStep")

    /** Whether a back gesture drives [seekable]; steps the view model reports meanwhile wait for its end. */
    var isSeeking = false

    // The last state of each screen: a leaving screen keeps showing it, and the one back leads to shows its preview.
    // Read only while composing screens that are not the current one, whose state no longer changes.
    private val last = mutableMapOf<StepKey, AuthStepUiState>(initial::class to initial)

    /** The state to show for [key]: [current] for its own screen, otherwise the screen's last (or previewed) state. */
    fun stepOf(
        key: StepKey,
        current: AuthStepUiState,
    ): AuthStepUiState = if (key == current::class) current else last[key] ?: current

    fun shown(step: AuthStepUiState) {
        last[step::class] = step
    }

    fun startSeeking(backStep: AuthStepUiState) {
        last[backStep::class] = backStep
        isSeeking = true
    }

    /**
     * After a cancelled gesture: seeks back to the start on [spec], then settles on [from], or on the step the view
     * model reported meanwhile ([currentKey]).
     */
    suspend fun returnTo(
        from: StepKey,
        spec: FiniteAnimationSpec<Float>,
        currentKey: () -> StepKey,
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
