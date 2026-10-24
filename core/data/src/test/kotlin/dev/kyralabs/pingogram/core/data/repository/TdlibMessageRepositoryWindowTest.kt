package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messages
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageContent
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [TdlibMessageRepository]'s windows that do not start at the newest message: a page around a message
 * ([TdlibMessageRepository.loadAround]), newer pages after it ([TdlibMessageRepository.loadNewer]) up to the chat's
 * last message, and the newest page that replaces it ([TdlibMessageRepository.loadNewest]).
 */
class TdlibMessageRepositoryWindowTest {
    private val client = FakeTdlibClient()

    /** The chat's messages, oldest first; the newest is its last message. */
    private var chatIds: List<Long> = emptyList()

    /** The run of messages TDLib has locally. */
    private var localIds: LongRange = LongRange.EMPTY

    private fun TestScope.repository() =
        TdlibMessageRepository(
            client,
            backgroundScope,
            SecretChatLinkPreviews(client, FakeLinkPreviewSettingsRepository()),
        )

    /**
     * Answers getChatHistory as TDLib does for [CHAT] with messages [ids]: from from_message_id (0: the newest) on,
     * -offset newer ones and the rest from it back, up to the limit, newest first. Locally TDLib has the run [localIds]
     * (the newest messages by default) and answers nothing from outside it. getChat answers with the newest message as
     * the chat's last one.
     */
    private fun serveChat(
        ids: LongRange,
        localIds: LongRange = ids,
    ) {
        chatIds = ids.toList()
        this.localIds = localIds
        client.on(TdApi.GetChatHistory::class) { request -> serve(request) }
        client.on(TdApi.GetChat::class) {
            val last = chatIds.lastOrNull()?.let { message(id = it, chatId = CHAT) }
            TdResult.Success(TdApiFixtures.chat(id = CHAT, lastMessage = last))
        }
    }

    /** getChatHistory's answer for the chat as it is now; see [serveChat]. */
    private fun serve(request: TdApi.GetChatHistory): TdResult<TdApi.Messages> {
        val from = if (request.fromMessageId == 0L) Long.MAX_VALUE else request.fromMessageId
        val known = if (request.onlyLocal) chatIds.filter { it in localIds || it > localIds.last } else chatIds
        val outside = request.onlyLocal && from != Long.MAX_VALUE && from !in localIds
        val newer = known.filter { it > from }.take(-request.offset)
        val older = known.filter { it <= from }.takeLast(request.limit + request.offset)
        val page = if (outside) emptyList() else (newer + older).sortedDescending()
        return TdResult.Success(messages(*page.map { message(id = it, chatId = CHAT) }.toTypedArray()))
    }

    /** getChatHistory answers once [release] completes. */
    private fun holdHistory(release: CompletableDeferred<Unit>) =
        client.on(TdApi.GetChatHistory::class) { request ->
            release.await()
            serve(request)
        }

    /** Message [id] of the chat arrives: it is the chat's newest message from now on. */
    private suspend fun arrives(id: Long) {
        chatIds = chatIds + id
        client.emit(updateNewMessage(message(id = id, chatId = CHAT)))
    }

    private suspend fun TdlibMessageRepository.snapshot(): ChatHistory = history(CHAT).first()

    private suspend fun TdlibMessageRepository.ids() = snapshot().messages.map { it.id }

    /** The from_message_id, offset, limit and only_local of every getChatHistory request. */
    private fun historyRequests() =
        client.sentOf<TdApi.GetChatHistory>().map { listOf<Any>(it.fromMessageId, it.offset, it.limit, it.onlyLocal) }

    // Around a message

    @Test
    fun `a page around a message has a few newer messages and more older ones`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()

            val result = repository.loadAround(CHAT, 100)

