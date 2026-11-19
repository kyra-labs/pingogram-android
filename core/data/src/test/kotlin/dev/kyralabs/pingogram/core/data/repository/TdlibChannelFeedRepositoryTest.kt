package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChannelFeed
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.isUnreadPost
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chatPosition
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [TdlibChannelFeedRepository] on a [FakeTdlibClient] and the real chat repository: the channels' last posts before
 * anything is read, each channel's newest posts merged newest first, TDLib's short first answer, a channel's new post
 * read when its last post changes, read states, seeing posts, and failures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TdlibChannelFeedRepositoryTest {
    /** What each channel's history holds, newest first, by chat id. */
    private val histories = mutableMapOf<Long, List<TdApi.Message>>()

    /** How many messages TDLib gives at most for a call from the newest; fewer than asked mimics its short answer. */
    private var firstCall = Int.MAX_VALUE

    private val failing = mutableSetOf<Long>()

    private val client =
        FakeTdlibClient().apply {
            on(TdApi.GetChatHistory::class) { request ->
                if (request.chatId in failing) return@on TdResult.Failure(TdError(500, "Request aborted"))
                val history = histories[request.chatId].orEmpty()
                val from =
                    if (request.fromMessageId == 0L) history else history.dropWhile { it.id > request.fromMessageId }
                val most = if (request.fromMessageId == 0L) minOf(request.limit, firstCall) else request.limit
                val page = from.take(most).toTypedArray()
                TdResult.Success(TdApi.Messages(page.size, page))
            }
            on(TdApi.ViewMessages::class) { TdResult.Success(TdApi.Ok()) }
            on(TdApi.GetMessages::class) { request ->
                if (request.chatId in failing) return@on TdResult.Failure(TdError(500, "Request aborted"))
                val history = histories[request.chatId].orEmpty()
                val found = request.messageIds.map { id -> history.firstOrNull { it.id == id } }.toTypedArray()
                TdResult.Success(TdApi.Messages(found.count { it != null }, found))
            }
        }

    private fun TestScope.repository() =
        TdlibChannelFeedRepository(
            client,
            TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats()),
        )

    private fun post(
        chatId: Long,
        id: Long,
    ) = message(id = id, chatId = chatId, date = id.toInt())

    /** Channel [id] with [posts], newest first, the newest its last message; posts up to [lastRead] read. */
    private suspend fun TestScope.channel(
        id: Long,
        vararg posts: Long,
        lastRead: Long = 0,
    ) {
        histories[id] = posts.map { post(id, it) }
        val last = histories.getValue(id).firstOrNull()
        client.emit(
            updateNewChat(
                chat(
                    id = id,
                    type = TdApi.ChatTypeSupergroup(id, true),
                    title = "Channel $id",
                    lastMessage = last,
                    lastReadInboxMessageId = lastRead,
                ),
            ),
        )
        runCurrent()
    }

    private fun ChannelFeed.ids() = posts.map { it.chat.id to it.message.id }

    /** The feed, collected as the screen does, so that it follows TDLib's updates. */
    private fun TestScope.collected(repository: TdlibChannelFeedRepository): () -> ChannelFeed {
        var latest = ChannelFeed(persistentListOf(), 0)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.feed.collect { latest = it } }
        runCurrent()
        return { latest }
    }

    private fun ChannelFeed.post(id: Long) = posts.single { it.message.id == id }.message

    @Test
    fun `a post follows TDLib's updates of its views, edit and content while the feed is collected`() =
        runTest {
            val repository = repository()
            channel(1, 12, 11)
            repository.load()
            val feed = collected(repository)

            client.emit(TdApi.UpdateMessageInteractionInfo(1, 11, TdApi.MessageInteractionInfo(42, 0, null, null)))
            client.emit(TdApi.UpdateMessageEdited(1, 11, 500, null))
            client.emit(
                TdApi.UpdateMessageContent(
                    1,
                    12,
                    TdApi.MessageText(TdApi.FormattedText("Edited", emptyArray()), null, null),
                ),
            )
            runCurrent()

            assertEquals(42, feed().post(11).interactionInfo?.viewCount)
            assertEquals(500L, feed().post(11).editDate)
            assertEquals("Edited", (feed().post(12).content as MessageContent.Text).text.text)
        }

    @Test
    fun `a deleted post leaves the feed, one TDLib only unloaded stays`() =
        runTest {
            val repository = repository()
            channel(1, 12, 11, 10)
            repository.load()
            val feed = collected(repository)

            client.emit(TdApi.UpdateDeleteMessages(1, longArrayOf(11), true, false))
            client.emit(TdApi.UpdateDeleteMessages(1, longArrayOf(10), false, true))
            runCurrent()

            assertEquals(listOf(1L to 12L, 1L to 10L), feed().ids())
        }

    @Test
    fun `refreshing reads posts again by channel, and those TDLib no longer returns leave the feed`() =
        runTest {
            val repository = repository()
            channel(1, 12, 11)
            channel(2, 25)
            repository.load()
            val feed = collected(repository)
            histories[1] = listOf(post(1, 12).apply { editDate = 700 })

            assertTrue(repository.refresh(mapOf(1L to listOf(12L, 11L), 2L to listOf(25L))) is Outcome.Success)
            runCurrent()

            assertEquals(listOf(2L to 25L, 1L to 12L), feed().ids())
            assertEquals(700L, feed().post(12).editDate)
            assertEquals(listOf(1L, 2L), client.sentOf<TdApi.GetMessages>().map { it.chatId })
        }

    @Test
    fun `before anything is read the feed holds each channel's last post, and no group`() =
        runTest {
            val repository = repository()
            channel(1, 12, 11)
            channel(2, 25, 24)
            val group = chat(id = 3, type = TdApi.ChatTypeSupergroup(3, false), lastMessage = post(3, 40))
            client.emit(updateNewChat(group))
            runCurrent()

            val feed = repository.feed.first()

            assertEquals(listOf(2L to 25L, 1L to 12L), feed.ids())
            assertEquals(2, feed.channelCount)
        }

    @Test
    fun `loading reads each channel's newest posts, merged newest first`() =
        runTest {
            val repository = repository()
            channel(1, 12, 11, 10)
            channel(2, 25, 9)

            assertTrue(repository.load() is Outcome.Success)
            runCurrent()

            assertEquals(
                listOf(2L to 25L, 1L to 12L, 1L to 11L, 1L to 10L, 2L to 9L),
                repository.feed.first().ids(),
            )
        }

    @Test
    fun `a short first answer from TDLib is followed by the posts before it`() =
        runTest {
            val repository = repository()
            channel(1, 12, 11, 10)
            firstCall = 1

            repository.load()
            runCurrent()

            assertEquals(
                listOf(12L, 11L, 10L),
                repository.feed
                    .first()
                    .posts
                    .map { it.message.id },
            )
        }

    @Test
    fun `a channel's new last post brings the posts since it was read`() =
        runTest {
            val repository = repository()
            channel(1, 12, 11)
            repository.load()
            histories[1] = listOf(post(1, 14), post(1, 13)) + histories.getValue(1)

            client.emit(TdApi.UpdateChatLastMessage(1, post(1, 14), arrayOf(chatPosition())))
            runCurrent()

            assertEquals(
                listOf(14L, 13L, 12L, 11L),
                repository.feed
                    .first()
                    .posts
                    .map { it.message.id },
            )
        }

    @Test
    fun `posts after the channel's last read one are unread`() =
        runTest {
            val repository = repository()
            channel(1, 12, 11, 10, lastRead = 11)
            repository.load()
            runCurrent()

            val unread =
                repository.feed
                    .first()
                    .posts
                    .map { it.message.id to it.isUnreadPost }

            assertEquals(listOf(12L to true, 11L to false, 10L to false), unread)
        }

    @Test
    fun `seeing posts marks them read in the closed channel`() =
        runTest {
            val repository = repository()

            repository.view(chatId = 1, messageIds = listOf(12, 11))

            val view = client.sentOf<TdApi.ViewMessages>().single()
            assertEquals(listOf(12L, 11L), view.messageIds.toList())
            assertTrue(view.forceRead)
            assertTrue(view.source is TdApi.MessageSourceOther)
        }

    @Test
    fun `a channel that cannot be read keeps its last post, and loading fails only when none can be read`() =
        runTest {
            val repository = repository()
            channel(1, 12, 11)
            channel(2, 25, 24)
            failing += 2L

            assertTrue(repository.load() is Outcome.Success)
            runCurrent()
            assertEquals(listOf(2L to 25L, 1L to 12L, 1L to 11L), repository.feed.first().ids())

            failing += 1L
            assertTrue(repository.load() is Outcome.Failure)
        }
}
