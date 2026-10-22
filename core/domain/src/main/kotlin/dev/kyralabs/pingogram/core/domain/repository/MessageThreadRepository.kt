package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageThread

/**
 * The threads of replies to messages (`REQUIREMENTS.md` FR-C9): the comments of a channel post, which live in the
 * channel's discussion group, and the replies to a message of a group. The thread's messages themselves come from
 * `MessageRepository` with its `MessageTopic.Thread`.
 */
interface MessageThreadRepository {
    /**
     * The thread of message [messageId] of chat [chatId] (td_api.tl `getMessageThread`): for a channel post, the
     * thread in its discussion group. Fails with `DomainError.NotFound` when the message has no thread (comments are
     * off, or it was deleted), and as every request does (`DomainError.Network`, …).
     */
    suspend fun thread(
        chatId: Long,
        messageId: Long,
    ): Outcome<MessageThread>
}
