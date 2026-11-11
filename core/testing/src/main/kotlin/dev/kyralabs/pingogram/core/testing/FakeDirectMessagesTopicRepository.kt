package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DirectMessagesTopic
import dev.kyralabs.pingogram.core.domain.repository.DirectMessagesTopicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [DirectMessagesTopicRepository] in memory: [topicList] is what TDLib announced, listed by `order` as TDLib's;
 * [loadMore] answers [loadAnswer] and [setMarkedAsUnread] [markAnswer], each recorded in [calls] (`load:<chat>`,
 * `unread:<chat>:<topic>:<flag>`); a mark that succeeds changes the topic.
 */
class FakeDirectMessagesTopicRepository(
    topics: List<DirectMessagesTopic> = emptyList(),
) : DirectMessagesTopicRepository {
    val topicList = MutableStateFlow(topics)

    val calls = mutableListOf<String>()

    var loadAnswer: Outcome<Boolean> = Outcome.Success(false)

    var markAnswer: Outcome<Unit> = Outcome.Success(Unit)

    override fun topics(chatId: Long): Flow<ImmutableList<DirectMessagesTopic>> =
        topicList.map { all -> all.filter { it.chatId == chatId }.sortedByDescending { it.order }.toImmutableList() }

    override fun topic(
        chatId: Long,
        topicId: Long,
    ): Flow<DirectMessagesTopic?> = topicList.map { all -> all.firstOrNull { it.chatId == chatId && it.id == topicId } }

    override suspend fun loadMore(chatId: Long): Outcome<Boolean> {
        calls += "load:$chatId"
        return loadAnswer
    }

    override suspend fun setMarkedAsUnread(
        chatId: Long,
        topicId: Long,
        unread: Boolean,
    ): Outcome<Unit> {
        calls += "unread:$chatId:$topicId:$unread"
        val answer = markAnswer
        if (answer is Outcome.Success) {
            topicList.value =
                topicList.value.map {
                    if (it.chatId == chatId && it.id == topicId) it.copy(isMarkedAsUnread = unread) else it
                }
        }
        return answer
    }
}
