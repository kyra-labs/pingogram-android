package dev.kyralabs.pingogram.core.domain.model

/**
 * One person's (or chat's) conversation with a channel's administrators, in the channel's direct messages chat
 * (td_api.tl `directMessagesChatTopic`), as its administrators see them.
 *
 * @property chatId the channel's direct messages chat.
 * @property id the topic's id, as [MessageTopic.DirectMessages] names it.
 * @property sender who wrote to the channel.
 * @property order where it sorts: topics go by [order], highest first; 0 lists it nowhere.
 * @property isPaid the sender pays Telegram Stars for each message (td_api.tl `can_send_unpaid_messages` false).
 * @property isMarkedAsUnread marked as unread by an administrator.
 * @property unreadCount the sender's messages nobody read yet.
 * @property lastReadInboxMessageId the newest of the sender's messages read.
 * @property lastMessage its newest message; null if it has none or TDLib does not know it.
 */
data class DirectMessagesTopic(
    val chatId: Long,
    val id: Long,
    val sender: MessageSender,
    val order: Long,
    val isPaid: Boolean,
    val isMarkedAsUnread: Boolean,
    val unreadCount: Long,
    val lastReadInboxMessageId: Long,
    val lastMessage: Message?,
) {
    val topic: MessageTopic.DirectMessages get() = MessageTopic.DirectMessages(id)

    /** Unread messages, or marked as unread. */
    val isUnread: Boolean get() = unreadCount > 0 || isMarkedAsUnread

    override fun toString() = "DirectMessagesTopic(chatId=$chatId, id=$id, order=$order, unread=$unreadCount, …)"
}
