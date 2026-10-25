package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.domain.model.DeviceAccount
import dev.kyralabs.pingogram.core.domain.repository.DeviceAccountsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [DeviceAccountsRepository] in memory: [accountList], [canAdd] and [adding] are set by the test, and the slots
 * switched to, the adds and the cancels are recorded.
 */
class FakeDeviceAccountsRepository(
    override val isAvailable: Boolean = true,
    accounts: ImmutableList<DeviceAccount> = persistentListOf(),
) : DeviceAccountsRepository {
    val accountList = MutableStateFlow(accounts)
    val canAdd = MutableStateFlow(true)
    val adding = MutableStateFlow(false)

    val switchedTo = mutableListOf<Int>()
    var adds = 0
        private set
    var cancels = 0
        private set

    override val accounts = accountList
    override val canAddAccount = canAdd
    override val isAdding = adding

    override fun switchTo(slot: Int) {
        switchedTo += slot
    }

    override fun addAccount() {
        adds++
    }

    override fun cancelAdding() {
        cancels++
    }
}
