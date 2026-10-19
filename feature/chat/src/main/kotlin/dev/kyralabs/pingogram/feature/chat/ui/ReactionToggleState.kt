package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How chosen each reaction chip of the list looks, 0 (not chosen) to 1, for its colors (`docs/DESIGN_SYSTEM.md` §5:
 * reactions on the motion scheme's fast effects spring). A tapped chip ([toggle]) crossfades to its new state while
 * TDLib's update, which TDLib sends at once, catches up; every other chip shows its state as it is. One animation for
 * the whole list, outside the items, read in the draw phase (`docs/PERFORMANCE.md` §4).
 */
@Stable
internal class ReactionToggleState(
    private val scope: CoroutineScope,
    private val motionScheme: () -> MotionScheme,
) {
    private var toggling: Toggle? by mutableStateOf(null)
    private val progress = Animatable(1f)
    private var animation: Job? = null

    /**
     * How chosen [chip] of message [messageId] looks now. Reads snapshot state: call it in the draw phase, so the
     * crossfade only redraws.
     */
    fun chosenFraction(
        messageId: Long,
        chip: ReactionChip,
    ): Float {
        val toggle = toggling?.takeIf { it.messageId == messageId && it.label == chip.label }
        return when {
            toggle == null -> if (chip.isChosen) 1f else 0f
            toggle.toChosen -> progress.value
            else -> 1f - progress.value
        }
    }

    /** Crossfades [chip] of message [messageId] to the other state; replaces a crossfade still running. */
    fun toggle(
        messageId: Long,
        chip: ReactionChip,
    ) {
        animation?.cancel()
        animation =
            scope.launch {
                val toggle = Toggle(messageId, chip.label, toChosen = !chip.isChosen)
                toggling = toggle
                try {
                    progress.snapTo(0f)
                    progress.animateTo(1f, motionScheme().fastEffectsSpec())
                } finally {
                    // A crossfade that replaced this one owns the state now.
                    if (toggling === toggle) toggling = null
                }
            }
    }

    private class Toggle(
        val messageId: Long,
        val label: ReactionLabel,
        val toChosen: Boolean,
    )
}

@Composable
internal fun rememberReactionToggleState(): ReactionToggleState {
    val scope = rememberCoroutineScope()
    val motionScheme by rememberUpdatedState(MaterialTheme.motionScheme)
    return remember(scope) { ReactionToggleState(scope) { motionScheme } }
}

/**
 * Adds or takes back the user's reaction of a tapped chip of [bubble]: a toggle haptic, the chip's crossfade
 * ([ReactionToggleState]), and the event.
 */
@Composable
internal fun rememberReactionToggle(
    bubble: ChatItem.Bubble,
    toggles: ReactionToggleState,
    onEvent: (ChatEvent) -> Unit,
): (ReactionChip) -> Unit {
    val haptics = LocalHapticFeedback.current
    val messageId = bubble.reactionsMessageId
    return remember(messageId, toggles, onEvent, haptics) {
        { chip ->
            val haptic = if (chip.isChosen) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
            haptics.performHapticFeedback(haptic)
            toggles.toggle(messageId, chip)
            onEvent(ChatEvent.ToggleReaction(messageId, chip.label))
        }
    }
}
