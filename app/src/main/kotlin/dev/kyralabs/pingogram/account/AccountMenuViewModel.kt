package dev.kyralabs.pingogram.account

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.DeviceAccount
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.repository.DeviceAccountsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What the Settings tab's long press offers (`docs/adr/0031-multiple-accounts.md`): the device's signed-in accounts
 * with their unread chats, the shown one marked, and "Add account" while [canAdd].
 */
@Immutable
data class AccountMenuState(
    val accounts: ImmutableList<AccountMenuItem>,
    val canAdd: Boolean,
)

/** An account in the menu: its [slot], the user's id (for the avatar's placeholder), name and photo. */
@Immutable
data class AccountMenuItem(
    val slot: Int,
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val unreadChats: Int,
    val isActive: Boolean,
) {
    override fun toString() = "AccountMenuItem(slot=$slot, isActive=$isActive, …)"
}

/** The Settings tab's account menu, as the app shell sees it; [state] is null while the app offers one account only. */
interface AccountMenuSource {
    val state: StateFlow<AccountMenuState?>

    fun switchTo(slot: Int)

    fun addAccount()
}

/**
 * [AccountMenuSource] over [DeviceAccountsRepository], the activity's, so the menu's accounts survive rotation; it
 * follows the accounts only while the shell collects [state], and offers nothing while
 * [DeviceAccountsRepository.isAvailable] is false.
 */
@HiltViewModel
class AccountMenuViewModel
    @Inject
    constructor(
        private val deviceAccounts: DeviceAccountsRepository,
    ) : ViewModel(),
        AccountMenuSource {
        override val state: StateFlow<AccountMenuState?> =
            if (deviceAccounts.isAvailable) {
                combine(deviceAccounts.accounts, deviceAccounts.canAddAccount) { accounts, canAdd ->
                    AccountMenuState(accounts.mapNotNull { it.toMenuItem() }.toImmutableList(), canAdd)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)
            } else {
                MutableStateFlow(null)
            }

        override fun switchTo(slot: Int) = deviceAccounts.switchTo(slot)

        override fun addAccount() = deviceAccounts.addAccount()

        private companion object {
            /** Kept through a configuration change, as the shell collects it again at once. */
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private fun DeviceAccount.toMenuItem(): AccountMenuItem? {
    val user = user ?: return null
    return AccountMenuItem(
        slot = slot,
        userId = user.id,
        name = listOf(user.firstName, user.lastName).filter(String::isNotBlank).joinToString(" "),
        photo = user.profilePhoto,
        unreadChats = unreadChats,
        isActive = isActive,
    )
}
