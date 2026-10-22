package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import dev.kyralabs.pingogram.core.domain.model.CallSignalingData
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * One-to-one calls as TDLib signals them (`REQUIREMENTS.md` FR-V1, `docs/ARCHITECTURE.md`, Calls). TDLib announces
 * every call, incoming ones included, with `updateCall`, and the engine's data from the other side with
 * `updateNewCallSignalingData`; neither is replayed, so this repository subscribes at app start and keeps the calls of
 * the session in memory. Everything is dropped when TDLib closes (logout).
 *
 * The actions fail with a `DomainError`: `CallPrivacyRestricted` when the other person's privacy settings refuse
 * calls, `CallPeerOutdated` when their app cannot take calls, `FloodWait`, `Network`, …
 */
interface CallRepository {
    /** Every call TDLib announced in this session by id, then the map after every change. */
    val calls: Flow<ImmutableMap<Int, Call>>

    /** Call [id], then every change; null while TDLib has not announced it. */
    fun call(id: Int): Flow<Call?>

    /**
     * The other side's engine data for every call, as it arrives; hot and without replay, so a call's engine must
     * collect it from before the call is ready (`CallManager` keeps what comes earlier).
     */
    val signalingData: Flow<CallSignalingData>

    /**
     * Calls user [userId] with the engine's [protocol] and returns the call's id; TDLib announces it at once, pending
     * until the other person answers.
     */
    suspend fun startCall(
        userId: Long,
        isVideo: Boolean,
        protocol: CallProtocol,
    ): Outcome<Int>

    /** Answers incoming call [callId] with the engine's [protocol]. */
    suspend fun acceptCall(
        callId: Int,
        protocol: CallProtocol,
    ): Outcome<Unit>

    /**
     * Ends call [callId]: declines or cancels it while it rings, hangs up once connected. [isDisconnected] says the
     * connection was lost rather than closed by the user; [duration] is how long it was connected, [connectionId] the
     * engine's id of the connection (0 if none).
     */
    suspend fun discardCall(
        callId: Int,
        isDisconnected: Boolean,
        duration: Duration,
        isVideo: Boolean,
        connectionId: Long,
    ): Outcome<Unit>

    /** Sends the engine's [data] for call [callId] to the other side. */
    suspend fun sendSignalingData(
        callId: Int,
        data: ByteArray,
    ): Outcome<Unit>
}
