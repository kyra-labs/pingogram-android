package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.SenderAction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.Flow

/**
 * What people are doing in chats right now, such as typing (`REQUIREMENTS.md` FR-L2, FR-C8): one action per chat and
 * sender, kept while it lasts. Everything is dropped when TDLib closes (logout).
 */
interface ChatActionRepository {
    /**
     * The current actions of every chat by chat id, then the map after every change; a chat where nobody is doing
     * anything has no entry. For the chat list, which looks its chats up here instead of observing each one.
     */
    val actionsByChat: Flow<ImmutableMap<Long, ImmutableList<SenderAction>>>

    /** The current actions in chat [chatId], in the order they started, then every change; empty if there are none. */
    fun actions(chatId: Long): Flow<ImmutableList<SenderAction>>
}
