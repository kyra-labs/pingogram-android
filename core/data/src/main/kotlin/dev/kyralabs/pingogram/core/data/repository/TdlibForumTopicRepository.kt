package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toForumTopic
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.data.mapper.toMessageContent
import dev.kyralabs.pingogram.core.data.mapper.toTdColor
import dev.kyralabs.pingogram.core.data.mapper.withInfo
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.ForumTopicList
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ForumTopicRepository] on TDLib's forum topics. Each forum someone observes has its loaded topics in memory
 * ([TopicCache]), dropped once nobody observes it any more. An observation starts from nothing and loads what it shows,
 * so it needs no update from before it: the repository is no `TdlibUpdateCache`.
 *
 * - **Pages** (`getForumTopics`, [PAGE_SIZE] topics from the offsets the page before gave) load one at a time per
 *   forum, shared by the callers that ask meanwhile.
 * - **Updates** of loaded topics: `updateForumTopicInfo` (name, icon, closed, hidden), `updateForumTopic` (pinned,
 *   read, mentions), a new message (`updateNewMessage`: the topic's last message, one more unread if incoming), a sent
 *   one getting its final id (`updateMessageSendSucceeded`) and an edited last message (`updateMessageContent`).
 * - **Fetched again** (`getForumTopic`), where no update says it all: a topic whose read state changed (TDLib reports
 *   no unread count with it), one whose last message was deleted, and one TDLib reports that is not loaded (a new one).
 * - **Managing** (`createForumTopic`, `editForumTopic`, `toggleForumTopicIsClosed`, `toggleForumTopicIsPinned`,
 *   `setPinnedForumTopics`, `deleteForumTopic`, `toggleGeneralForumTopicIsHidden`): TDLib's updates carry most
 *   changes back; what no update says is applied once TDLib took it: a new topic fetched, the pinned topics' new
 *   order, a deleted topic dropped. One function per change td_api.tl offers (hence `TooManyFunctions`).
 */
@Suppress("TooManyFunctions")
@Singleton
class TdlibForumTopicRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope private val scope: CoroutineScope,
    ) : ForumTopicRepository {
        private val store = UpdateStore(client, scope, TopicCache(), TopicCache::reduce)

        private val pageLocks = ConcurrentHashMap<Long, Mutex>()

        init {
            scope.launch { client.updates.collect { update -> refetchesFor(update).forEach(::refetchLater) } }
        }

        override fun topics(chatId: Long): Flow<ForumTopicList> =
            observing(chatId) { cache -> cache.forums[chatId]?.list() ?: EMPTY_LIST }

        override fun topic(
            chatId: Long,
            topicId: Int,
        ): Flow<ForumTopic?> =
            observing(chatId, fetch = topicId) { cache -> cache.forums[chatId]?.topics?.get(topicId) }

        override suspend fun loadMore(chatId: Long): Outcome<Unit> =
            pageLocks.computeIfAbsent(chatId) { Mutex() }.withLock {
                val next =
                    store.state.value.forums[chatId]
                        ?.next ?: PageOffsets.FIRST
                if (next == PageOffsets.END) return@withLock Outcome.Success(Unit)
                val request = TdApi.GetForumTopics(chatId, "", next.date, next.messageId, next.topicId, PAGE_SIZE)
                client.request(request).map { page -> store.update { cache -> cache.withPage(chatId, page) } }
            }

        override suspend fun createTopic(
            chatId: Long,
            name: String,
            color: TopicColor,
        ): Outcome<Int> {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return Outcome.Failure(DomainError.TitleInvalid)
            val icon = TdApi.ForumTopicIcon(color.toTdColor(), 0)
            return client.request(TdApi.CreateForumTopic(chatId, trimmed, false, icon)).map { info ->
                // Its updateForumTopicInfo may come before the forum is observed again; fetch it for the list.
                if (chatId in store.state.value.forums) refetchLater(chatId to info.forumTopicId)
                info.forumTopicId
            }
        }

        override suspend fun renameTopic(
            chatId: Long,
            topicId: Int,
            name: String,
        ): Outcome<Unit> {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return Outcome.Failure(DomainError.TitleInvalid)
            // editIconCustomEmoji false keeps the icon: Pingogram offers no custom emoji icons.
            return client.request(TdApi.EditForumTopic(chatId, topicId, trimmed, false, 0)).map {}
        }

        override suspend fun setClosed(
            chatId: Long,
            topicId: Int,
            isClosed: Boolean,
        ): Outcome<Unit> = client.request(TdApi.ToggleForumTopicIsClosed(chatId, topicId, isClosed)).map {}

        override suspend fun setPinned(
            chatId: Long,
            topicId: Int,
            isPinned: Boolean,
        ): Outcome<Unit> = client.request(TdApi.ToggleForumTopicIsPinned(chatId, topicId, isPinned)).map {}

        override suspend fun reorderPinned(
            chatId: Long,
            topicIds: List<Int>,
        ): Outcome<Unit> =
            client.request(TdApi.SetPinnedForumTopics(chatId, topicIds.toIntArray())).map {
                // TDLib reports the new order of no topic: put the loaded ones in it.
                store.update { cache -> cache.reordering(chatId, topicIds) }
            }

        override suspend fun deleteTopic(
            chatId: Long,
            topicId: Int,
        ): Outcome<Unit> =
            client.request(TdApi.DeleteForumTopic(chatId, topicId)).map {
                // No update says a topic is gone, only that its messages are.
                store.update { cache -> cache.removing(chatId, topicId) }
            }

        override suspend fun setGeneralHidden(
            chatId: Long,
            isHidden: Boolean,
        ): Outcome<Unit> = client.request(TdApi.ToggleGeneralForumTopicIsHidden(chatId, isHidden)).map {}

        /**
         * What [select] takes of the cache, then every change, while forum [chatId] counts one more observer; topic
         * [fetch] of it is fetched first when it is not loaded.
         */
        private fun <T> observing(
            chatId: Long,
            fetch: Int? = null,
            select: (TopicCache) -> T,
        ): Flow<T> =
            flow {
                store.update { cache -> cache.observing(chatId) }
                try {
                    if (fetch != null && store.state.value.forums[chatId]
                            ?.topics
                            ?.get(fetch) == null
                    ) {
                        refetch(chatId, fetch)
                    }
                    emitAll(store.state.map(select).distinctUntilChanged())
                } finally {
                    store.update { cache -> cache.releasing(chatId) }
                }
            }

        private fun refetchLater(target: Pair<Long, Int>) {
            scope.launch { refetch(target.first, target.second) }
        }

        /** `getForumTopic`, its answer kept if the forum is still observed; a failure keeps what was there. */
        private suspend fun refetch(
            chatId: Long,
            topicId: Int,
        ) {
            val topic = client.request(TdApi.GetForumTopic(chatId, topicId)).getOrNull()?.toForumTopic() ?: return
            store.update { cache -> cache.putting(topic) }
        }

        /** The loaded topics, and new ones, that [update] calls to fetch again, in observed forums only. */
        private fun refetchesFor(update: TdApi.Update): List<Pair<Long, Int>> {
            val forums = store.state.value.forums
            return when (update) {
                is TdApi.UpdateForumTopic -> {
                    listOfNotNull((update.chatId to update.forumTopicId).takeIf { update.chatId in forums })
                }

                is TdApi.UpdateForumTopicInfo -> {
                    val info = update.info
                    val loaded = forums[info.chatId]?.topics?.containsKey(info.forumTopicId)
                    listOfNotNull((info.chatId to info.forumTopicId).takeIf { loaded == false })
                }

                is TdApi.UpdateDeleteMessages -> {
                    val topics = forums[update.chatId]?.topics?.values.orEmpty()
                    val deleted = update.messageIds.toSet()
                    topics.filter { it.lastMessage?.id in deleted }.map { update.chatId to it.id }
                }

                else -> {
                    emptyList()
                }
            }
        }

        private companion object {
            /** Topics per page: a screen and a half of rows. */
            const val PAGE_SIZE = 20

            val EMPTY_LIST = ForumTopicList(persistentListOf(), reachedEnd = false)
        }
    }

