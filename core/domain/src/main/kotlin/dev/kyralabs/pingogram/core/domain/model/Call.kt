package dev.kyralabs.pingogram.core.domain.model

import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList

/**
 * A one-to-one call as TDLib signals it (td_api.tl `call`, `REQUIREMENTS.md` FR-V1): TDLib does the key exchange and
 * the signaling with Telegram; the media go through the call engine once the call is [CallState.Ready]
 * (`docs/adr/0015-call-media-engine.md`). Plain data, mapped from TDLib by `core:data`.
 */
data class Call(
    /** TDLib's id of the call, valid in this session only. */
    val id: Int,
    /** The other person: the callee of an outgoing call, the caller of an incoming one. */
    val userId: Long,
    val isOutgoing: Boolean,
    val isVideo: Boolean,
    val state: CallState,
)

/** Where a [Call] is (td_api.tl `CallState`). */
sealed interface CallState {
    /**
     * Ringing: an outgoing call waits for Telegram to create it ([isCreated]), then for the other person's device
     * ([isReceived]) and their answer; an incoming call waits for the user's.
     */
    data class Pending(
        val isCreated: Boolean,
        val isReceived: Boolean,
    ) : CallState

    /** Answered; TDLib is agreeing on the key. */
    data object ExchangingKeys : CallState

    /** Both sides agree: the engine connects with [connection]. */
    data class Ready(
        val connection: CallConnection,
    ) : CallState

    /** Ending on this side; TDLib tells Telegram. */
    data object HangingUp : CallState

    /** Ended, for [reason]. Telegram may ask the user to rate it ([needsRating]). */
    data class Discarded(
        val reason: CallDiscardReason,
        val needsRating: Boolean,
    ) : CallState

    /** TDLib could not set the call up, e.g. the other person's privacy settings refuse it. */
    data class Failed(
        val error: DomainError,
    ) : CallState
}

/** The call is still going on: ringing, connecting or connected. */
val CallState.isActive: Boolean
    get() = this is CallState.Pending || this is CallState.ExchangingKeys || this is CallState.Ready

/**
 * What the engine needs to connect a [CallState.Ready] call (td_api.tl `callStateReady`).
 *
 * `toString()` leaves the key out, so a call can be logged.
 */
class CallConnection(
    /** The protocol both sides agreed on: the other side's library versions, which the engine matches with its own. */
    val protocol: CallProtocol,
    val servers: ImmutableList<CallServer>,
    encryptionKey: ByteArray,
    /** Four emojis both people compare to check that nobody sits between them. */
    val emojis: ImmutableList<String>,
    /** The media may go directly between the two devices; otherwise only through Telegram's servers. */
    val allowP2p: Boolean,
    /** JSON the engine may read; may be empty. */
    val customParameters: String,
) {
    private val key = encryptionKey.copyOf()

    /** The key TDLib agreed on, for the engine only; a copy each time. */
    fun encryptionKey(): ByteArray = key.copyOf()

    override fun equals(other: Any?): Boolean =
        other is CallConnection &&
            protocol == other.protocol &&
            servers == other.servers &&
            key.contentEquals(other.key) &&
            emojis == other.emojis &&
            allowP2p == other.allowP2p &&
            customParameters == other.customParameters

    override fun hashCode(): Int {
        var result = protocol.hashCode()
        result = HASH_FACTOR * result + servers.hashCode()
        result = HASH_FACTOR * result + key.contentHashCode()
        result = HASH_FACTOR * result + emojis.hashCode()
        return result
    }

    override fun toString() = "CallConnection(protocol=$protocol, servers=${servers.size}, allowP2p=$allowP2p, …)"

    private companion object {
        const val HASH_FACTOR = 31
    }
}

/** The protocols a side supports (td_api.tl `callProtocol`). */
data class CallProtocol(
    val udpP2p: Boolean,
    val udpReflector: Boolean,
    val minLayer: Int,
    val maxLayer: Int,
    val libraryVersions: ImmutableList<String>,
)

/**
 * A server the engine may relay the call through (td_api.tl `callServer`): one of Telegram's reflectors, or a WebRTC
 * STUN/TURN server. `toString()` leaves the credentials out.
 */
data class CallServer(
    val id: Long,
    val ipv4: String,
    val ipv6: String,
    val port: Int,
    val type: CallServerType,
) {
    override fun toString() = "CallServer(id=$id, port=$port, type=${type::class.simpleName})"
}

sealed interface CallServerType {
    /** A Telegram reflector; [peerTag] identifies the call to it. */
    class TelegramReflector(
        peerTag: ByteArray,
        val isTcp: Boolean,
    ) : CallServerType {
        private val tag = peerTag.copyOf()

        fun peerTag(): ByteArray = tag.copyOf()

        override fun equals(other: Any?): Boolean =
            other is TelegramReflector && isTcp == other.isTcp && tag.contentEquals(other.tag)

        override fun hashCode(): Int = tag.contentHashCode() * HASH_FACTOR + isTcp.hashCode()

        private companion object {
            const val HASH_FACTOR = 31
        }
    }

    data class Webrtc(
        val username: String,
        val password: String,
        val supportsTurn: Boolean,
        val supportsStun: Boolean,
    ) : CallServerType {
        override fun toString() = "Webrtc(turn=$supportsTurn, stun=$supportsStun)"
    }
}

/** Data the engine of call [callId] sends to the other side's through Telegram (`updateNewCallSignalingData`). */
class CallSignalingData(
    val callId: Int,
    data: ByteArray,
) {
    private val bytes = data.copyOf()

    fun data(): ByteArray = bytes.copyOf()

    override fun toString() = "CallSignalingData(callId=$callId, ${bytes.size} bytes)"
}
