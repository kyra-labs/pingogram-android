package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.ForumTopicList
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * The forums' unread topics for the chat list ([unreadTopics], by chat id; null for a forum not counted yet), with the
 * forums whose count is observed now ([observed]) and every forum ever observed ([requested]); the rest is unused.
 */
class FakeForumTopicRepository : ForumTopicRepository {
    val unreadTopics = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val observed = mutableSetOf<Long>()
    val requested = mutableListOf<Long>()

    override fun unreadTopicCount(chatId: Long): Flow<Int?> =
        unreadTopics
            .map { it[chatId] }
            .distinctUntilChanged()
            .onStart {
                observed += chatId
                requested += chatId
            }.onCompletion { observed -= chatId }

    override fun topics(chatId: Long): Flow<ForumTopicList> = unused()

    override suspend fun loadMore(chatId: Long): Outcome<Unit> = unused()

    override fun topic(
        chatId: Long,
        topicId: Int,
    ): Flow<ForumTopic?> = unused()

    override suspend fun createTopic(
        chatId: Long,
        name: String,
        color: TopicColor,
    ): Outcome<Int> = unused()

    override suspend fun renameTopic(
        chatId: Long,
        topicId: Int,
        name: String,
    ): Outcome<Unit> = unused()

    override suspend fun setClosed(
        chatId: Long,
        topicId: Int,
        isClosed: Boolean,
    ): Outcome<Unit> = unused()

    override suspend fun setPinned(
        chatId: Long,
        topicId: Int,
        isPinned: Boolean,
    ): Outcome<Unit> = unused()

    override suspend fun reorderPinned(
        chatId: Long,
        topicIds: List<Int>,
    ): Outcome<Unit> = unused()

    override suspend fun deleteTopic(
        chatId: Long,
        topicId: Int,
    ): Outcome<Unit> = unused()

    override suspend fun setGeneralHidden(
        chatId: Long,
        isHidden: Boolean,
    ): Outcome<Unit> = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("Not used by the chat list")
}
