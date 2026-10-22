package dev.kyralabs.pingogram.feature.calls.engine

import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The media of one-to-one calls (`docs/adr/0015-call-media-engine.md`): what moves the audio once TDLib has signaled a
 * call and agreed on its key. `NtgCallsEngine` in production, a fake in tests; nothing else in the app touches the
 * engine's library.
 */
interface CallEngine {
    /**
     * What this engine speaks, sent to TDLib with every call it starts or answers. The first call loads the engine's
     * library, so it is asked for only when a call starts or is answered, never at app start.
     */
    suspend fun protocol(): CallProtocol

    /**
     * Opens the microphone and the speaker for call [callId] and connects it with what TDLib agreed on. The session
     * runs until [CallSession.close]; it does not close itself when the connection is lost.
     */
    suspend fun connect(
        callId: Int,
        isOutgoing: Boolean,
        connection: CallConnection,
    ): CallSession
}

/** One connected call of a [CallEngine]. */
interface CallSession {
    val state: StateFlow<CallMediaState>

    /** Data for the other side's engine, to send through TDLib (`sendCallSignalingData`). */
    val signalingData: Flow<ByteArray>

    /** Takes data from the other side's engine (`updateNewCallSignalingData`). */
    suspend fun receiveSignalingData(data: ByteArray)

    /** Stops sending the microphone, or starts again. */
    suspend fun setMuted(muted: Boolean)

    /** Stops sending and playing while another call holds the line (Telecom's hold), or starts again. */
    suspend fun setOnHold(onHold: Boolean)

    /** Hangs up the media and frees the microphone and the speaker. Idempotent. */
    suspend fun close()
}

/** Where a [CallSession]'s connection is. */
enum class CallMediaState {
    /** Looking for a way to the other side: first, or again after the connection was lost (reconnecting). */
    Connecting,

    Connected,

    /** No way to the other side could be found, or it timed out; the call cannot go on. */
    Failed,

    Closed,
}
