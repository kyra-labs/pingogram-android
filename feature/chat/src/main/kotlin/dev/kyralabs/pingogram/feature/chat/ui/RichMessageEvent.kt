package dev.kyralabs.pingogram.feature.chat.ui

/** What the user does with the conversation's rich messages, for [ChatViewModel]. */
sealed interface RichMessageEvent : ChatEvent {
    /** Section [sectionId] (`RichBlock.Details.id`) of message [messageId] was tapped: it opens or closes. */
    data class ToggleSection(
        val messageId: Long,
        val sectionId: Int,
    ) : RichMessageEvent

    /** "Show more" under a long message [messageId]: the bubble shows all of it. */
    data class ShowAll(
        val messageId: Long,
    ) : RichMessageEvent

    /** "Show full message" under message [messageId], of which Telegram sent only the start: the rest loads. */
    data class LoadFull(
        val messageId: Long,
    ) : RichMessageEvent
}
