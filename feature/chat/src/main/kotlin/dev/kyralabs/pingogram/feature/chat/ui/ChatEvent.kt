package dev.kyralabs.pingogram.feature.chat.ui

/** What the user and the screen's lifecycle do on the conversation screen, sent to [ChatViewModel.onEvent]. */
sealed interface ChatEvent {
    /** The screen is started (visible): the chat is opened in TDLib until [Stopped]. */
    data object Started : ChatEvent

    /** The screen is stopped, or left: the chat is closed in TDLib. */
    data object Stopped : ChatEvent

    /**
     * Whether the list shows its oldest loaded message or is within about a page of it; reported on every change,
     * and false when the list leaves the screen. While true, older messages load, page after page, until the start.
     */
    data class NearOldestChanged(
        val isNearOldest: Boolean,
    ) : ChatEvent

    /** "Try again" after finding the chat or loading messages failed. */
    data object Retry : ChatEvent

    /** A hidden spoiler in message [messageId] was tapped: every spoiler of its text shows. */
    data class RevealSpoilers(
        val messageId: Long,
    ) : ChatEvent

    /**
     * The photo, video, GIF or video message [messageId] (an album's item, by its own message) was tapped. It is for
     * the media viewer, the next item of the roadmap; until it comes, nothing happens.
     */
    data class OpenMedia(
        val messageId: Long,
    ) : ChatEvent

    /** Download the file [fileId] of a document, at visible priority. */
    data class DownloadFile(
        val fileId: Int,
    ) : ChatEvent

    /** Stop downloading the file [fileId]; what was downloaded stays. */
    data class CancelDownload(
        val fileId: Int,
    ) : ChatEvent

    /** The downloaded document [fileId] of message [messageId] was tapped, to open it with another app. */
    data class OpenFile(
        val messageId: Long,
        val fileId: Int,
    ) : ChatEvent

    /** The location or venue of message [messageId] was tapped, to show it in a maps app. */
    data class OpenLocation(
        val messageId: Long,
    ) : ChatEvent

    /**
     * The play button of the voice message or song [messageId], whose file is [fileId], was tapped: it plays, or
     * pauses, downloading the file first if it is not on the device.
     */
    data class PlayPause(
        val messageId: Long,
        val fileId: Int,
    ) : ChatEvent

    /**
     * The voice message or song [messageId] is to play from [fraction] (0–1) of its length: the one in the player
     * moves there, another one starts there.
     */
    data class Seek(
        val messageId: Long,
        val fraction: Float,
    ) : ChatEvent
}
