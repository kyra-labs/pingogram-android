package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable

/**
 * What the Settings screen shows. Only the account section exists so far; the other settings follow in
 * `docs/ROADMAP.md` M3.
 *
 * @property isLogOutDialogOpen the log out confirmation is open.
 * @property isLoggingOut logging out was confirmed and is in progress; once TDLib starts logging out, the app leaves
 *   for the login flow (FR-A8), so this screen never sees it finish.
 */
@Immutable
data class SettingsUiState(
    val isLogOutDialogOpen: Boolean = false,
    val isLoggingOut: Boolean = false,
)
