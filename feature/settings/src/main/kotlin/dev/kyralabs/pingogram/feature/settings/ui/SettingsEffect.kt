package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError

/** One-off side effects of [SettingsViewModel], handled once by [SettingsRoute]. */
sealed interface SettingsEffect {
    /** A failed request, shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : SettingsEffect
}
