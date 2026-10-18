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
}
