package dev.kyralabs.pingogram.feature.calls.system

import dev.kyralabs.pingogram.feature.calls.AudioRoute
import dev.kyralabs.pingogram.feature.calls.CallAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Android's view of a call (`docs/adr/0018-calls-and-telecom.md`): Telecom as a self-managed calling app, so the
 * system's UI, Bluetooth headsets, cars and watches know of the call, route its audio and can answer or end it; or,
 * where the device has no Telecom, the audio mode and route set directly. [TelecomCallSystem] in production, a fake
 * in tests.
 */
interface CallSystem {
    /**
     * Tells the system about a call, in [scope], which owns it until [SystemCall.end]. What the system asks for comes
     * through [listener], on [scope]'s dispatcher.
     */
    fun add(
        scope: CoroutineScope,
        request: SystemCallRequest,
        listener: SystemCallListener,
    ): SystemCall
}

data class SystemCallRequest(
    /** The other person's name, which the system's UI, a car or a watch shows. */
    val displayName: String,
    /** The other person, as an address the system can tell apart from others: `pingogram:user/<id>`. */
    val userId: Long,
    val isOutgoing: Boolean,
)

/** What the system asks of a call: someone answered or ended it elsewhere, or another call holds the line. */
interface SystemCallListener {
    /** Answered from outside the app: a Bluetooth headset, a car, a watch. */
    fun onAnswer()

    /** Ended from outside the app, or by the system. */
    fun onDisconnect()

    /** Another call took the line ([onHold] true), or gave it back. */
    fun onHold(onHold: Boolean)
}

/** One call as the system knows it. */
interface SystemCall {
    /** Where its sound goes, and the outputs the system offers. */
    val audio: StateFlow<CallAudio>

    /** The system's microphone mute, which a headset's button or the system UI may change. */
    val isMuted: StateFlow<Boolean>

    /** The user answered in the app. */
    fun answer()

    /** The call is connected and holds the line. */
    fun setActive()

    /** The user put the call on hold. */
    fun setInactive()

    fun selectRoute(route: AudioRoute)

    /** The call is over, for [cause]; the system forgets it. Idempotent. */
    fun end(cause: SystemEndCause)
}

enum class SystemEndCause {
    /** Ended by the user here. */
    Local,

    /** Ended by the other person. */
    Remote,

    /** The other person declined, or the user did. */
    Rejected,

    /** Nobody answered. */
    Missed,

    /** The connection failed. */
    Error,
}
