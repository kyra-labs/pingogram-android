package dev.kyralabs.pingogram.core.domain.model

/**
 * An account on this device (`docs/adr/0031-multiple-accounts.md`): its [slot] (0 for the first), the user once TDLib
 * announced them, how many of its chats are unread, and whether it is the one the screens show.
 */
data class DeviceAccount(
    val slot: Int,
    val user: User?,
    val unreadChats: Int,
    val isActive: Boolean,
)

/** How many accounts a device holds: Telegram's app allows 3, a fourth while one of them has Telegram Premium. */
object DeviceAccountLimits {
    const val MAX_ACCOUNTS = 3
    const val MAX_ACCOUNTS_WITH_PREMIUM = 4

    fun of(accounts: List<DeviceAccount>): Int =
        if (accounts.any { it.user?.isPremium == true }) MAX_ACCOUNTS_WITH_PREMIUM else MAX_ACCOUNTS
}
