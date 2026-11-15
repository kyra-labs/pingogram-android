package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.Flow

/**
 * What people are doing in chats right now, such as typing (`REQUIREMENTS.md` FR-L2, FR-C8): one action per chat and
 * sender, kept while it lasts, and per forum topic or message thread where TDLib names one. Everything is dropped when
 * TDLib closes (logout).
 */
interface ChatActionRepository {
    /**
     * The current actions of every chat by chat id, then the map after every change; a chat where nobody is doing
     * anything has no entry. An action in a topic or thread counts for its chat too, as TDLib reports it for both. For
     * the chat list, which looks its chats up here instead of observing each one.
     */
    val actionsByChat: Flow<ImmutableMap<Long, ImmutableList<SenderAction>>>

    /**
     * The current actions in chat [chatId], those in its topics and threads included, in the order they started, then
     * every change; empty if there are none.
     */
    fun actions(chatId: Long): Flow<ImmutableList<SenderAction>>

    /**
     * The current actions in [topic] of chat [chatId], in the order they started, then every change: a forum topic's or
     * a message thread's own, never another topic's. A forum's General topic also has the actions TDLib reports for the
     * chat in no topic, as Telegram's apps type in General without naming it. Saved Messages and direct messages topics
     * have the chat's actions: TDLib names no such topic in an action.
     */
    fun actions(
        chatId: Long,
        topic: MessageTopic,
    ): Flow<ImmutableList<SenderAction>>
}