/** Where the next page of a forum's topics starts (td_api.tl `forumTopics.next_offset_*`). */
internal data class PageOffsets(
    val date: Int,
    val messageId: Long,
    val topicId: Int,
) {
    companion object {
        val FIRST = PageOffsets(0, 0, 0)

        /** Every topic is loaded. */
        val END = PageOffsets(-1, -1, -1)
    }
}

/** The forums someone observes, by chat id. */
internal data class TopicCache(
    val forums: PersistentMap<Long, ForumState> = persistentMapOf(),
) {
    fun reduce(update: TdApi.Update): TopicCache =
        when (update) {
            is TdApi.UpdateForumTopicInfo -> {
                editing(update.info.chatId, update.info.forumTopicId) { it.withInfo(update.info) }
            }

            is TdApi.UpdateForumTopic -> {
                editing(update.chatId, update.forumTopicId) { topic ->
                    val read = update.lastReadInboxMessageId >= (topic.lastMessage?.id ?: 0)
                    topic.copy(
                        isPinned = update.isPinned,
                        lastReadInboxMessageId = update.lastReadInboxMessageId,
                        lastReadOutboxMessageId = update.lastReadOutboxMessageId,
                        unreadMentionCount = update.unreadMentionCount,
                        unreadCount = if (read) 0 else topic.unreadCount,
                    )
                }
            }

            is TdApi.UpdateNewMessage -> {
                val message = update.message
                val topicId = message.forumTopicId()
                if (topicId == null || message.schedulingState != null) {
                    this
                } else {
                    editing(message.chatId, topicId) { it.withNewMessage(message.toMessage()) }
                }
            }

            is TdApi.UpdateMessageSendSucceeded -> {
                val message = update.message
                val topicId = message.forumTopicId() ?: return this
                editing(message.chatId, topicId) { topic ->
                    val sent = topic.lastMessage?.id == update.oldMessageId
                    if (sent) topic.copy(lastMessage = message.toMessage()) else topic
                }
            }

            is TdApi.UpdateMessageContent -> {
                val topics = forums[update.chatId]?.topics?.values.orEmpty()
                topics.firstOrNull { it.lastMessage?.id == update.messageId }?.let { topic ->
                    editing(update.chatId, topic.id) { changed ->
                        val last = changed.lastMessage ?: return@editing changed
                        val content = update.newContent.toMessageContent(last.sender, last.isChannelPost)
                        changed.copy(lastMessage = last.copy(content = content))
                    }
                } ?: this
            }

            else -> {
                this
            }
        }

    /** One more observer of forum [chatId]; its state is created if missing. */
    fun observing(chatId: Long): TopicCache {
        val forum = forums[chatId] ?: ForumState()
        return copy(forums = forums.putting(chatId, forum.copy(observers = forum.observers + 1)))
    }

    /** One observer of forum [chatId] left; its topics go with the last one. */
    fun releasing(chatId: Long): TopicCache {
        val forum = forums[chatId] ?: return this
        return if (forum.observers <= 1) {
            copy(forums = forums.removing(chatId))
        } else {
            copy(forums = forums.putting(chatId, forum.copy(observers = forum.observers - 1)))
        }
    }

    /** The topics of [page] added to forum [chatId], with the offsets of the next page, while it is observed. */
    fun withPage(
        chatId: Long,
        page: TdApi.ForumTopics,
    ): TopicCache {
        val forum = forums[chatId] ?: return this
        val topics = page.topics.filterNotNull().map { it.toForumTopic() }
        val merged = forum.topics.putAll(topics.associateBy { it.id })
        val done = topics.isEmpty() || merged.size >= page.totalCount
        val next =
            if (done) {
                PageOffsets.END
            } else {
                PageOffsets(page.nextOffsetDate, page.nextOffsetMessageId, page.nextOffsetForumTopicId)
            }
        return copy(forums = forums.putting(chatId, forum.copy(topics = merged, next = next)))
    }

    /** [topic], fetched, in its forum while it is observed. */
    fun putting(topic: ForumTopic): TopicCache {
        val forum = forums[topic.chatId] ?: return this
        return copy(forums = forums.putting(topic.chatId, forum.copy(topics = forum.topics.putting(topic.id, topic))))
    }

    /** The loaded pinned topics of forum [chatId] in the order of [topicIds], once TDLib took it. */
    fun reordering(
        chatId: Long,
        topicIds: List<Int>,
    ): TopicCache {
        val forum = forums[chatId]
        val loaded = topicIds.mapNotNull { forum?.topics?.get(it) }
        if (forum == null || loaded.isEmpty()) return this
        // Orders only rank the pinned topics among themselves (ForumState.list): keep the highest one, rank the others
        // below it in the new order.
        val top = loaded.maxOf { it.order }
        val reordered = loaded.mapIndexed { index, topic -> topic.copy(order = top - index) }
        return copy(
            forums = forums.putting(chatId, forum.copy(topics = forum.topics.putAll(reordered.associateBy { it.id }))),
        )
    }

    /** Forum [chatId] without topic [topicId], deleted. */
    fun removing(
        chatId: Long,
        topicId: Int,
    ): TopicCache {
        val forum = forums[chatId] ?: return this
        return copy(forums = forums.putting(chatId, forum.copy(topics = forum.topics.removing(topicId))))
    }

    private inline fun editing(
        chatId: Long,
        topicId: Int,
        edit: (ForumTopic) -> ForumTopic,
    ): TopicCache {
        val forum = forums[chatId]
        val topic = forum?.topics?.get(topicId)
        val edited = topic?.let(edit)
        return if (forum == null || edited == null || edited == topic) {
            this
        } else {
            copy(forums = forums.putting(chatId, forum.copy(topics = forum.topics.putting(topicId, edited))))
        }
    }
}

