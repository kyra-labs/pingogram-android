package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ForumTopicList
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewMessage
import dev.kyralabs.pingogram.core.testing.inTopic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The topics of a forum: pages, their order, TDLib's updates, single topics, forgetting a forum nobody observes. */
class TdlibForumTopicRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibForumTopicRepository(client, backgroundScope)

    private fun info(
        id: Int,
        name: String = "Topic $id",
        isGeneral: Boolean = false,
        isClosed: Boolean = false,
        isHidden: Boolean = false,
        color: Int = 0x6FB9F0,
    ) = TdApi.ForumTopicInfo(
        FORUM,
        id,
        name,
        TdApi.ForumTopicIcon(color, 0),
        0,
        TdApi.MessageSenderUser(1),
        isGeneral,
        false,
        isClosed,
        isHidden,
        false,
    )

    private fun topic(
        id: Int,
        lastMessageId: Long = id * 10L,
        isPinned: Boolean = false,
        order: Long = 0,
        unread: Int = 0,
        info: TdApi.ForumTopicInfo = info(id),
    ) = TdApi.ForumTopic(
        info,
        message(id = lastMessageId, chatId = FORUM).inTopic(TdApi.MessageTopicForum(id)),
        order,
        isPinned,
        unread,
        0,
        0,
        0,
        0,
        0,
        TdApi.ChatNotificationSettings(),
        null,
    )

    private fun page(
        vararg topics: TdApi.ForumTopic,
        total: Int = topics.size,
        next: Int = 0,
    ) = TdResult.Success(TdApi.ForumTopics(total, arrayOf(*topics), next, next.toLong(), next))

    /** Observes the forum's topics in the background, as a screen does, and returns the latest list each time. */
    private fun TestScope.observed(repository: TdlibForumTopicRepository): () -> ForumTopicList {
        var latest: ForumTopicList? = null
        backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler),
        ) { repository.topics(FORUM).collect { latest = it } }
        return { checkNotNull(latest) }
    }

    @Test
    fun `loads the first page in the forum's order`() =
        runTest {
            client.on(
                TdApi.GetForumTopics::class,
                page(
                    topic(5, lastMessageId = 90),
                    topic(3, lastMessageId = 20, isPinned = true, order = 9),
                    topic(1, info = info(1, isGeneral = true), lastMessageId = 10),
                    topic(4, lastMessageId = 95),
                ),
            )
            val repository = repository()
            val topics = observed(repository)

            assertEquals(Outcome.Success(Unit), repository.loadMore(FORUM))

            assertEquals(listOf(1, 3, 4, 5), topics().topics.map { it.id })
            assertTrue(topics().reachedEnd)
            val request = client.sentOf<TdApi.GetForumTopics>().single()
            assertEquals(
                listOf(0L, 0L, 0L, 20L),
                listOf(
                    request.offsetDate.toLong(),
                    request.offsetMessageId,
                    request.offsetForumTopicId.toLong(),
                    request.limit.toLong(),
                ),
            )
        }

    @Test
    fun `the next page starts where the one before ended, until every topic is loaded`() =
        runTest {
            client.on(TdApi.GetForumTopics::class) { request ->
                if (request.offsetDate == 0) page(topic(9), total = 2, next = 77) else page(topic(8), total = 2)
            }
            val repository = repository()
            val topics = observed(repository)

            repository.loadMore(FORUM)
            assertEquals(false, topics().reachedEnd)
            repository.loadMore(FORUM)
            repository.loadMore(FORUM)

            assertEquals(listOf(9, 8), topics().topics.map { it.id })
            assertTrue(topics().reachedEnd)
            assertEquals(listOf(0, 77), client.sentOf<TdApi.GetForumTopics>().map { it.offsetDate })
        }

    @Test
    fun `a General topic the administrators hid is left out`() =
        runTest {
            client.on(
                TdApi.GetForumTopics::class,
                page(topic(1, info = info(1, isGeneral = true, isHidden = true)), topic(2)),
            )
            val repository = repository()
            val topics = observed(repository)

            repository.loadMore(FORUM)

            assertEquals(listOf(2), topics().topics.map { it.id })
        }

    @Test
    fun `follows the name, the icon and the state, and a new message`() =
        runTest {
            client.on(TdApi.GetForumTopics::class, page(topic(2)))
            val repository = repository()
            val topics = observed(repository)
            repository.loadMore(FORUM)

            client.emit(TdApi.UpdateForumTopicInfo(info(2, name = "Renamed", isClosed = true, color = 0xFB6F5F)))
            client.emit(updateNewMessage(message(id = 50, chatId = FORUM).inTopic(TdApi.MessageTopicForum(2))))

            val topic = topics().topics.single()
            assertEquals(
                listOf<Any>("Renamed", true, TopicColor.Red),
                listOf(topic.name, topic.isClosed, topic.icon.color),
            )
            assertEquals(50L, topic.lastMessage?.id)
            assertEquals(1, topic.unreadCount)
        }

    @Test
    fun `fetches a topic again when its read state changes`() =
        runTest {
            client.on(TdApi.GetForumTopics::class, page(topic(2, unread = 4)))
            client.on(TdApi.GetForumTopic::class, TdResult.Success(topic(2, unread = 1)))
            val repository = repository()
            val topics = observed(repository)
            repository.loadMore(FORUM)

            client.emit(TdApi.UpdateForumTopic(FORUM, 2, false, 15, 0, 0, 0, 0, TdApi.ChatNotificationSettings(), null))
            runCurrent()

            assertEquals(1, topics().topics.single().unreadCount)
            assertEquals(2, client.sentOf<TdApi.GetForumTopic>().single().forumTopicId)
        }

    @Test
    fun `a topic not loaded is fetched for its conversation`() =
        runTest {
            client.on(TdApi.GetForumTopic::class, TdResult.Success(topic(6, info = info(6, name = "Help"))))
            val repository = repository()

            repository.topic(FORUM, 6).test {
                assertEquals("Help", awaitItem()?.name)
            }
        }

    @Test
    fun `a forum nobody observes any more is forgotten`() =
        runTest {
            client.on(TdApi.GetForumTopics::class, page(topic(2)))
            val repository = repository()

            repository.topics(FORUM).test {
                awaitItem()
                repository.loadMore(FORUM)
                assertEquals(1, awaitItem().topics.size)
            }

            assertTrue(
                repository
                    .topics(FORUM)
                    .first()
                    .topics
                    .isEmpty(),
            )
        }

    private companion object {
        const val FORUM = -100L
    }
}
