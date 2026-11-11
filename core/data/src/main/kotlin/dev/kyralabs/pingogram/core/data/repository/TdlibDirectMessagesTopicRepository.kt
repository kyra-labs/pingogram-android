package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.data.mapper.toMessageSender
import dev.kyralabs.pingogram.core.domain.model.DirectMessagesTopic
import dev.kyralabs.pingogram.core.domain.repository.DirectMessagesTopicRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [DirectMessagesTopicRepository] on TDLib's direct messages chat topics. TDLib announces each topic only with
 * `updateDirectMessagesChatTopic` (after `loadDirectMessagesChatTopics`, and whenever it changes: a new message, a
 * read), so they are kept for the session in an [UpdateStore], which makes this a [TdlibUpdateCache]: by chat, then
 * by topic, a few fields each, only for the channels the user administers. `loadDirectMessagesChatTopics` answers 404
 * once all are loaded, which is no error here.
 */
@AccountScoped
class TdlibDirectMessagesTopicRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : DirectMessagesTopicRepository,
        TdlibUpdateCache {
        private val store =
            UpdateStore<PersistentMap<Long, PersistentMap<Long, DirectMessagesTopic>>>(
                client,
                scope,
                persistentMapOf(),
            ) {
                chats,
                update,
                ->
                val topic = (update as? TdApi.UpdateDirectMessagesChatTopic)?.topic?.toDirectMessagesTopic()
                if (topic == null) {
                    chats
                } else {
                    chats.put(topic.chatId, (chats[topic.chatId] ?: persistentMapOf()).put(topic.id, topic))
                }
            }

        override fun topics(chatId: Long): Flow<ImmutableList<DirectMessagesTopic>> =
            store.state
                .map { it[chatId] }
                .distinctUntilChanged { old, new -> old === new }
                .map { topics -> topics?.values.orEmpty().listed() }
                .distinctUntilChanged()

        override fun topic(
            chatId: Long,
            topicId: Long,
        ): Flow<DirectMessagesTopic?> = store.state.map { it[chatId]?.get(topicId) }.distinctUntilChanged()

        override suspend fun loadMore(chatId: Long): Outcome<Boolean> =
            when (val answer = client.request(TdApi.LoadDirectMessagesChatTopics(chatId, PAGE_SIZE))) {
                is Outcome.Success -> Outcome.Success(true)

                // 404: every topic is loaded.
                is Outcome.Failure -> if (answer.error == DomainError.NotFound) Outcome.Success(false) else answer
            }

        override suspend fun setMarkedAsUnread(
            chatId: Long,
            topicId: Long,
            unread: Boolean,
        ): Outcome<Unit> =
            client.request(TdApi.SetDirectMessagesChatTopicIsMarkedAsUnread(chatId, topicId, unread)).map {}

        private companion object {
            const val PAGE_SIZE = 50
        }
    }

/** The listed topics (an `order` of 0 lists a topic nowhere), by `order`, highest first. */
private fun Collection<DirectMessagesTopic>.listed(): ImmutableList<DirectMessagesTopic> =
    filter { it.order != 0L }.sortedByDescending { it.order }.toImmutableList()

/** The topic; null for a sender of a kind a newer TDLib adds. */
internal fun TdApi.DirectMessagesChatTopic.toDirectMessagesTopic(): DirectMessagesTopic? {
    val sender = senderId?.toMessageSender() ?: return null
    return DirectMessagesTopic(
        chatId = chatId,
        id = id,
        sender = sender,
        order = order,
        isPaid = !canSendUnpaidMessages,
        isMarkedAsUnread = isMarkedAsUnread,
        unreadCount = unreadCount,
        lastReadInboxMessageId = lastReadInboxMessageId,
        lastMessage = lastMessage?.toMessage(),
    )
}