/**
 * One observed forum: its loaded [topics] by id, where the next page starts ([next], [PageOffsets.END] once every
 * topic is loaded; null before the first page), and how many observe it.
 */
internal data class ForumState(
    val topics: PersistentMap<Int, ForumTopic> = persistentMapOf(),
    val next: PageOffsets? = null,
    val observers: Int = 0,
) {
    /**
     * The topics in the forum's order: General first (also when the administrators hid it, which the list shows only
     * to those who may show it again), then the pinned ones in TDLib's order, then the others by their newest message.
     */
    fun list(): ForumTopicList {
        val sorted =
            topics.values.sortedWith(
                compareByDescending<ForumTopic> { it.isGeneral }
                    .thenByDescending { it.isPinned }
                    .thenByDescending { if (it.isPinned) it.order else it.lastMessage?.id ?: 0 },
            )
        return ForumTopicList(sorted.toImmutableList(), reachedEnd = next == PageOffsets.END)
    }
}

/** The forum topic [this] message is in; null for a message without one. */
private fun TdApi.Message.forumTopicId(): Int? =
    when (val topic = topicId) {
        is TdApi.MessageTopicForum -> topic.forumTopicId
        else -> null
    }

/** [this] topic after [message]: its last message if newer, one more unread if incoming. */
private fun ForumTopic.withNewMessage(message: Message): ForumTopic {
    val newer = message.id > (lastMessage?.id ?: 0)
    if (!newer) return this
    val unread = if (message.isOutgoing || message.sendingState != null) unreadCount else unreadCount + 1
    return copy(lastMessage = message, unreadCount = unread)
}
