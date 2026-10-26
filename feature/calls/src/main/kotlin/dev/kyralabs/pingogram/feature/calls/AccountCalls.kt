package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallProblem
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import dev.kyralabs.pingogram.core.domain.model.CallSignalingData
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.CallRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * The call ids of [AllAccountsCallRepository] (`docs/adr/0031-multiple-accounts.md`): each account's TDLib counts its
 * calls on its own, so an id carries its account's slot above TDLib's id ([of], [slotOf], [tdlibId]). The first
 * account's ids are TDLib's, as before there were several accounts.
 */
internal object CallIds {
    /** TDLib's call ids stay far below this: it counts the calls of one session from 1. */
    private const val SLOT_STRIDE = 1 shl 24

    fun of(
        slot: Int,
        tdlibId: Int,
    ): Int = slot * SLOT_STRIDE + tdlibId

    fun slotOf(callId: Int): Int = callId / SLOT_STRIDE

    fun tdlibId(callId: Int): Int = callId % SLOT_STRIDE
}

/** The account a call belongs to ([CallIds]); null while Telegram has not created an outgoing call yet. */
internal val ActiveCall.slot: Int? get() = callId?.let(CallIds::slotOf)

/** Every account's calls as one [CallRepository] ([AllAccountsCallRepository]), which [CallManager] runs. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class AllAccounts

/**
 * The one-to-one calls of every account on the device (`docs/adr/0031-multiple-accounts.md`), as one
 * [CallRepository]: each account's calls and signaling data with ids that carry its slot ([CallIds]), so a call to an
 * account in the background rings as one to the shown account does, and each action goes to the call's own account.
 * New calls go out from the shown account, whose screens start them. One call at a time across them all, as before
 * ([CallManager], [CallLine]).
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
internal class AllAccountsCallRepository
    @Inject
    constructor(
        private val graphs: AccountGraphs,
    ) : CallRepository {
        override val calls: Flow<ImmutableMap<Int, Call>> =
            graphs.accounts.flatMapLatest { accounts ->
                if (accounts.isEmpty()) return@flatMapLatest flowOf(persistentMapOf())
                combine(
                    accounts.map { account ->
                        account.repositories.callRepository().calls.map { calls ->
                            calls.values.map { call -> call.copy(id = CallIds.of(account.slot, call.id)) }
                        }
                    },
                ) { lists -> lists.flatMap { it }.associateBy { it.id }.toImmutableMap() }
            }

        override fun call(id: Int): Flow<Call?> =
            repository(id)
                ?.call(CallIds.tdlibId(id))
                ?.map { call -> call?.copy(id = id) }
                ?.distinctUntilChanged() ?: flowOf(null)

        override val signalingData: Flow<CallSignalingData> =
            graphs.accounts.flatMapLatest { accounts ->
                if (accounts.isEmpty()) return@flatMapLatest emptyFlow()
                accounts
                    .map { account ->
                        account.repositories.callRepository().signalingData.map { data ->
                            CallSignalingData(CallIds.of(account.slot, data.callId), data.data())
                        }
                    }.merge()
            }

        override suspend fun startCall(
            userId: Long,
            isVideo: Boolean,
            protocol: CallProtocol,
        ): Outcome<Int> {
            val slot = graphs.activeSlot.value
            val repository = graphs.repositories(slot)?.callRepository() ?: return gone()
            return when (val started = repository.startCall(userId, isVideo, protocol)) {
                is Outcome.Success -> Outcome.Success(CallIds.of(slot, started.value))
                is Outcome.Failure -> started
            }
        }

        override suspend fun acceptCall(
            callId: Int,
            protocol: CallProtocol,
        ): Outcome<Unit> = repository(callId)?.acceptCall(CallIds.tdlibId(callId), protocol) ?: gone()

        override suspend fun discardCall(
            callId: Int,
            isDisconnected: Boolean,
            duration: Duration,
            isVideo: Boolean,
            connectionId: Long,
        ): Outcome<Unit> =
            repository(callId)?.discardCall(CallIds.tdlibId(callId), isDisconnected, duration, isVideo, connectionId)
                ?: gone()

        override suspend fun rateCall(
            callId: Int,
            stars: Int,
            problems: Set<CallProblem>,
        ): Outcome<Unit> = repository(callId)?.rateCall(CallIds.tdlibId(callId), stars, problems) ?: gone()

        override suspend fun sendSignalingData(
            callId: Int,
            data: ByteArray,
        ): Outcome<Unit> = repository(callId)?.sendSignalingData(CallIds.tdlibId(callId), data) ?: gone()

        private fun repository(callId: Int): CallRepository? =
            graphs.repositories(CallIds.slotOf(callId))?.callRepository()

        /** The call's account left the device: its call ended with it. */
        private fun <T> gone(): Outcome<T> = Outcome.Failure(DomainError.Unauthorized)
    }

/**
 * Each account's users, for the person of a call of any account ([ActiveCall.slot]); the shown account's for a call
 * without an account yet, and for one whose account left.
 */
internal fun interface CallUsers {
    fun of(slot: Int?): UserRepository
}

internal class AccountCallUsers
    @Inject
    constructor(
        private val graphs: AccountGraphs,
        private val shown: UserRepository,
    ) : CallUsers {
        override fun of(slot: Int?): UserRepository = slot?.let { graphs.repositories(it)?.userRepository() } ?: shown
    }
