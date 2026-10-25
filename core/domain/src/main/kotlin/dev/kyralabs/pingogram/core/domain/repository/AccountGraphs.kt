package dev.kyralabs.pingogram.core.domain.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Every account on the device with its repositories (`docs/adr/0031-multiple-accounts.md`), for the parts of the app
 * that follow all of them in the background, whichever account the screens show: notifications and their
 * conversation shortcuts, calls. Everything else injects the active account's repositories one by one.
 */
interface AccountGraphs {
    /**
     * The accounts on the device, signed in or signing in, in the order they were added, once the app knows which
     * there are (it reads them as it starts); then every change.
     */
    val accounts: Flow<ImmutableList<AccountGraph>>

    /** The slot of the account the screens show. */
    val activeSlot: StateFlow<Int>
}

/** One account on the device: its slot (0 for the first) and its [repositories]. */
class AccountGraph(
    val slot: Int,
    val repositories: AccountRepositories,
) {
    override fun toString() = "AccountGraph(slot=$slot)"
}
