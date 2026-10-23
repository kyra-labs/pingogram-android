package dev.kyralabs.pingogram.feature.calls.engine

import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The media of calls (`docs/adr/0015-call-media-engine.md`, video in `docs/adr/0019-video-and-group-calls-scope.md`,
 * voice chats and live streams in `docs/adr/0023-group-voice-chats-and-live-streams.md`): what moves the audio and
 * video once TDLib has signaled a call and agreed on its key, or joined a voice chat. `NtgCallsEngine` in production,
 * a fake in tests; nothing else in the app touches the engine's library.
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

    /**
     * Opens the microphone and the speaker for voice chat [groupCallId] and makes its join payload, which TDLib's
     * `joinVideoChat` takes (`docs/adr/0023-group-voice-chats-and-live-streams.md`). The microphone starts [muted] or
     * not. Loads the engine's library like [protocol]. The session runs until [GroupCallSession.close].
     */
    suspend fun createGroupCall(
        groupCallId: Int,
        muted: Boolean,
    ): GroupCallSession
}

/** One voice chat or live stream of a [CallEngine]: created, then [connect]ed with what Telegram answered. */
interface GroupCallSession {
    /** The join payload for `joinVideoChat` (JSON), and the microphone's audio source (SSRC) in it. */
    val joinPayload: String
    val audioSourceId: Int

    val state: StateFlow<CallMediaState>

    /**
     * What the call is, once connected: a voice chat ([GroupCallMode.Rtc]) or a stream received in segments
     * ([GroupCallMode.Stream]); null before. Telegram may turn a voice chat into a stream.
     */
    val mode: StateFlow<GroupCallMode?>

    /**
     * A live stream's requests, while the call is a stream: the engine asks for the stream's current time, then for
     * each one-second segment, which [sendTime] and [sendSegment] answer. Hot; without a collector they wait.
     */
    val streamRequests: Flow<StreamRequest>

    /** Connects with the call's parameters (JSON) from `joinVideoChat`. */
    suspend fun connect(params: String)

    /** Stops sending the microphone, or starts again. */
    suspend fun setMuted(muted: Boolean)

    /** Stops sending and playing while another call holds the line (Telecom's hold), or starts again. */
    suspend fun setOnHold(onHold: Boolean)

    /** Answers [StreamRequest.Time]: the stream's newest time (ms), or 0 when it has none yet (asked again later). */
    suspend fun sendTime(timeMs: Long)

    /** Answers [request] with its segment's [data], or with why there is none. */
    suspend fun sendSegment(
        request: StreamRequest.Segment,
        result: SegmentResult,
    )

    /** Where a live stream's video goes, on the engine's threads; null to stop receiving it. */
    suspend fun setVideoSink(sink: VideoFrameSink?)

    /** Leaves the media and frees the microphone and the speaker. Idempotent. */
    suspend fun close()
}

/** How a group call reaches the device. */
enum class GroupCallMode {
    /** A voice chat: everyone sends and receives through Telegram's servers. */
    Rtc,

    /** A large call or a live stream: received as segments Telegram recorded, nothing sent. */
    Stream,
}

/** What a live stream asks for (NTgCalls' `onRequestBroadcastTimestamp` and `onRequestBroadcastPart`). */
sealed interface StreamRequest {
    data object Time : StreamRequest

    /**
     * Segment [segmentId], part [partId], which starts at [timeMs] on [channelId] (0: the audio) in [quality] (null for
     * the audio).
     */
    data class Segment(
        val segmentId: Long,
        val partId: Int,
        val timeMs: Long,
        val channelId: Int,
        val quality: StreamQuality?,
    ) : StreamRequest
}

/** A live stream's video quality, as the engine asks for it. */
enum class StreamQuality { Thumbnail, Medium, Full }

/** The answer to a [StreamRequest.Segment]. */
sealed interface SegmentResult {
    class Data(
        val bytes: ByteArray,
    ) : SegmentResult

    /** Telegram has not recorded it yet: the engine asks again shortly. */
    data object NotReady : SegmentResult

    /** The time is too far off: the engine asks for the stream's time again. */
    data object ResyncNeeded : SegmentResult
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
     * Sends [camera]'s video at [quality], or no video for null. The other side's engine learns it at once, as it
     * learns the mute. Switching cameras, or the quality, opens the camera again; NTgCalls closes the old one.
     */
    suspend fun setCamera(
        camera: CallCamera?,
        quality: VideoQuality = VideoQuality.High,
    )

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

/**
 * The most the camera sends: its size and frame rate. WebRTC lowers both by itself when the connection cannot carry
 * them; this is the ceiling, chosen from the network (`CallManager`).
 */
@Suppress("MagicNumber") // The sizes and frame rates are the values themselves.
enum class VideoQuality(
    val width: Int,
    val height: Int,
    val fps: Int,
) {
    /** 720p at 30 frames a second, what Telegram's apps send. */
    High(1280, 720, 30),

    /** 480p at 24: mobile data, or a call whose connection was lost once. */
    Medium(854, 480, 24),

    /** 360p at 15: roaming, or Data Saver on. */
    Low(640, 360, 15),
}

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
