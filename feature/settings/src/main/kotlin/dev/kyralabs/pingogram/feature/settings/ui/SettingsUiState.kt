package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto

/**
 * What the Settings screen shows: the account header, the rows that open the other settings pages, and "Log out".
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
 * The logged-in account at the top of Settings, read-only.
 *
 * @property userId the account's user id, which picks its avatar placeholder.
 * @property name the first and last names.
 * @property phoneNumber the phone number formatted for its country ("+44 7911 123456"), or as `+` and its digits until
 *   TDLib has formatted it; empty if TDLib reports none.
 * @property username the first active username, without `@`; null without one.
 */
@Immutable
data class AccountUiModel(
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val phoneNumber: String,
    val username: String?,
) {
    override fun toString() = "AccountUiModel(userId=$userId, …)"
}

/** The pages Settings opens, each pushed above the Settings tab. */
enum class SettingsPage {
    Appearance,
    DataStorage,
    About,
}
