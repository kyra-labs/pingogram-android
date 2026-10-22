package dev.kyralabs.pingogram.feature.calls

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The call the user is in, as [CallManager] runs it: what the call screen, the notifications and the foreground service
 * show. Pingogram runs one call at a time; another incoming call meanwhile is declined.
 */
@Immutable
data class ActiveCall(
    /** The other person. */
    val userId: Long,
    /** TDLib's id of the call; null while Telegram has not created an outgoing one yet. */
    val callId: Int?,
    val isOutgoing: Boolean,
    val phase: CallPhase,
    /** The four emojis of the key, to compare with the other person's screen; empty until the key is agreed. */
    val emojis: ImmutableList<String> = persistentListOf(),
    val isMuted: Boolean = false,
    /** Held: by the user, or by Android while another call (a phone call) takes the line. */
    val isOnHold: Boolean = false,
    val audio: CallAudio = CallAudio(),
    /** When the call first connected, on the elapsed-realtime clock in milliseconds; null before. */
    val connectedAtMillis: Long? = null,
    /**
     * The other person called with video, or turned it on: Pingogram has no video calls yet and talks with audio only
     * (`docs/adr/0019-video-and-group-calls-scope.md`).
     */
    val videoOffered: Boolean = false,
)

/** Where an [ActiveCall] is, as the user sees it. */
@Immutable
sealed interface CallPhase {
    /** Outgoing: asking Telegram to create the call. */
    data object Requesting : CallPhase

    /**
     * Ringing: an outgoing call waits for the other person to answer ([reachedDevice] once their phone rings), an
     * incoming one for the user.
     */
    data class Ringing(
        val reachedDevice: Boolean,
    ) : CallPhase

    /** Answered: agreeing on the key, then connecting the media for the first time. */
    data object Connecting : CallPhase

    data object Connected : CallPhase

    /** Connected before, the connection was lost, and the engine is looking for a new one. */
    data object Reconnecting : CallPhase

    /** Over; the screen shows why for a moment, then closes. */
    data class Ended(
        val reason: CallEndReason,
    ) : CallPhase
}

/** Why an [ActiveCall] ended. */
@Immutable
sealed interface CallEndReason {
    /** Hung up, by either side, after it connected. */
    data object HungUp : CallEndReason

    /** Not answered: the other person did not pick up, or the user did not, or the caller gave up. */
    data object Missed : CallEndReason

    /** Declined by the person called, or they were busy. */
    data object Declined : CallEndReason

    /** The connection was lost and could not be found again. */
    data object Disconnected : CallEndReason

    /** Could not be made, e.g. the other person's privacy settings refuse calls ([error] says why). */
    data class Failed(
        val error: DomainError,
    ) : CallEndReason
}

/** Where the call's sound goes, and where else it could go. */
@Immutable
data class CallAudio(
    val route: AudioRoute? = null,
    val available: ImmutableList<AudioRoute> = persistentListOf(),
)

/** An audio output of a call: [id] names it to the system (Telecom's endpoint id). */
@Immutable
data class AudioRoute(
    val id: String,
    val type: AudioRouteType,
    /** A Bluetooth device's name, or empty. */
    val name: String = "",
)

enum class AudioRouteType {
    Earpiece,
    Speaker,
    WiredHeadset,
    Bluetooth,
}
