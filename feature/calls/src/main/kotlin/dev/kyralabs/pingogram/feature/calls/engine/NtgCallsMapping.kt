package dev.kyralabs.pingogram.feature.calls.engine

import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import dev.kyralabs.pingogram.core.domain.model.CallServer
import dev.kyralabs.pingogram.core.domain.model.CallServerType
import dev.kyralabs.pingogram.core.domain.model.ConferenceChannel
import io.github.pytgcalls.ConnectionState
import io.github.pytgcalls.VideoRotation
import io.github.pytgcalls.media.StreamStatus
import io.github.pytgcalls.models.MediaSegmentQuality
import io.github.pytgcalls.models.SegmentPartRequest
import io.github.pytgcalls.p2p.Protocol
import io.github.pytgcalls.p2p.RTCServer
import kotlinx.collections.immutable.toImmutableList
import org.json.JSONException
import org.json.JSONObject

/** What NTgCalls speaks, as TDLib's `callProtocol`. */
internal fun Protocol.toCallProtocol(): CallProtocol =
    CallProtocol(
        udpP2p = udp_p2p,
        udpReflector = udp_reflector,
        minLayer = min_layer,
        maxLayer = max_layer,
        libraryVersions = library_versions.orEmpty().toImmutableList(),
    )

/**
 * A server of TDLib's `callStateReady` as NTgCalls takes it: a Telegram reflector relays (TURN-like) with its peer tag;
 * a WebRTC server with its credentials and what it supports.
 */
internal fun CallServer.toRtcServer(): RTCServer =
    when (val type = type) {
        is CallServerType.TelegramReflector -> {
            RTCServer(id, ipv4, ipv6, port, null, null, true, false, type.isTcp, type.peerTag())
        }

        is CallServerType.Webrtc -> {
            RTCServer(
                id,
                ipv4,
                ipv6,
                port,
                type.username,
                type.password,
                type.supportsTurn,
                type.supportsStun,
                false,
                null,
            )
        }
    }

/** NTgCalls' connection states; it reports connecting again after a lost connection, while it reconnects. */
internal fun ConnectionState?.toMediaState(): CallMediaState =
    when (this) {
        ConnectionState.CONNECTED -> CallMediaState.Connected
        ConnectionState.FAILED, ConnectionState.TIMEOUT -> CallMediaState.Failed
        ConnectionState.CLOSED -> CallMediaState.Closed
        ConnectionState.CONNECTING, null -> CallMediaState.Connecting
    }

/** The other side's camera, as its engine reports it; idling is no video. */
internal fun StreamStatus?.toRemoteVideo(): RemoteVideo =
    when (this) {
        StreamStatus.ACTIVE -> RemoteVideo.Active
        StreamStatus.PAUSED -> RemoteVideo.Paused
        StreamStatus.IDLING, null -> RemoteVideo.Off
    }

/** A frame's rotation in degrees clockwise, as WebRTC's `VideoFrame` takes it. */
@Suppress("MagicNumber") // Degrees.
internal fun VideoRotation?.toDegrees(): Int =
    when (this) {
        VideoRotation.VIDEO_ROTATION_90 -> 90
        VideoRotation.VIDEO_ROTATION_180 -> 180
        VideoRotation.VIDEO_ROTATION_270 -> 270
        VideoRotation.VIDEO_ROTATION_0, null -> 0
    }

/**
 * NTgCalls' id of voice chat [groupCallId]: above every id of a one-to-one call, which TDLib numbers apart from group
 * calls, so the two never meet in NTgCalls.
 */
internal fun groupKey(groupCallId: Int): Long = GROUP_KEY_BASE + groupCallId

private const val GROUP_KEY_BASE = 1L shl 32

/**
 * The NTgCalls id of the [n]th conference call made since the app started: a range of its own, above voice chats', as
 * TDLib names the call only once it is joined (`docs/adr/0025-conference-calls.md`).
 */
internal fun conferenceKey(n: Int): Long = CONFERENCE_KEY_BASE + n

private const val CONFERENCE_KEY_BASE = 2L shl 32

/** NTgCalls' data channel of a conference frame: 1 the screen it shares, 0 the rest. */
internal fun Int.toChannel(): ConferenceChannel =
    if (this ==
        1
    ) {
        ConferenceChannel.ScreenSharing
    } else {
        ConferenceChannel.Main
    }

/** The microphone's audio source (SSRC) in NTgCalls' join payload (`{"ssrc": 1234, …}`); 0 if it has none. */
@Suppress("SwallowedException") // An unreadable payload has no SSRC; TDLib then refuses the join, which says so.
internal fun String.audioSource(): Int =
    try {
        JSONObject(this).optInt("ssrc")
    } catch (e: JSONException) {
        0
    }

/** A live stream's part request as the call manager answers it. */
internal fun SegmentPartRequest.toStreamRequest(): StreamRequest.Segment =
    StreamRequest.Segment(
        segmentId = segment_id,
        partId = part_id,
        timeMs = timestamp,
        channelId = channel_id,
        quality =
            when (quality) {
                MediaSegmentQuality.THUMBNAIL -> StreamQuality.Thumbnail
                MediaSegmentQuality.MEDIUM -> StreamQuality.Medium
                MediaSegmentQuality.FULL -> StreamQuality.Full
                MediaSegmentQuality.NONE, null -> null
            },
    )
