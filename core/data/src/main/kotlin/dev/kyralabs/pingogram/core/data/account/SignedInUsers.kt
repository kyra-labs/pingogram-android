package dev.kyralabs.pingogram.core.data.account

import dev.kyralabs.pingogram.core.data.di.Accounts
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in accounts on the device as their users, by slot (`docs/adr/0031-multiple-accounts.md`): what one
 * account's push registration names of the others, and whether another is still signed in when one logs out.
 */
fun interface SignedInUsers {
    /** The user of each signed-in account by slot, then every change. */
    fun users(): Flow<Map<Int, Long>>
}

/** [SignedInUsers] of [Accounts]; lazy, as each account's graph, which asks it, is made by [Accounts]. */
@Singleton
class AccountsSignedInUsers
    @Inject
    constructor(
        private val accounts: dagger.Lazy<Accounts>,
    ) : SignedInUsers {
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun users(): Flow<Map<Int, Long>> =
            accounts
                .get()
                .registry
                .flatMapLatest {
                    val all = accounts.get().all()
                    if (all.isEmpty()) return@flatMapLatest flowOf(emptyMap())
                    combine(
                        all.map { account ->
                            combine(
                                account.component.authRepository().authStep,
                                account.component.userRepository().myUserId,
                            ) { step, userId ->
                                userId?.takeIf { step == AuthStep.Ready }?.let {
                                    account.slot.index to
                                        it
                                }
                            }
                        },
                    ) { users -> users.filterNotNull().toMap() }
                }.distinctUntilChanged()
    }
