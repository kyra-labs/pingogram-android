package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.domain.repository.AccountGraph
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow

/** [AccountGraphs] for tests: the accounts as set ([accountList]), the shown one's slot as set ([active]). */
class FakeAccountGraphs(
    accounts: ImmutableList<AccountGraph> = persistentListOf(),
    activeSlot: Int = 0,
) : AccountGraphs {
    val accountList = MutableStateFlow(accounts)
    val active = MutableStateFlow(activeSlot)

    override val accounts = accountList
    override val activeSlot = active
}
