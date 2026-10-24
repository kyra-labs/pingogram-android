package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.data.error.toDomainError
import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallDiscardReason
import dev.kyralabs.pingogram.core.domain.model.CallProblem
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import dev.kyralabs.pingogram.core.domain.model.CallServer
import dev.kyralabs.pingogram.core.domain.model.CallServerType
import dev.kyralabs.pingogram.core.domain.model.CallState
import dev.kyralabs.pingogram.core.tdlib.TdError
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

internal fun TdApi.Call.toCall(): Call =
    Call(
        id = id,
        userId = userId,
        isOutgoing = isOutgoing,
        isVideo = isVideo,
        state = state.toCallState(),
    )

/** Null (td_api.tl requires a value, but the field is a Java reference) and states a newer TDLib adds: discarded. */
internal fun TdApi.CallState?.toCallState(): CallState =
    when (this) {
        is TdApi.CallStatePending -> CallState.Pending(isCreated, isReceived)
        is TdApi.CallStateExchangingKeys -> CallState.ExchangingKeys
        is TdApi.CallStateReady -> CallState.Ready(toConnection())
        is TdApi.CallStateHangingUp -> CallState.HangingUp
        is TdApi.CallStateDiscarded -> CallState.Discarded(reason.toCallDiscardReason(), needRating)
        is TdApi.CallStateError -> CallState.Failed(TdError(error.code, error.message).toDomainError())
        else -> CallState.Discarded(CallDiscardReason.Unknown, needsRating = false)
    }

private fun TdApi.CallStateReady.toConnection() =
    CallConnection(
        protocol = protocol.toCallProtocol(),
        servers = servers.orEmpty().mapNotNull { it.toCallServer() }.toImmutableList(),
        encryptionKey = encryptionKey ?: ByteArray(0),
        emojis = emojis.orEmpty().toImmutableList(),
        allowP2p = allowP2p,
        customParameters = customParameters.orEmpty(),
    )

internal fun TdApi.CallProtocol.toCallProtocol(): CallProtocol =
    CallProtocol(
        udpP2p = udpP2p,
        udpReflector = udpReflector,
        minLayer = minLayer,
        maxLayer = maxLayer,
        libraryVersions = libraryVersions.orEmpty().toImmutableList(),
    )

internal fun CallProtocol.toTdApi(): TdApi.CallProtocol =
    TdApi.CallProtocol(udpP2p, udpReflector, minLayer, maxLayer, libraryVersions.toTypedArray())

/** Null for a server type a newer TDLib adds, which the engine could not use. */
private fun TdApi.CallServer.toCallServer(): CallServer? {
    val type =
        when (val type = type) {
            is TdApi.CallServerTypeTelegramReflector -> {
                CallServerType.TelegramReflector(type.peerTag ?: ByteArray(0), type.isTcp)
            }

            is TdApi.CallServerTypeWebrtc -> {
                CallServerType.Webrtc(
                    type.username.orEmpty(),
                    type.password.orEmpty(),
                    type.supportsTurn,
                    type.supportsStun,
                )
            }

            else -> {
                return null
            }
        }
    return CallServer(id = id, ipv4 = ipAddress.orEmpty(), ipv6 = ipv6Address.orEmpty(), port = port, type = type)
}

internal fun CallProblem.toTdApi(): TdApi.CallProblem =
    when (this) {
        CallProblem.Echo -> TdApi.CallProblemEcho()
        CallProblem.Noise -> TdApi.CallProblemNoise()
        CallProblem.Interruptions -> TdApi.CallProblemInterruptions()
        CallProblem.DistortedSpeech -> TdApi.CallProblemDistortedSpeech()
        CallProblem.SilentLocal -> TdApi.CallProblemSilentLocal()
        CallProblem.SilentRemote -> TdApi.CallProblemSilentRemote()
        CallProblem.Dropped -> TdApi.CallProblemDropped()
        CallProblem.DistortedVideo -> TdApi.CallProblemDistortedVideo()
        CallProblem.PixelatedVideo -> TdApi.CallProblemPixelatedVideo()
    }
