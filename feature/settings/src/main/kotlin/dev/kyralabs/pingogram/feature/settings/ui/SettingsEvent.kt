package dev.kyralabs.pingogram.feature.settings.ui

/** What the user does on the Settings screen, handled by [SettingsViewModel.onEvent]. */
sealed interface SettingsEvent {
    /** "Log out" was tapped: ask for confirmation. */
    data object LogOut : SettingsEvent

    data object ConfirmLogOut : SettingsEvent

    data object DismissLogOut : SettingsEvent

    /** Another account's row was tapped: the app opens again on it. */
    data class SwitchAccount(
        val slot: Int,
    ) : SettingsEvent

    /** "Add account" was tapped: the app opens on a new account's sign-in. */
    data object AddAccount : SettingsEvent
}
