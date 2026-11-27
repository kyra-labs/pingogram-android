package dev.kyralabs.pingogram.feature.chat.ui

/** What the user does with Listen to posts (`REQUIREMENTS.md` FR-X26), handled by `listen.ListenViewModel`. */
sealed interface ListenEvent : ChatEvent {
    /** "Listen" in message [messageId]'s menu: reads that message alone. */
    data class Message(
        val messageId: Long,
    ) : ListenEvent

    /** "Listen to unread posts" in a channel: reads its posts after the last one read, oldest first. */
    data object Unread : ListenEvent

    data object Pause : ListenEvent

    data object Resume : ListenEvent

    /** Skips to the next post; after the last one, stops. */
    data object Next : ListenEvent

    /** Reads at the voice messages' next speed, which voice messages then play at too. */
    data object ChangeSpeed : ListenEvent

    data object Stop : ListenEvent
}
