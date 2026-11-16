package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DirectMessagesTopic
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The conversations of a channel's direct messages chat, as its administrators see them (td_api.tl
 * `directMessagesChatTopic`): one per person who wrote to the channel. TDLib announces each with
 * `updateDirectMessagesChatTopic` once asked for them ([loadMore]), and this repository keeps them for the session.
 * Their messages are `MessageRepository`'s history of `MessageTopic.DirectMessages`.
 *
 * Requests that get no answer in time fail with `DomainError.Network`, a flood wait with `DomainError.FloodWait`.
 */
interface DirectMessagesTopicRepository {
    /** The topics of chat [chatId] TDLib announced, by `order`, highest first, then the list after every change. */
    fun topics(chatId: Long): Flow<ImmutableList<DirectMessagesTopic>>

    /** Topic [topicId] of chat [chatId], then after every change; null while TDLib has not announced it. */
    fun topic(
        chatId: Long,
        topicId: Long,
    ): Flow<DirectMessagesTopic?>

    /**
     * Asks TDLib for more topics of chat [chatId] (`loadDirectMessagesChatTopics`), which reach [topics]. True if more
     * may follow, false once all are loaded.
     */
    suspend fun loadMore(chatId: Long): Outcome<Boolean>

    /** Marks topic [topicId] as unread, or not ([unread]) (`setDirectMessagesChatTopicIsMarkedAsUnread`). */
    suspend fun setMarkedAsUnread(
        chatId: Long,
        topicId: Long,
        unread: Boolean,
    ): Outcome<Unit>
}
