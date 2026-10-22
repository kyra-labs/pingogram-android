package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The pinned messages of a chat, for the conversation's pinned messages bar (`REQUIREMENTS.md` FR-C9). Pinning and
 * unpinning one message are message actions (`MessageActionRepository.pin`, `unpin`).
 */
interface PinnedMessageRepository {
    /**
     * The pinned messages of chat [chatId] (of its forum topic or message thread [topic], if not null), newest first,
     * once they are loaded, then every change for as long as it is collected: a message pinned or unpinned on any
     * device, deleted or edited. Each collection loads them again; at most the newest [MAX_PINNED] come. A load that
     * fails reads as none.
     */
    fun pinnedMessages(
        chatId: Long,
        topic: MessageTopic? = null,
    ): Flow<ImmutableList<Message>>

    /** Unpins every pinned message of chat [chatId]; fails if the user may not, or with `DomainError.Network`. */
    suspend fun unpinAll(chatId: Long): Outcome<Unit>

    companion object {
        /** The pinned messages [pinnedMessages] loads at most, newest first (td_api.tl `searchChatMessages.limit`). */
        const val MAX_PINNED = 100
    }
}
