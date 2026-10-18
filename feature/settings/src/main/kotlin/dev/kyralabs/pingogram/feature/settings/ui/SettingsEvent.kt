package dev.kyralabs.pingogram.feature.settings.ui

/** What the user does on the Settings screen, handled by [SettingsViewModel.onEvent]. */
sealed interface SettingsEvent {
    /** "Log out" was tapped: ask for confirmation. */
    data object LogOut : SettingsEvent

    data object ConfirmLogOut : SettingsEvent

    data object DismissLogOut : SettingsEvent
}
