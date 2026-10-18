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

    /** The file of a voice message or song could not be played: a snackbar says so. */
    data object PlaybackFailed : ChatEffect

    /**
     * Scroll the list to message [messageId], which the items hold now, and highlight it: the replied message of a
     * tapped quote ([ChatEvent.OpenReply]), or the message the conversation was opened at.
     */
    data class ScrollToMessage(
        val messageId: Long,
    ) : ChatEffect

    /** The replied message of a tapped quote was deleted, or is too far back: a snackbar says so. */
    data object MessageUnavailable : ChatEffect

    /**
     * The message the conversation was opened at (found by search) is not in the history loaded for it, up to
     * [ChatViewModel.MAX_OPEN_PAGES] pages back: the conversation stays at the newest messages, and a snackbar says so.
     */
    data object MessageNotLoaded : ChatEffect

    /** Open the photo, video or GIF of message [messageId] of this chat in the media viewer. */
    data class OpenMedia(
        val messageId: Long,
    ) : ChatEffect

    /** Show the point at [latitude], [longitude] (in degrees) in a maps app, named [label] if there is one. */
    data class OpenLocation(
        val latitude: Double,
        val longitude: Double,
        val label: String?,
    ) : ChatEffect {
        override fun toString() = "OpenLocation(…)"
    }
}