            assertEquals(Outcome.Success(Unit), result)
            assertEquals(listOf(listOf<Any>(100L, -30, 50, true)), historyRequests())
            assertEquals((130L downTo 81L).toList(), repository.ids())
        }

    @Test
    fun `a page around an older message reaches neither the newest message nor the start`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()

            repository.loadAround(CHAT, 100)

            val history = repository.snapshot()
            assertFalse(history.reachedEnd)
            assertFalse(history.reachedStart)
        }

    @Test
    fun `a page around a message that holds the chat's last message reaches the newest message`() =
        runTest {
            serveChat(1L..110L)
            val repository = repository()

            repository.loadAround(CHAT, 100)

            assertEquals((110L downTo 81L).toList(), repository.ids())
            assertTrue(repository.snapshot().reachedEnd)
        }

    @Test
    fun `a message already loaded needs no page`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()
            repository.loadNewest(CHAT)

            repository.loadAround(CHAT, 180)

            assertEquals(1, historyRequests().size)
            assertEquals((200L downTo 151L).toList(), repository.ids())
        }

    @Test
    fun `a page around a message replaces loaded messages that do not hold it`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()
            repository.loadNewest(CHAT)

            repository.loadAround(CHAT, 50)

            assertEquals((80L downTo 31L).toList(), repository.ids())
        }

    @Test
    fun `TDLib's server answers when it has nothing around the message locally`() =
        runTest {
            serveChat(1L..200L, localIds = 151L..200L)
            val repository = repository()

            repository.loadAround(CHAT, 100)

            assertEquals(listOf(listOf<Any>(100L, -30, 50, true), listOf<Any>(100L, -30, 50, false)), historyRequests())
            assertEquals((130L downTo 81L).toList(), repository.ids())
        }

    @Test
    fun `around a message that no longer exists, the messages where it was show`() =
        runTest {
            serveChat(1L..200L)
            chatIds = chatIds - 100L
            val repository = repository()

            repository.loadAround(CHAT, 100)

            assertEquals((130L downTo 80L).filter { it != 100L }, repository.ids())
            assertEquals(1, historyRequests().size)
        }

    @Test
    fun `a failed page around a message leaves the history as it was`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()
            repository.loadNewest(CHAT)
            client.on(TdApi.GetChatHistory::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 3")))

            val result = repository.loadAround(CHAT, 50)

            assertEquals(Outcome.Failure(DomainError.FloodWait(3)), result)
            assertEquals((200L downTo 151L).toList(), repository.ids())
            assertTrue(repository.snapshot().reachedEnd)
        }

    // Newer pages

    @Test
    fun `a newer page follows the newest loaded message`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()
            repository.loadAround(CHAT, 100)

            repository.loadNewer(CHAT)

            assertEquals(listOf<Any>(130L, -50, 50, true), historyRequests().last())
            assertEquals((180L downTo 81L).toList(), repository.ids())
            assertFalse(repository.snapshot().reachedEnd)
        }

    @Test
    fun `newer pages reach the newest message with the chat's last one`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()
            repository.loadAround(CHAT, 100)

            repository.loadNewer(CHAT)
            repository.loadNewer(CHAT)

            assertEquals((200L downTo 81L).toList(), repository.ids())
            assertTrue(repository.snapshot().reachedEnd)
        }

    @Test
    fun `nothing newer loads once the newest message is there`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()
            repository.loadNewest(CHAT)

            val result = repository.loadNewer(CHAT)

            assertEquals(Outcome.Success(Unit), result)
            assertEquals(1, historyRequests().size)
        }

    @Test
    fun `older pages keep a window around a message short of the newest message`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()
            repository.loadAround(CHAT, 100)

            repository.loadOlder(CHAT)

            // Offset 0 includes the oldest loaded message itself: 49 older ones.
            assertEquals((130L downTo 32L).toList(), repository.ids())
            assertFalse(repository.snapshot().reachedEnd)
        }

    @Test
    fun `without the chat's last message, the server having nothing newer reaches the newest message`() =
        runTest {
            serveChat(1L..110L)
            client.on(TdApi.GetChat::class, TdResult.Failure(TdError(400, "Chat not found")))
            val repository = repository()
            repository.loadAround(CHAT, 100)
            assertFalse(repository.snapshot().reachedEnd)

            repository.loadNewer(CHAT)

            val expected = listOf(listOf<Any>(110L, -50, 50, true), listOf<Any>(110L, -50, 50, false))
            assertEquals(expected, historyRequests().takeLast(2))
            assertTrue(repository.snapshot().reachedEnd)
        }

    @Test
    fun `a failed newer page leaves the history as it was`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()
            repository.loadAround(CHAT, 100)
            client.on(TdApi.GetChatHistory::class, TdResult.Failure(TdError(500, "Internal")))

            repository.loadNewer(CHAT)

            assertEquals((130L downTo 81L).toList(), repository.ids())
        }

    // New messages

    @Test
    fun `a new message stays out of a window short of the newest message`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()
            repository.loadAround(CHAT, 100)

            arrives(201)

            assertEquals((130L downTo 81L).toList(), repository.ids())
        }

    @Test
    fun `a new message joins the window once newer pages reached the newest message`() =
        runTest {
            serveChat(1L..140L)
            val repository = repository()
            repository.loadAround(CHAT, 100)
            repository.loadNewer(CHAT)

            arrives(141)

            assertEquals((141L downTo 81L).toList(), repository.ids())
        }

    @Test
    fun `a message that arrives while the newer page loads ends up in the window`() =
        runTest {
            serveChat(1L..140L)
            val repository = repository()
            repository.loadAround(CHAT, 100)
            val held = CompletableDeferred<Unit>()
            holdHistory(held)

            val load = async { repository.loadNewer(CHAT) }
            runCurrent()
            arrives(141)
            held.complete(Unit)
            load.await()

            assertEquals((141L downTo 81L).toList(), repository.ids())
        }

    // The newest page

    @Test
    fun `the newest page replaces a window short of the newest message`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()
            repository.loadAround(CHAT, 100)

            repository.loadNewest(CHAT)

            assertEquals(listOf<Any>(0L, 0, 50, true), historyRequests().last())
            assertEquals((200L downTo 151L).toList(), repository.ids())
            assertTrue(repository.snapshot().reachedEnd)
        }

    @Test
    fun `the newest page of a chat with nothing loaded comes from TDLib's local messages first`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()

            repository.loadNewest(CHAT)

            assertEquals(listOf(listOf<Any>(0L, 0, 50, true)), historyRequests())
            assertEquals((200L downTo 151L).toList(), repository.ids())
        }

    @Test
    fun `a chat without messages reaches both ends with the newest page`() =
        runTest {
            serveChat(LongRange.EMPTY)
            val repository = repository()

            repository.loadNewest(CHAT)
            repository.loadNewest(CHAT)

            val history = repository.snapshot()
            assertTrue(history.messages.isEmpty() && history.reachedStart && history.reachedEnd)
            assertEquals(2, historyRequests().size)
        }

    // Telling windows apart

    @Test
    fun `a window that replaces loaded messages gets a new id, which pages and updates keep`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()
            repository.loadNewest(CHAT)
            val first = repository.snapshot().windowId

            repository.loadAround(CHAT, 100)
            val around = repository.snapshot().windowId
            repository.loadNewer(CHAT)
            repository.loadOlder(CHAT)
            client.emit(updateMessageContent(CHAT, messageId = 100, content = messageText("Edited")))

            assertTrue(around > first)
            assertEquals(around, repository.snapshot().windowId)
        }

    // Loads of a chat

    @Test
    fun `an update that overtakes the page around a message still applies to it`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()
            val held = CompletableDeferred<Unit>()
            holdHistory(held)

            val load = async { repository.loadAround(CHAT, 100) }
            runCurrent()
            client.emit(updateMessageContent(CHAT, messageId = 100, content = messageText("Edited")))
            held.complete(Unit)
            load.await()

            val edited = message(id = 100, chatId = CHAT, content = messageText("Edited")).toMessage()
            assertEquals(edited, repository.snapshot().messages.single { it.id == 100L })
        }

    @Test
    fun `loads of a chat run one after the other, and each ends up in the window`() =
        runTest {
            serveChat(1L..200L)
            val repository = repository()
            repository.loadAround(CHAT, 100)
            val held = CompletableDeferred<Unit>()
            holdHistory(held)

            val older = async { repository.loadOlder(CHAT) }
            val newer = async { repository.loadNewer(CHAT) }
            runCurrent()
            // The newer page waits for the older one.
            assertEquals(2, historyRequests().size)
            held.complete(Unit)
            older.await()
            newer.await()

            assertEquals((180L downTo 32L).toList(), repository.ids())
        }

    private companion object {
        const val CHAT = 100L
    }
}
