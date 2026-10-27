package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toCall
import dev.kyralabs.pingogram.core.data.mapper.toTdApi
import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallProblem
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import dev.kyralabs.pingogram.core.domain.model.CallSignalingData
import dev.kyralabs.pingogram.core.domain.repository.CallRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import kotlin.time.Duration

/**
 * [CallRepository] on TDLib's calls. TDLib announces every call, incoming ones included, with `updateCall`, the whole
 * call again at each change of its state, so the calls of the session are kept: a handful at most.
 *
 * The engine's data from the other side ([signalingData]) is not cached: it is read from TDLib's updates as they come,
 * by `:feature:calls`' `CallManager`, which collects them from app start.
 */
@AccountScoped
class TdlibCallRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : CallRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, persistentMapOf(), ::reduce)

        override val calls: Flow<ImmutableMap<Int, Call>> = store.state.distinctUntilChanged { old, new -> old === new }

        override fun call(id: Int): Flow<Call?> = store.state.map { it[id] }.distinctUntilChanged()

        override val signalingData: Flow<CallSignalingData> =
            client.updates
                .filterIsInstance<TdApi.UpdateNewCallSignalingData>()
                .map { CallSignalingData(it.callId, it.data ?: ByteArray(0)) }

        override suspend fun startCall(
            userId: Long,
            isVideo: Boolean,
            protocol: CallProtocol,
        ): Outcome<Int> = client.request(TdApi.CreateCall(userId, protocol.toTdApi(), isVideo)).map { it.id }

        override suspend fun acceptCall(
            callId: Int,
            protocol: CallProtocol,
        ): Outcome<Unit> = client.request(TdApi.AcceptCall(callId, protocol.toTdApi())).map {}

        override suspend fun discardCall(
            callId: Int,
            isDisconnected: Boolean,
            duration: Duration,
            isVideo: Boolean,
            connectionId: Long,
            inviteLink: String,
        ): Outcome<Unit> =
            client
                .request(
                    TdApi.DiscardCall(
                        callId,
                        isDisconnected,
                        inviteLink,
                        duration.inWholeSeconds.toInt(),
                        isVideo,
                        connectionId,
                    ),
                ).map {}

        override suspend fun rateCall(
            callId: Int,
            stars: Int,
            problems: Set<CallProblem>,
        ): Outcome<Unit> =
            client
                .request(
                    TdApi.SendCallRating(
                        TdApi.InputCallDiscarded(callId),
                        stars,
                        // comment: Pingogram asks only for the stars and the problems.
                        "",
                        problems.map { it.toTdApi() }.toTypedArray(),
                    ),
                ).map {}

        override suspend fun sendSignalingData(
            callId: Int,
            data: ByteArray,
        ): Outcome<Unit> = client.request(TdApi.SendCallSignalingData(callId, data)).map {}
    }

/** The calls with [update] applied; the same map for other updates and for a call that is unchanged. */
private fun reduce(
    calls: PersistentMap<Int, Call>,
    update: TdApi.Update,
): PersistentMap<Int, Call> {
    if (update !is TdApi.UpdateCall) return calls
    val call = update.call.toCall()
    return if (calls[call.id] == call) calls else calls.put(call.id, call)
}
