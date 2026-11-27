package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Message

/**
 * A chat's messages after a given one, read without opening the chat's history or marking anything read: a channel's
 * unread posts for Listen to posts (`REQUIREMENTS.md` FR-X26).
 */
interface UnreadPostsRepository {
    /**
     * At most [limit] messages of chat [chatId] newer than message [afterMessageId], oldest first; fewer where there
     * are fewer. Fails as TDLib does (`DomainError.Network`, `DomainError.FloodWait`) only when none could be read.
     */
    suspend fun postsAfter(
        chatId: Long,
        afterMessageId: Long,
        limit: Int,
    ): Outcome<List<Message>>
}
