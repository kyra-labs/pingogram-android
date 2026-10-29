package dev.kyralabs.pingogram.core.domain.model

/**
 * A topic of Saved Messages (td_api.tl `savedMessagesTopic`): Telegram sorts what the user saved by where it came
 * from, one topic per chat, as the tabs of Saved Messages show them.
 *
 * @property id the topic's id, as [MessageTopic.SavedMessages] names it.
 * @property kind where its messages come from.
 * @property isPinned pinned among the topics.
 * @property order where it sorts: topics go by [order], highest first; 0 lists it nowhere.
 * @property lastMessage its newest message; null if it has none or TDLib does not know it.
 */
data class SavedMessagesTopic(
    val id: Long,
    val kind: SavedTopicKind,
    val isPinned: Boolean,
    val order: Long,
    val lastMessage: Message?,
) {
    val topic: MessageTopic.SavedMessages get() = MessageTopic.SavedMessages(id)

    override fun toString() = "SavedMessagesTopic(id=$id, kind=$kind, isPinned=$isPinned, order=$order, …)"
}

/** Where the messages of a [SavedMessagesTopic] come from. */
sealed interface SavedTopicKind {
    /** What the user sent to Saved Messages, and what was forwarded from chats the user does not know. */
    data object MyNotes : SavedTopicKind

    /** Messages forwarded from people who do not link their forwards to their account. */
    data object AuthorHidden : SavedTopicKind

    /** Messages forwarded from chat [chatId]. */
    data class SavedFrom(
        val chatId: Long,
    ) : SavedTopicKind
}
