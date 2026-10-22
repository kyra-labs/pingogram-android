package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.feature.calls.AudioRoute
import dev.kyralabs.pingogram.feature.calls.CallEndReason
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What the call screen shows. */
@Immutable
sealed interface CallUiState {
    /** The call is being set up (the first frames after "Call"): nothing to show yet. */
    data object Loading : CallUiState

    /** No call: the screen closes. */
    data object Closed : CallUiState

    /**
     * Before calling or answering: the microphone permission is missing. The screen says why it is needed, then asks
     * Android ([canAsk]) or, after a final refusal, offers the app's settings.
     */
    data class NeedsMicrophone(
        val person: CallPerson,
        val isAnswering: Boolean,
        val canAsk: Boolean,
    ) : CallUiState

    data class InCall(
        val person: CallPerson,
        val isOutgoing: Boolean,
        val stage: CallStage,
        /** "01:23" once connected. */
        val duration: String?,
        val emojis: ImmutableList<String> = persistentListOf(),
        val isMuted: Boolean = false,
        val isOnHold: Boolean = false,
        val route: AudioRoute? = null,
        val routes: ImmutableList<AudioRoute> = persistentListOf(),
    ) : CallUiState {
        /** The incoming call rings: answer or decline. */
        val isIncomingRinging: Boolean get() = !isOutgoing && stage == CallStage.Ringing

        /** Mute, the route and hold apply: the call is under way. */
        val hasControls: Boolean get() = !isIncomingRinging && stage !is CallStage.Ended
    }
}

/** The other person as the call screen shows them. */
@Immutable
data class CallPerson(
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
)

/** The call's stage in words; [CallStage.Ended] carries why. */
@Immutable
sealed interface CallStage {
    data object Requesting : CallStage

    /** Outgoing, before the other person's phone rings; incoming, it rings here. */
    data object Ringing : CallStage

    /** Outgoing: the other person's phone rings. */
    data object RingingThere : CallStage

    data object Connecting : CallStage

    data object Connected : CallStage

    data object Reconnecting : CallStage

    data class Ended(
        val reason: CallEndReason,
    ) : CallStage
}

/** What the user does on the call screen. */
sealed interface CallEvent {
    data object Answer : CallEvent

    data object HangUp : CallEvent

    data object ToggleMute : CallEvent

    data object ToggleHold : CallEvent

    data class SelectRoute(
        val route: AudioRoute,
    ) : CallEvent

    /** Android answered the permission request: [granted], and whether it would ask again ([canAskAgain]). */
    data class MicrophoneResult(
        val granted: Boolean,
        val canAskAgain: Boolean,
    ) : CallEvent

    /** "Not now" before the permission request: an outgoing call is not made, an incoming one is declined. */
    data object DismissMicrophone : CallEvent
}
