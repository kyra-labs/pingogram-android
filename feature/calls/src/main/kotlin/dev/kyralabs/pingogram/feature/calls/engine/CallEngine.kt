package dev.kyralabs.pingogram.feature.calls.engine

import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The media of one-to-one calls (`docs/adr/0015-call-media-engine.md`, video in
 * `docs/adr/0019-video-and-group-calls-scope.md`): what moves the audio and video once TDLib has signaled a call and
 * agreed on its key. `NtgCallsEngine` in production, a fake in tests; nothing else in the app touches the engine's
 * library.
 */
interface CallEngine {
    /**
     * What this engine speaks, sent to TDLib with every call it starts or answers. The first call loads the engine's
     * library, so it is asked for only when a call starts or is answered, never at app start.
     */
    suspend fun protocol(): CallProtocol

    /** The device's cameras, front ones first; empty without one. Loads the engine's library like [protocol]. */
    suspend fun cameras(): List<CallCamera>

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

    /**
     * Sends [camera]'s video, or no video for null. The other side's engine learns it at once, as it learns the mute.
     * Switching cameras is a new [camera]; NTgCalls opens it and closes the old one.
     */
    suspend fun setCamera(camera: CallCamera?)

    /** Whether the other side sends video, as its engine reports it; [RemoteVideo.Off] until it says otherwise. */
    val remoteVideo: StateFlow<RemoteVideo>

    /**
     * Where the frames go: [local] gets the camera's own (the self-view), [remote] the other side's, on the engine's
     * threads, never the main thread. Without a [remote] sink the other side's video is not received at all. Kept
     * across [setCamera].
     */
    suspend fun setVideoSinks(
        local: VideoFrameSink?,
        remote: VideoFrameSink?,
    )

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

/** A camera a call can send: [metadata] names it to the engine. */
data class CallCamera(
    val id: String,
    val isFront: Boolean,
    val metadata: String,
)

/** The other side's video. */
enum class RemoteVideo {
    Off,

    /** On, with frames coming. */
    Active,

    /** On but held back: the other side's app is in the background, or the connection is too weak for video. */
    Paused,
}

/** Takes video frames of a call, on the engine's threads. */
fun interface VideoFrameSink {
    /**
     * One frame: [i420] holds its Y plane ([width] × [height] bytes), then U and V (each half the width and half the
     * height, rounded up), packed without padding. [rotationDegrees] (0, 90, 180, 270) turns it upright. The array is
     * the sink's to keep.
     */
    fun onFrame(
        i420: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
    )
}
