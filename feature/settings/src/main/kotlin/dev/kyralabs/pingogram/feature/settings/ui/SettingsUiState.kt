package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * What the Settings screen shows: the account's card, the rows that open the other settings pages, and "Log out".
 *
 * @property account the logged-in account; null until TDLib has announced its user.
 * @property otherAccounts the device's other signed-in accounts, below the card (`docs/adr/0031-multiple-accounts.md`);
 *   empty while the app offers one account only.
 * @property canAddAccount "Add account" shows: the app offers several accounts and fewer than the limit are signed in.
 * @property isLogOutDialogOpen the log out confirmation is open.
 * @property isLoggingOut logging out was confirmed and is in progress; once TDLib starts logging out, the app leaves
 *   for the login flow (FR-A8), so this screen never sees it finish.
 */
@Immutable
data class SettingsUiState(
    val account: AccountUiModel? = null,
    val otherAccounts: ImmutableList<OtherAccountUiModel> = persistentListOf(),
    val canAddAccount: Boolean = false,
    val isLogOutDialogOpen: Boolean = false,
    val isLoggingOut: Boolean = false,
)

/**
 * The logged-in account, on its card at the top of Settings.
 *
 * @property userId the account's user id, which picks its avatar placeholder.
 * @property name the first and last names.
 * @property phoneNumber the phone number formatted for its country ("+44 7911 123456"), or as `+` and its digits until
 *   TDLib has formatted it; empty if TDLib reports none.
 * @property username the first active username, without `@`; null without one.
 * @property isPremium the account subscribes to Telegram Premium, as TDLib reports it (`user.is_premium`).
 * @property isOnTestServers the account signed in on Telegram's test servers (`REQUIREMENTS.md` FR-A9).
 */
@Immutable
data class AccountUiModel(
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val phoneNumber: String,
    val username: String?,
    val isPremium: Boolean = false,
    val isOnTestServers: Boolean = false,
) {
    override fun toString() = "AccountUiModel(userId=$userId, …)"
}

/**
 * Another account on this device, a row below the card that switches to it.
 *
 * @property slot the account's slot, which [SettingsEvent.SwitchAccount] names.
 * @property phoneNumber formatted as the card's ([AccountUiModel.phoneNumber]).
 * @property unreadChats the account's unread chats in its main list, shown as a badge; 0 for none.
 */
@Immutable
data class OtherAccountUiModel(
    val slot: Int,
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val phoneNumber: String,
    val unreadChats: Int,
) {
    override fun toString() = "OtherAccountUiModel(slot=$slot, …)"
}

/** The pages Settings opens, each pushed above the Settings tab, or beside Settings home on expanded windows. */
enum class SettingsPage {
    EditProfile,
    Appearance,
    ChatFolders,
    Stickers,
    Notifications,
    DataStorage,
    PowerSaving,
    Language,
    About,
    PrivacySecurity,
}

/**
 * Settings home as the list beside a page (`docs/DESIGN_SYSTEM.md` §7, expanded windows): its page rows are
 * selectable, and the row of [openPage], the page shown beside it, reads as selected; none while no page is open.
 */
@Immutable
data class SettingsPageSelection(
    val openPage: SettingsPage?,
)
