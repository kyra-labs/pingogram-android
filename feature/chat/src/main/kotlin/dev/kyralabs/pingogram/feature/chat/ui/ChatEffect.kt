package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError

/** One-off effects of [ChatViewModel], handled by the screen once each (`docs/ARCHITECTURE.md`, ViewModels). */
sealed interface ChatEffect {
    /** An action failed: a snackbar shows the error's text, none for [DomainError.Silent]. Errors are never dialogs. */
    data class ShowError(
        val error: DomainError,
    ) : ChatEffect

    /**
     * A downloaded file was to be opened. Opening it with another app needs a `FileProvider`, which is not there yet,
     * so a snackbar says that this comes later; replacing this effect is all it takes once it is.
     */
    data object FileOpeningUnavailable : ChatEffect

    /** Show the point at [latitude], [longitude] (in degrees) in a maps app, named [label] if there is one. */
    data class OpenLocation(
        val latitude: Double,
        val longitude: Double,
        val label: String?,
    ) : ChatEffect {
        override fun toString() = "OpenLocation(…)"
    }
}
