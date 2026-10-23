package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * What a search in one chat looks for (`REQUIREMENTS.md` §5.9, in-chat search): messages with the words of [text], of
 * [sender] only if not null (with a blank [text], every message of theirs), in the forum topic or message thread
 * [topic] if not null. A secret chat ([isSecretChat]) keeps its messages on the device only, and is searched there,
 * by text only.
 */
data class ChatSearchQuery(
    val text: String,
    val sender: MessageSender? = null,
    val topic: MessageTopic? = null,
    val isSecretChat: Boolean = false,
) {
    /** Whether there is anything to search: words, or a sender outside secret chats. */
    val isEmpty: Boolean get() = text.isBlank() && (sender == null || isSecretChat)

    override fun toString() = "ChatSearchQuery(length=${text.length}, sender=$sender, topic=$topic, …)"
}

/**
 * A page of the messages a search in one chat found, newest first (`ChatSearchRepository.search`): their ids, about
 * how many match in all ([totalCount], null while TDLib does not know), and where the next page starts ([next], null
 * once there is nothing more).
 */
data class ChatSearchPage(
    val messageIds: ImmutableList<Long>,
    val totalCount: Int?,
    val next: ChatSearchCursor?,
)

/** Where the next page of a search in one chat starts, as the page before it said. */
sealed interface ChatSearchCursor {
    /** Before message [messageId] (td_api.tl `foundChatMessages.next_from_message_id`). */
    data class FromMessage(
        val messageId: Long,
    ) : ChatSearchCursor

    /** TDLib's offset of a secret chat's search (td_api.tl `foundMessages.next_offset`). */
    data class Offset(
        val offset: String,
    ) : ChatSearchCursor
}
