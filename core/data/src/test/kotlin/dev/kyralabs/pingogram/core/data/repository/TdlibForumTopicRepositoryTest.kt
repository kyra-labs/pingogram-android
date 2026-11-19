package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.ForumTopicList
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewMessage
import dev.kyralabs.pingogram.core.testing.inTopic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The topics of a forum: pages, their order, TDLib's updates, single topics, forgetting a forum nobody observes; and
 * managing them: creating, renaming, closing, pinning and reordering, deleting, hiding General, and the refusals.
 */
@OptIn(ExperimentalCoroutinesApi::class)
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
        isOutgoing: Boolean = false,
    ) = TdApi.ForumTopicInfo(
        FORUM,
        id,
        name,
        TdApi.ForumTopicIcon(color, 0),
        0,
        TdApi.MessageSenderUser(1),
        isGeneral,
        isOutgoing,
        isClosed,
        isHidden,
        false,
    )

    private fun topic(
        id: Int,
        lastMessageId: Long? = id * 10L,
        isPinned: Boolean = false,
        order: Long = 0,
        unread: Int = 0,
        info: TdApi.ForumTopicInfo = info(id),
    ) = TdApi.ForumTopic(
        info,
        lastMessageId?.let { message(id = it, chatId = FORUM).inTopic(TdApi.MessageTopicForum(id)) },
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
    fun `pinned topics keep the pages' pin order, whatever their order values`() =
        runTest {
            client.on(
                TdApi.GetForumTopics::class,
                page(
                    topic(1, info = info(1, isGeneral = true)),
                    topic(20, isPinned = true, order = 1, lastMessageId = 10),
                    topic(32, isPinned = true, order = 5, lastMessageId = 90),
                    topic(64, isPinned = true, order = 9, lastMessageId = 95),
                    topic(7, lastMessageId = 99),
                ),
            )
            val repository = repository()
            val topics = observed(repository)

            repository.loadMore(FORUM)

            assertEquals(listOf(1, 20, 32, 64, 7), topics().topics.map { it.id })
        }

    @Test
    fun `a hidden General left out of the pages is fetched, as every forum has one`() =
        runTest {
            client.on(TdApi.GetForumTopics::class, page(topic(5), topic(4)))
            client.on(TdApi.GetForumTopic::class) {
                TdResult.Success(
                    topic(1, info = info(1, isGeneral = true, isClosed = true, isHidden = true), unread = 2),
                )
            }
            val repository = repository()
            val topics = observed(repository)

            repository.loadMore(FORUM)

            val general = topics().topics.first()
            assertTrue(general.isGeneral && general.isHidden)
            assertEquals(2, general.unreadCount)
            assertEquals(1, client.sentOf<TdApi.GetForumTopic>().single().forumTopicId)
        }

    @Test
    fun `a topic's own mute is kept, and one that follows the forum has none`() =
        runTest {
            val muted =
                topic(5).apply {
                    notificationSettings =
                        TdApi.ChatNotificationSettings().apply { muteFor = 3600 }
                }
            val following =
                topic(4).apply {
                    notificationSettings =
                        TdApi.ChatNotificationSettings().apply { useDefaultMuteFor = true }
                }
            client.on(TdApi.GetForumTopics::class, page(muted, following))
            val repository = repository()
            val topics = observed(repository)

            repository.loadMore(FORUM)

            assertEquals(3600, topics().topics.single { it.id == 5 }.muteFor)
            assertEquals(null, topics().topics.single { it.id == 4 }.muteFor)
        }

    @Test
    fun `lists the first page's topics newest first, once it holds the forum's unread messages`() =
        runTest {
            client.on(
                TdApi.GetForumTopics::class,
                page(
                    topic(5, unread = 3, lastMessageId = 50),
                    topic(4, lastMessageId = 60),
                    topic(3, unread = 120, lastMessageId = 70),
                    total = 9,
                    next = 7,
                ),
            )
            val repository = repository()

            repository.recentTopics(FORUM, flowOf(123)).test {
                assertEquals(null, awaitItem())
                assertEquals(listOf(3, 4, 5), awaitItem()?.map { it.id })
            }
            // The loaded topics hold the forum's 123 unread messages: no further page to load.
            assertEquals(1, client.sentOf<TdApi.GetForumTopics>().size)
        }

    @Test
    fun `a topic without a last message comes last among the recent ones`() =
        runTest {
            client.on(
                TdApi.GetForumTopics::class,
                page(topic(5, lastMessageId = 50), topic(4, lastMessageId = null), topic(3, lastMessageId = 70)),
            )

            repository().recentTopics(FORUM, flowOf(0)).test {
                assertEquals(null, awaitItem())
                assertEquals(listOf(3, 5, 4), awaitItem()?.map { it.id })
            }
        }

    @Test
    fun `loads past a read topic until the loaded topics hold the forum's unread messages`() =
        runTest {
            var pages = 0
            client.on(TdApi.GetForumTopics::class) {
                pages++
                when (pages) {
                    // The newest topics are read; the unread ones come further down.
                    1 -> page(topic(10), topic(11), topic(12, unread = 2), total = 9, next = 1)

                    2 -> page(topic(20), topic(21, unread = 4), total = 9, next = 2)

                    else -> page(topic(30, unread = 1), topic(31), total = 9, next = 3)
                }
            }
            val repository = repository()
            var latest: List<ForumTopic>? = null

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.recentTopics(FORUM, flowOf(7)).collect { latest = it }
            }
            runCurrent()

            assertEquals(setOf(12, 21, 30), latest?.filter { it.unreadCount > 0 }?.map { it.id }?.toSet())
            // The list's first page, then pages of 100 topics, td_api.tl's most, to find them in few requests.
            assertEquals(listOf(20, 100, 100), client.sentOf<TdApi.GetForumTopics>().map { it.limit })
        }

    @Test
    fun `loads at most five more pages to find the unread topics`() =
        runTest {
            var pages = 0
            client.on(TdApi.GetForumTopics::class) {
                pages++
                page(topic(pages * 10, unread = 1), topic(pages * 10 + 1, unread = 1), total = 1000, next = pages)
            }
            val repository = repository()
            var latest: List<ForumTopic>? = null

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.recentTopics(FORUM, flowOf(1000)).collect { latest = it }
            }
            runCurrent()

            assertEquals(12, latest?.size)
            assertEquals(6, client.sentOf<TdApi.GetForumTopics>().size)
        }

    @Test
    fun `loads more once the forum's unread messages grow past the loaded topics`() =
        runTest {
            var pages = 0
            client.on(TdApi.GetForumTopics::class) {
                pages++
                if (pages == 1) {
                    page(topic(10, unread = 2), topic(11), total = 9, next = 1)
                } else {
                    page(topic(20, unread = 3), total = 9, next = 2)
                }
            }
            val repository = repository()
            val unreadMessages = MutableStateFlow(2)
            var latest: List<ForumTopic>? = null

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.recentTopics(FORUM, unreadMessages).collect { latest = it }
            }
            runCurrent()
            assertEquals(setOf(10, 11), latest?.map { it.id }?.toSet())

            unreadMessages.value = 5
            runCurrent()

            assertEquals(setOf(10, 11, 20), latest?.map { it.id }?.toSet())
            assertEquals(2, client.sentOf<TdApi.GetForumTopics>().size)
        }

    @Test
    fun `the unread count follows a topic read and a new message`() =
        runTest {
            client.on(TdApi.GetForumTopics::class, page(topic(5, unread = 3), topic(4)))
            // Topic 5 read elsewhere; General, which the first page leaves out, has nothing unread.
            client.on(TdApi.GetForumTopic::class) { request ->
                TdResult.Success(
                    if (request.forumTopicId == 5) topic(5, unread = 0) else topic(1, info = info(1, isGeneral = true)),
                )
            }
            val repository = repository()

            repository.recentTopics(FORUM, flowOf(3)).map { topics -> topics?.count { it.unreadCount > 0 } }.test {
                assertEquals(null, awaitItem())
                assertEquals(1, awaitItem())

                client.emit(updateNewMessage(message(id = 41, chatId = FORUM).inTopic(TdApi.MessageTopicForum(4))))
                assertEquals(2, awaitItem())

                client.emit(
                    TdApi.UpdateForumTopic(FORUM, 5, false, 50, 0, 0, 0, 0, TdApi.ChatNotificationSettings(), null),
                )
                assertEquals(1, awaitItem())
            }
        }

    @Test
    fun `a forum whose topics cannot be loaded has no count`() =
        runTest {
            client.on(TdApi.GetForumTopics::class, TdResult.Failure(TdError(400, "CHANNEL_PRIVATE")))
            val repository = repository()

            repository.recentTopics(FORUM, flowOf(3)).test {
                assertEquals(null, awaitItem())
                runCurrent()
                expectNoEvents()
            }
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
                    topic(6, lastMessageId = null),
                ),
            )
            val repository = repository()
            val topics = observed(repository)

            assertEquals(Outcome.Success(Unit), repository.loadMore(FORUM))

            assertEquals(listOf(1, 3, 4, 5, 6), topics().topics.map { it.id })
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
    fun `a General topic the administrators hid stays in the list, marked hidden, for those who may show it`() =
        runTest {
            client.on(
                TdApi.GetForumTopics::class,
                page(
                    topic(1, info = info(1, isGeneral = true, isHidden = true)),
                    topic(2, info = info(2, isOutgoing = true)),
                ),
            )
            val repository = repository()
            val topics = observed(repository)

            repository.loadMore(FORUM)

            assertEquals(listOf(1, 2), topics().topics.map { it.id })
            assertTrue(topics().topics[0].isHidden)
            assertEquals(listOf(false, true), topics().topics.map { it.isOutgoing })
        }

    @Test
    fun `creates a topic with its trimmed name and Telegram's color, and fetches it for the list`() =
        runTest {
            client.on(TdApi.GetForumTopics::class, page(topic(2)))
            client.on(TdApi.CreateForumTopic::class, TdResult.Success(info(9, name = "Plans")))
            client.on(TdApi.GetForumTopic::class, TdResult.Success(topic(9, info = info(9, name = "Plans"))))
            val repository = repository()
            val topics = observed(repository)
            repository.loadMore(FORUM)

            assertEquals(Outcome.Success(9), repository.createTopic(FORUM, "  Plans ", TopicColor.Rose))
            runCurrent()

            val request = client.sentOf<TdApi.CreateForumTopic>().single()
            assertEquals(
                listOf<Any>(FORUM, "Plans", false),
                listOf(request.chatId, request.name, request.isNameImplicit),
            )
            assertEquals(0xFF93B2, request.icon.color)
            assertEquals(0L, request.icon.customEmojiId)
            assertEquals(setOf(2, 9), topics().topics.map { it.id }.toSet())
        }

    @Test
    fun `a blank name is refused before TDLib is asked`() =
        runTest {
            val repository = repository()

            assertEquals(Outcome.Failure(DomainError.TitleInvalid), repository.createTopic(FORUM, " ", TopicColor.Blue))
            assertEquals(Outcome.Failure(DomainError.TitleInvalid), repository.renameTopic(FORUM, 2, ""))
            assertTrue(client.sent.isEmpty())
        }

    @Test
    fun `renames a topic, keeping its icon`() =
        runTest {
            client.on(TdApi.EditForumTopic::class, TdResult.Success(TdApi.Ok()))
            val repository = repository()

            assertEquals(Outcome.Success(Unit), repository.renameTopic(FORUM, 2, "Help desk "))

            val request = client.sentOf<TdApi.EditForumTopic>().single()
            assertEquals(listOf<Any>(FORUM, 2, "Help desk"), listOf(request.chatId, request.forumTopicId, request.name))
            assertFalse(request.editIconCustomEmoji)
        }

    @Test
    fun `closes, pins and hides General through their functions`() =
        runTest {
            client.on(TdApi.ToggleForumTopicIsClosed::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.ToggleForumTopicIsPinned::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.ToggleGeneralForumTopicIsHidden::class, TdResult.Success(TdApi.Ok()))
            val repository = repository()

            assertEquals(Outcome.Success(Unit), repository.setClosed(FORUM, 2, isClosed = true))
            assertEquals(Outcome.Success(Unit), repository.setPinned(FORUM, 3, isPinned = false))
            assertEquals(Outcome.Success(Unit), repository.setGeneralHidden(FORUM, isHidden = true))

            val closed = client.sentOf<TdApi.ToggleForumTopicIsClosed>().single()
            assertEquals(listOf<Any>(FORUM, 2, true), listOf(closed.chatId, closed.forumTopicId, closed.isClosed))
            val pinned = client.sentOf<TdApi.ToggleForumTopicIsPinned>().single()
            assertEquals(listOf<Any>(FORUM, 3, false), listOf(pinned.chatId, pinned.forumTopicId, pinned.isPinned))
            val hidden = client.sentOf<TdApi.ToggleGeneralForumTopicIsHidden>().single()
            assertEquals(listOf<Any>(FORUM, true), listOf(hidden.chatId, hidden.isHidden))
        }

    @Test
    fun `reorders the pinned topics, and the list takes the order once TDLib took it`() =
        runTest {
            client.on(
                TdApi.GetForumTopics::class,
                page(topic(3, isPinned = true, order = 9), topic(4, isPinned = true, order = 8), topic(5)),
            )
            client.on(TdApi.SetPinnedForumTopics::class, TdResult.Success(TdApi.Ok()))
            val repository = repository()
            val topics = observed(repository)
            repository.loadMore(FORUM)

            assertEquals(Outcome.Success(Unit), repository.reorderPinned(FORUM, listOf(4, 3)))

            assertArrayEquals(intArrayOf(4, 3), client.sentOf<TdApi.SetPinnedForumTopics>().single().forumTopicIds)
            assertEquals(listOf(4, 3, 5), topics().topics.map { it.id })
        }

    @Test
    fun `deletes a topic, which leaves the list`() =
        runTest {
            client.on(TdApi.GetForumTopics::class, page(topic(2), topic(3)))
            client.on(TdApi.DeleteForumTopic::class, TdResult.Success(TdApi.Ok()))
            val repository = repository()
            val topics = observed(repository)
            repository.loadMore(FORUM)

            assertEquals(Outcome.Success(Unit), repository.deleteTopic(FORUM, 2))

            assertEquals(2, client.sentOf<TdApi.DeleteForumTopic>().single().forumTopicId)
            assertEquals(listOf(3), topics().topics.map { it.id })
        }

    @Test
    fun `refusals keep the list as it was and map to their errors`() =
        runTest {
            client.on(
                TdApi.GetForumTopics::class,
                page(topic(3, isPinned = true, order = 9), topic(4, isPinned = true, order = 8)),
            )
            client.on(TdApi.SetPinnedForumTopics::class, TdResult.Failure(TdError(400, "CHAT_ADMIN_REQUIRED")))
            client.on(TdApi.DeleteForumTopic::class, TdResult.Failure(TdError(400, "TOPIC_DELETED")))
            client.on(
                TdApi.CreateForumTopic::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 12")),
            )
            client.on(TdApi.ToggleForumTopicIsClosed::class, TdResult.Failure(TdError(400, "Not enough rights")))
            val repository = repository()
            val topics = observed(repository)
            repository.loadMore(FORUM)

            assertEquals(
                Outcome.Failure(DomainError.AdminRightsRequired),
                repository.reorderPinned(FORUM, listOf(4, 3)),
            )
            assertEquals(Outcome.Failure(DomainError.NotFound), repository.deleteTopic(FORUM, 3))
            assertEquals(
                Outcome.Failure(DomainError.FloodWait(12)),
                repository.createTopic(FORUM, "New", TopicColor.Red),
            )
            assertEquals(
                Outcome.Failure(DomainError.AdminRightsRequired),
                repository.setClosed(FORUM, 4, isClosed = true),
            )
            assertEquals(listOf(3, 4), topics().topics.map { it.id })
        }

    @Test
    fun `an answer that does not come in time fails as a network error`() =
        runTest {
            client.on(TdApi.ToggleForumTopicIsPinned::class) {
                awaitCancellation()
            }
            val repository = repository()

            assertEquals(Outcome.Failure(DomainError.Network), repository.setPinned(FORUM, 2, isPinned = true))
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
            client.on(
                TdApi.GetForumTopics::class,
                page(topic(1, info = info(1, isGeneral = true)), topic(2, unread = 4)),
            )
            client.on(TdApi.GetForumTopic::class, TdResult.Success(topic(2, unread = 1)))
            val repository = repository()
            val topics = observed(repository)
            repository.loadMore(FORUM)

            client.emit(TdApi.UpdateForumTopic(FORUM, 2, false, 15, 0, 0, 0, 0, TdApi.ChatNotificationSettings(), null))
            runCurrent()

            assertEquals(1, topics().topics.single { it.id == 2 }.unreadCount)
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
