package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * The thread of replies to a message (`REQUIREMENTS.md` FR-C9, td_api.tl `messageThreadInfo`): thread [threadId] of
 * chat [chatId], the discussion group for a channel post's comments.
 *
 * @property head the messages that start the thread, oldest first: the post as the discussion group has it (an album
 *   has several), or the group message replied to.
 * @property replyCount the replies so far.
 * @property unreadCount the replies the user has not read.
 * @property lastReadInboxMessageId the last reply the user read; 0 if none.
 * @property lastReadOutboxMessageId the last of the user's replies someone else read; 0 if none.
 */
data class MessageThread(
    val chatId: Long,
    val threadId: Long,
    val head: ImmutableList<Message>,
    val replyCount: Int,
    val unreadCount: Int,
    val lastReadInboxMessageId: Long,
    val lastReadOutboxMessageId: Long,
) {
    /** Where the replies are. */
    val topic: MessageTopic.Thread get() = MessageTopic.Thread(threadId)

    /** Whether the thread is a channel post's comments: its head is the post, forwarded from the channel. */
    val isComments: Boolean get() = head.firstOrNull()?.forwardInfo?.origin is MessageOrigin.Channel

    override fun toString() = "MessageThread(chatId=$chatId, threadId=$threadId, replyCount=$replyCount, …)"
}
