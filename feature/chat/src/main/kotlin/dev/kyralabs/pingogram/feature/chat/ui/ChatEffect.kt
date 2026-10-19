package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError

/** One-off effects of [ChatViewModel], handled by the screen once each (`docs/ARCHITECTURE.md`, ViewModels). */
sealed interface ChatEffect {
    /** An action failed: a snackbar shows the error's text, none for [DomainError.Silent]. Errors are never dialogs. */
    data class ShowError(
        val error: DomainError,
    ) : ChatEffect

    /**
     * Open the downloaded document at [path] in another app, as [mimeType] (the document's, null if it names none); a
     * snackbar if no app takes it.
     */
    data class OpenFile(
        val path: String,
        val mimeType: String?,
    ) : ChatEffect {
        // The path ends in the document's name, which can be personal.
        override fun toString() = "OpenFile(mimeType=$mimeType, …)"
    }

    /** The file of a voice message or song could not be played: a snackbar says so. */
    data object PlaybackFailed : ChatEffect

    /**
     * The message of a tapped quote, or the one the conversation was opened at, no longer exists: a snackbar says so,
     * and the list shows where it was.
     */
    data object MessageUnavailable : ChatEffect

    /** A message was scheduled for [at] (Unix time, seconds): a snackbar says when it will be sent. */
    data class MessageScheduled(
        val at: Long,
    ) : ChatEffect

    /** Open the photo, video or GIF of message [messageId] of this chat in the media viewer. */
    data class OpenMedia(
        val messageId: Long,
    ) : ChatEffect

    /**
     * Put [text] on the clipboard, as plain text. Android 13 and later confirm it themselves; earlier versions get a
     * snackbar.
     */
    data class CopyText(
        val text: String,
    ) : ChatEffect {
        override fun toString() = "CopyText(length=${text.length})"
    }

    /** The user started a reply or an edit: the composer's text field takes the focus, which opens the keyboard. */
    data object FocusComposer : ChatEffect

    /** The action asked for is not allowed for the message (any more): a snackbar says so. */
    data object ActionUnavailable : ChatEffect

    /**
     * Open the camera app to take a photo into the file at [path] (in the app's cache, handed over as a content URI);
     * the screen reports what came of it with `ChatEvent.PhotoTaken`, also when no camera app opens.
     */
    data class LaunchCamera(
        val path: String,
    ) : ChatEffect {
        override fun toString() = "LaunchCamera(…)"
    }

    /** Show the point at [latitude], [longitude] (in degrees) in a maps app, named [label] if there is one. */
    data class OpenLocation(
        val latitude: Double,
        val longitude: Double,
        val label: String?,
    ) : ChatEffect {
        override fun toString() = "OpenLocation(…)"
    }
}
