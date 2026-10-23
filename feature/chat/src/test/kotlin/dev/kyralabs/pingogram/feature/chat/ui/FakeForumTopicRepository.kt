package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.ForumTopicList
import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The topics of each forum in memory, in the forum's order, with whether they are all loaded, what [loadMore] answers
 * (by default it changes nothing), and every [loadMore] call.
 */
class FakeForumTopicRepository : ForumTopicRepository {
    val forums = MutableStateFlow<Map<Long, ForumTopicList>>(emptyMap())

    /** The forum of every [loadMore] call. */
    val loadRequests = mutableListOf<Long>()

    var onLoadMore: suspend (Long) -> Outcome<Unit> = { Outcome.Success(Unit) }

    fun setTopics(
        chatId: Long,
        vararg topics: ForumTopic,
        reachedEnd: Boolean = true,
    ) {
        forums.value += chatId to ForumTopicList(topics.toList().toImmutableList(), reachedEnd)
    }

    override fun topics(chatId: Long): Flow<ForumTopicList> =
        forums.map { it[chatId] ?: ForumTopicList(persistentListOf(), reachedEnd = false) }.distinctUntilChanged()

    override suspend fun loadMore(chatId: Long): Outcome<Unit> {
        loadRequests += chatId
        return onLoadMore(chatId)
    }

    override fun topic(
        chatId: Long,
        topicId: Int,
    ): Flow<ForumTopic?> =
        forums
            .map { forum ->
                forum[chatId]?.topics?.firstOrNull { it.id == topicId }
            }.distinctUntilChanged()
}
