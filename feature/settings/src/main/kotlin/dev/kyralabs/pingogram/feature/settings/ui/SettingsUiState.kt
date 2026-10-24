package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto

/**
 * What the Settings screen shows: the account's card, the rows that open the other settings pages, and "Log out".
 *
 * @property account the logged-in account; null until TDLib has announced its user.
 * @property isLogOutDialogOpen the log out confirmation is open.
 * @property isLoggingOut logging out was confirmed and is in progress; once TDLib starts logging out, the app leaves
 *   for the login flow (FR-A8), so this screen never sees it finish.
 */
@Immutable
data class SettingsUiState(
    val account: AccountUiModel? = null,
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
 */
@Immutable
data class AccountUiModel(
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val phoneNumber: String,
    val username: String?,
    val isPremium: Boolean = false,
) {
    override fun toString() = "AccountUiModel(userId=$userId, …)"
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
