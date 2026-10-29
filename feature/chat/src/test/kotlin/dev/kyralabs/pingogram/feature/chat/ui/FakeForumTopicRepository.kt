package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.ForumTopicList
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The topics of each forum in memory, in the forum's order, with whether they are all loaded, what [loadMore] answers
 * (by default it changes nothing), and every [loadMore] call; the topics managed ([requests], as text), answered by
 * [onManage] (success by default; [createTopic] answers [createdId]).
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

    /** Every managing call, e.g. "create Plans Rose", "close 7 true", "reorder [5, 3]". */
    val requests = mutableListOf<String>()

    /** What a managing call answers, by its text; success by default. */
    var onManage: suspend (String) -> Outcome<Unit> = { Outcome.Success(Unit) }

    var createdId = 99

    private suspend fun manage(request: String): Outcome<Unit> {
        requests += request
        return onManage(request)
    }

    override suspend fun createTopic(
        chatId: Long,
        name: String,
        color: TopicColor,
    ): Outcome<Int> =
        when (val result = manage("create $name $color")) {
            is Outcome.Success -> Outcome.Success(createdId)
            is Outcome.Failure -> result
        }

    override suspend fun renameTopic(
        chatId: Long,
        topicId: Int,
        name: String,
    ) = manage("rename $topicId $name")

    override suspend fun setClosed(
        chatId: Long,
        topicId: Int,
        isClosed: Boolean,
    ) = manage("close $topicId $isClosed")

    override suspend fun setPinned(
        chatId: Long,
        topicId: Int,
        isPinned: Boolean,
    ) = manage("pin $topicId $isPinned")

    override suspend fun reorderPinned(
        chatId: Long,
        topicIds: List<Int>,
    ) = manage("reorder $topicIds")

    override suspend fun deleteTopic(
        chatId: Long,
        topicId: Int,
    ) = manage("delete $topicId")

    override suspend fun setGeneralHidden(
        chatId: Long,
        isHidden: Boolean,
    ) = manage("hide general $isHidden")
}
