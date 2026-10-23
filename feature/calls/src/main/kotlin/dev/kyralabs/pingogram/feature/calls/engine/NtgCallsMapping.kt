package dev.kyralabs.pingogram.feature.calls.engine

import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import dev.kyralabs.pingogram.core.domain.model.CallServer
import dev.kyralabs.pingogram.core.domain.model.CallServerType
import io.github.pytgcalls.ConnectionState
import io.github.pytgcalls.VideoRotation
import io.github.pytgcalls.media.StreamStatus
import io.github.pytgcalls.p2p.Protocol
import io.github.pytgcalls.p2p.RTCServer
import kotlinx.collections.immutable.toImmutableList

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
