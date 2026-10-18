package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.failedState
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageVoiceNote
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messages
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.pendingState
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateDeleteMessages
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageContent
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageContentOpened
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageEdited
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageInteractionInfo
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageIsPinned
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageMentionRead
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageSendFailed
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageSendSucceeded
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewMessage
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.videoNote
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TdlibMessageRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibMessageRepository(client, backgroundScope)

    /** A repository with the first page of [CHAT] loaded, a chat whose messages have [ids]. */
    private suspend fun TestScope.repositoryWith(vararg ids: Long) =
        repository().also { repository ->
            serveHistory(ids.toList())
            repository.loadOlder(CHAT)
        }

    /**
     * Answers getChatHistory as TDLib does for chats (all alike) whose messages have [ids]: up to [pageSize] messages
     * before from_message_id, newest first, of those TDLib has locally ([localIds]) or of all of them.
     */
    private fun serveHistory(
        ids: List<Long>,
        localIds: List<Long> = ids,
        pageSize: Int = 50,
    ) = client.on(TdApi.GetChatHistory::class) { request ->
        val page =
            (if (request.onlyLocal) localIds else ids)
                .sortedDescending()
                .filter { request.fromMessageId == 0L || it < request.fromMessageId }
                .take(pageSize)
        TdResult.Success(messages(*page.map { message(id = it, chatId = request.chatId) }.toTypedArray()))
    }

    private fun answerHistory(vararg answer: TdApi.Message?) =
        client.on(TdApi.GetChatHistory::class, TdResult.Success(messages(*answer)))

    /** getChatHistory answers once [answer] completes; [requested] completes when TDLib is asked. */
    private class HeldAnswer(
        val requested: CompletableDeferred<Unit> = CompletableDeferred(),
        val answer: CompletableDeferred<TdResult<TdApi.Messages>> = CompletableDeferred(),
    )

    private fun holdHistoryAnswer() =
        HeldAnswer().also { held ->
            client.on(TdApi.GetChatHistory::class) {
                held.requested.complete(Unit)
                held.answer.await()
            }
        }

    private suspend fun TdlibMessageRepository.snapshot(chatId: Long = CHAT): ChatHistory = history(chatId).first()

    private suspend fun TdlibMessageRepository.ids(chatId: Long = CHAT) = snapshot(chatId).messages.map { it.id }

    private suspend fun TdlibMessageRepository.loadedMessage(id: Long): Message =
        snapshot().messages.single { it.id == id }

    private fun historyRequests() =
        client.sentOf<TdApi.GetChatHistory>().map { listOf<Any>(it.chatId, it.fromMessageId, it.onlyLocal) }

    // Loading

    @Test
    fun `observing loads nothing`() =
        runTest {
            val history = repository().snapshot()

            assertEquals(ChatHistory(persistentListOf(), reachedStart = false), history)
            assertEquals(emptyList<List<Any>>(), historyRequests())
        }

    @Test
    fun `the first page is the newest messages TDLib has locally, at most 50`() =
        runTest {
            serveHistory(listOf(1, 2, 3))

            val result = repository().loadOlder(CHAT)

            val request = client.sentOf<TdApi.GetChatHistory>().single()
            assertEquals(Outcome.Success(Unit), result)
            assertEquals(
                listOf<Any>(CHAT, 0L, 0, 50, true),
                listOf(request.chatId, request.fromMessageId, request.offset, request.limit, request.onlyLocal),
            )
        }

    @Test
    fun `history shows the loaded messages newest first`() =
        runTest {
            val repository = repositoryWith(1, 2, 3)

            assertEquals(listOf(3L, 2L, 1L), repository.ids())
        }

    @Test
    fun `the next page is the messages before the oldest loaded one`() =
        runTest {
            serveHistory((1L..80L).toList())
            val repository = repository()

            repository.loadOlder(CHAT)
            repository.loadOlder(CHAT)

            assertEquals(listOf(listOf<Any>(CHAT, 0L, true), listOf<Any>(CHAT, 31L, true)), historyRequests())
            assertEquals((80L downTo 1L).toList(), repository.ids())
        }

    @Test
    fun `asks the server when TDLib has no older messages locally`() =
        runTest {
            serveHistory(listOf(1, 2, 3), localIds = emptyList())
            val repository = repository()

            repository.loadOlder(CHAT)

            assertEquals(listOf(listOf<Any>(CHAT, 0L, true), listOf<Any>(CHAT, 0L, false)), historyRequests())
            assertEquals(listOf(3L, 2L, 1L), repository.ids())
        }

    @Test
    fun `a short page does not reach the start`() =
        runTest {
            serveHistory((1L..100L).toList(), localIds = listOf(99, 100))
            val repository = repository()

            repository.loadOlder(CHAT)

            assertEquals(1, historyRequests().size)
            assertEquals(
                ChatHistory(persistentListOf(loaded(100), loaded(99)), reachedStart = false),
                repository.snapshot(),
            )
        }

    @Test
    fun `no older messages on the server reaches the start`() =
        runTest {
            val repository = repositoryWith(1, 2)

            repository.loadOlder(CHAT)

            assertEquals(
                ChatHistory(persistentListOf(loaded(2), loaded(1)), reachedStart = true),
                repository.snapshot(),
            )
        }

    @Test
    fun `loads nothing once the start is reached`() =
        runTest {
            val repository = repositoryWith(1, 2)
            repository.loadOlder(CHAT)

            val result = repository.loadOlder(CHAT)

            assertEquals(Outcome.Success(Unit), result)
            assertEquals(3, historyRequests().size)
        }

    @Test
    fun `messages loaded already are not added again`() =
        runTest {
            client.on(TdApi.GetChatHistory::class) { request ->
                val page = if (request.fromMessageId == 0L) listOf(5L, 4L, 3L) else listOf(3L, 2L, 1L)
                TdResult.Success(messages(*page.map { message(id = it) }.toTypedArray()))
            }
            val repository = repository()

            repository.loadOlder(CHAT)
            repository.loadOlder(CHAT)

            assertEquals(listOf(5L, 4L, 3L, 2L, 1L), repository.ids())
        }

    @Test
    fun `a local answer with only loaded messages counts as none`() =
        runTest {
            client.on(TdApi.GetChatHistory::class) { request ->
                val page =
                    when {
                        request.fromMessageId == 0L -> listOf(5L, 4L, 3L)
                        request.onlyLocal -> listOf(3L)
                        else -> listOf(2L, 1L)
                    }
                TdResult.Success(messages(*page.map { message(id = it) }.toTypedArray()))
            }
            val repository = repository()
            repository.loadOlder(CHAT)

            repository.loadOlder(CHAT)

            assertEquals(listOf(true, true, false), client.sentOf<TdApi.GetChatHistory>().map { it.onlyLocal })
            assertEquals(listOf(5L, 4L, 3L, 2L, 1L), repository.ids())
        }

    @Test
    fun `skips the null entries TDLib may answer with`() =
        runTest {
            answerHistory(message(id = 2), null, message(id = 1))
            val repository = repository()

            repository.loadOlder(CHAT)

            assertEquals(listOf(2L, 1L), repository.ids())
        }

    @Test
    fun `concurrent loads of a chat share one request`() =
        runTest {
            val answer = holdHistoryAnswer()
            val repository = repository()

            val loads = List(2) { async { repository.loadOlder(CHAT) } }
            answer.requested.await()
            answer.answer.complete(TdResult.Success(messages(message(id = 1))))

            assertEquals(List(2) { Outcome.Success(Unit) }, loads.awaitAll())
            assertEquals(1, historyRequests().size)
        }

    // Failures

    @Test
    fun `a flood wait leaves the history as it was`() =
        runTest {
            val repository = repositoryWith(2, 3)
            client.on(TdApi.GetChatHistory::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 7")))

            val result = repository.loadOlder(CHAT)

            assertEquals(Outcome.Failure(DomainError.FloodWait(7)), result)
            assertEquals(listOf(3L, 2L), repository.ids())
        }

    @Test
    fun `a server that does not answer in time fails with Network and leaves the history as it was`() =
        runTest {
            val repository = repositoryWith(2, 3)
            val never = CompletableDeferred<TdResult<TdApi.Messages>>()
            client.on(TdApi.GetChatHistory::class) { request ->
                if (request.onlyLocal) TdResult.Success(messages()) else never.await()
            }

            val result = repository.loadOlder(CHAT)

            assertEquals(Outcome.Failure(DomainError.Network), result)
            assertEquals(listOf(3L, 2L), repository.ids())
        }

    @Test
    fun `an unknown chat fails with NotFound`() =
        runTest {
            client.on(TdApi.GetChatHistory::class, TdResult.Failure(TdError(400, "Chat not found")))
            val repository = repository()

            val result = repository.loadOlder(CHAT)

            assertEquals(Outcome.Failure(DomainError.NotFound), result)
            assertEquals(ChatHistory(persistentListOf(), reachedStart = false), repository.snapshot())
        }

    @Test
    fun `a failed load can be tried again`() =
        runTest {
            client.on(TdApi.GetChatHistory::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 7")))
            val repository = repository()
            repository.loadOlder(CHAT)
            serveHistory(listOf(1))

            val result = repository.loadOlder(CHAT)

            assertEquals(Outcome.Success(Unit), result)
            assertEquals(listOf(1L), repository.ids())
        }

    // Updates

    @Test
    fun `history emits every change`() =
        runTest {
            serveHistory(listOf(1))
            val repository = repository()

            repository.history(CHAT).test {
                awaitItem()

                repository.loadOlder(CHAT)
                assertEquals(listOf(1L), awaitItem().messages.map { it.id })

                client.emit(updateNewMessage(message(id = 2)))
                assertEquals(listOf(2L, 1L), awaitItem().messages.map { it.id })
            }
        }

    @Test
    fun `a new message goes on top`() =
        runTest {
            val repository = repositoryWith(1, 2)

            client.emit(updateNewMessage(message(id = 3)))

            assertEquals(listOf(3L, 2L, 1L), repository.ids())
        }

    @Test
    fun `a new message older than the loaded ones is left to loadOlder`() =
        runTest {
            val repository = repositoryWith(4, 5)

            client.emit(updateNewMessage(message(id = 1)))

            assertEquals(listOf(5L, 4L), repository.ids())
        }

    @Test
    fun `a new message in a chat with nothing loaded yet is left to loadOlder`() =
        runTest {
            val repository = repository()

            repository.history(CHAT).test {
                awaitItem()

                client.emit(updateNewMessage(message(id = 1)))

                expectNoEvents()
            }
        }

    @Test
    fun `a scheduled message is not part of the history`() =
        runTest {
            val repository = repositoryWith(1)
            val scheduled = message(id = 2).apply { schedulingState = TdApi.MessageSchedulingStateSendAtDate(1, 0) }

            client.emit(updateNewMessage(scheduled))

            assertEquals(listOf(1L), repository.ids())
        }

    @Test
    fun `a sent message replaces its temporary one, in its place by id`() =
        runTest {
            val repository = repositoryWith(5)
            client.emit(updateNewMessage(message(id = 1_001, isOutgoing = true, sendingState = pendingState())))
            client.emit(updateNewMessage(message(id = 1_002, isOutgoing = true, sendingState = pendingState())))

            client.emit(updateMessageSendSucceeded(message(id = 6, isOutgoing = true), oldMessageId = 1_001))

            assertEquals(listOf(1_002L, 6L, 5L), repository.ids())
        }

    @Test
    fun `a message that failed to send replaces its temporary one`() =
        runTest {
            val repository = repositoryWith(5)
            client.emit(updateNewMessage(message(id = 1_001, isOutgoing = true, sendingState = pendingState())))
            val failed = message(id = 1_003, isOutgoing = true, sendingState = failedState())

            client.emit(updateMessageSendFailed(failed, oldMessageId = 1_001))

            assertEquals(listOf(failed.toMessage(), loaded(5)), repository.snapshot().messages)
        }

    @Test
    fun `updateMessageContent replaces the content`() =
        runTest {
            val repository = repositoryWith(1)

            client.emit(updateMessageContent(CHAT, messageId = 1, content = messageText("Edited")))

            assertEquals(message(id = 1, content = messageText("Edited")).toMessage(), repository.loadedMessage(1))
        }

    @Test
    fun `updateMessageEdited sets the edit date`() =
        runTest {
            val repository = repositoryWith(1)

            client.emit(updateMessageEdited(CHAT, messageId = 1, editDate = 1_700_000_100))

            assertEquals(message(id = 1, editDate = 1_700_000_100).toMessage(), repository.loadedMessage(1))
        }

    @Test
    fun `updateMessageInteractionInfo replaces the interaction info`() =
        runTest {
            val repository = repositoryWith(1)
            val info = TdApi.MessageInteractionInfo(10, 2, null, null)

            client.emit(updateMessageInteractionInfo(CHAT, messageId = 1, interactionInfo = info))

            assertEquals(message(id = 1, interactionInfo = info).toMessage(), repository.loadedMessage(1))
        }

    @Test
    fun `updateMessageIsPinned sets the pinned state`() =
        runTest {
            val repository = repositoryWith(1)

            client.emit(updateMessageIsPinned(CHAT, messageId = 1, isPinned = true))

            assertEquals(message(id = 1, isPinned = true).toMessage(), repository.loadedMessage(1))
        }

    @Test
    fun `updateMessageContentOpened marks a voice note as listened`() =
        runTest {
            answerHistory(message(id = 1, content = messageVoiceNote(isListened = false)))
            val repository = repository()
            repository.loadOlder(CHAT)

            client.emit(updateMessageContentOpened(CHAT, messageId = 1))

            assertEquals(
                message(id = 1, content = messageVoiceNote(isListened = true)).toMessage(),
                repository.loadedMessage(1),
            )
        }

    @Test
    fun `updateMessageContentOpened marks a video note as viewed`() =
        runTest {
            answerHistory(message(id = 1, content = TdApi.MessageVideoNote(videoNote(), false, false)))
            val repository = repository()
            repository.loadOlder(CHAT)

            client.emit(updateMessageContentOpened(CHAT, messageId = 1))

            assertEquals(
                message(id = 1, content = TdApi.MessageVideoNote(videoNote(), true, false)).toMessage(),
                repository.loadedMessage(1),
            )
        }

    @Test
    fun `updateMessageMentionRead clears the unread mention`() =
        runTest {
            answerHistory(message(id = 1, containsUnreadMention = true))
            val repository = repository()
            repository.loadOlder(CHAT)

            client.emit(updateMessageMentionRead(CHAT, messageId = 1))

            assertFalse(repository.loadedMessage(1).containsUnreadMention)
        }

    @Test
    fun `permanently deleted messages are removed`() =
        runTest {
            val repository = repositoryWith(1, 2, 3)

            client.emit(updateDeleteMessages(CHAT, 2))

            assertEquals(listOf(3L, 1L), repository.ids())
        }

    @Test
    fun `messages that became inaccessible are removed`() =
        runTest {
            val repository = repositoryWith(1, 2, 3)

            client.emit(updateDeleteMessages(CHAT, 2, isPermanent = false))

            assertEquals(listOf(3L, 1L), repository.ids())
        }

    @Test
    fun `messages TDLib unloaded go, with every older one`() =
        runTest {
            val repository = repositoryWith(1, 2, 3, 4, 5)

            client.emit(updateDeleteMessages(CHAT, 2, isPermanent = false, fromCache = true))

            assertEquals(listOf(5L, 4L, 3L), repository.ids())
        }

    @Test
    fun `after TDLib unloaded messages the start has to be loaded again`() =
        runTest {
            val repository = repositoryWith(1, 2, 3)
            repository.loadOlder(CHAT)

            client.emit(updateDeleteMessages(CHAT, 1, isPermanent = false, fromCache = true))

            assertFalse(repository.snapshot().reachedStart)
        }

    @Test
    fun `updates of a chat that is not loaded are ignored`() =
        runTest {
            val repository = repositoryWith(1)

            client.emit(updateNewMessage(message(id = 1, chatId = OTHER_CHAT)))

            assertEquals(emptyList<Long>(), repository.ids(OTHER_CHAT))
        }

    @Test
    fun `an update that overtakes the page it concerns still applies to it`() =
        runTest {
            client.on(TdApi.GetChatHistory::class) {
                // TDLib read the page before the message was deleted, but the deletion arrives first.
                client.emit(updateDeleteMessages(CHAT, 2))
                TdResult.Success(messages(message(id = 3), message(id = 2), message(id = 1)))
            }
            val repository = repository()

            repository.loadOlder(CHAT)

            assertEquals(listOf(3L, 1L), repository.ids())
        }

    @Test
    fun `a message that arrives while the first page loads is kept`() =
        runTest {
            client.on(TdApi.GetChatHistory::class) {
                client.emit(updateNewMessage(message(id = 4)))
                TdResult.Success(messages(message(id = 3), message(id = 2), message(id = 1)))
            }
            val repository = repository()

            repository.loadOlder(CHAT)

            assertEquals(listOf(4L, 3L, 2L, 1L), repository.ids())
        }

    @Test
    fun `a message sent while its page loads does not come back as temporary`() =
        runTest {
            client.on(TdApi.GetChatHistory::class) {
                client.emit(updateMessageSendSucceeded(message(id = 6, isOutgoing = true), oldMessageId = 1_001))
                val pending = message(id = 1_001, isOutgoing = true, sendingState = pendingState())
                TdResult.Success(messages(pending, message(id = 5)))
            }
            val repository = repository()

            repository.loadOlder(CHAT)

            assertEquals(listOf(6L, 5L), repository.ids())
        }

    // Memory

    @Test
    fun `keeps the three most recently used chats`() =
        runTest {
            serveHistory(listOf(1))
            val repository = repository()

            (1L..4L).forEach { chatId -> repository.loadOlder(chatId) }

            assertEquals(
                listOf(listOf(1L), listOf(1L), listOf(1L), emptyList()),
                listOf(4L, 3L, 2L, 1L).map { chatId -> repository.ids(chatId) },
            )
        }

    @Test
    fun `never forgets an observed chat`() =
        runTest {
            serveHistory(listOf(1))
            val repository = repository()

            repository.history(1).test {
                awaitItem()
                repository.loadOlder(1)
                awaitItem()

                (2L..5L).forEach { chatId -> repository.loadOlder(chatId) }

                assertEquals(listOf(1L), repository.ids(1))
            }
        }

    @Test
    fun `a chat no longer observed is kept up to date`() =
        runTest {
            val repository = repositoryWith(1)
            repository.snapshot()

            client.emit(updateNewMessage(message(id = 2)))

            assertEquals(listOf(2L, 1L), repository.ids())
        }

    @Test
    fun `trimMemory forgets the chats nobody observes`() =
        runTest {
            val repository = repositoryWith(1)

            repository.trimMemory()

            assertEquals(emptyList<Long>(), repository.ids())
        }

    @Test
    fun `trimMemory keeps the observed chats`() =
        runTest {
            serveHistory(listOf(1))
            val repository = repository()

            repository.history(CHAT).test {
                awaitItem()
                repository.loadOlder(CHAT)
                awaitItem()

                repository.trimMemory()

                assertEquals(listOf(1L), repository.ids())
            }
        }

    @Test
    fun `a page of a chat forgotten while it loaded is dropped`() =
        runTest {
            val answer = holdHistoryAnswer()
            val repository = repository()
            val load = async { repository.loadOlder(CHAT) }
            answer.requested.await()

            repository.trimMemory()
            answer.answer.complete(TdResult.Success(messages(message(id = 1))))

            assertEquals(Outcome.Success(Unit), load.await())
            assertTrue(repository.ids().isEmpty())
        }

    @Test
    fun `forgets every chat when TDLib closes`() =
        runTest {
            val repository = repositoryWith(1)

            client.emitAuthorizationState(TdApiFixtures.closed())

            assertEquals(emptyList<Long>(), repository.ids())
        }

    // Replied messages

    @Test
    fun `repliedMessage asks TDLib for the message the given one replies to`() =
        runTest {
            client.on(TdApi.GetRepliedMessage::class, TdResult.Success(message(id = 3, chatId = CHAT)))

            val replied = repository().repliedMessage(CHAT, 10)

            val request = client.sentOf<TdApi.GetRepliedMessage>().single()
            assertEquals(listOf(CHAT, 10L), listOf(request.chatId, request.messageId))
            assertEquals(Outcome.Success(loaded(3)), replied)
        }

    @Test
    fun `a replied message in the loaded history needs no request`() =
        runTest {
            val repository = repository()
            answerHistory(message(id = 10, chatId = CHAT, replyTo = replyTo(3)), message(id = 3, chatId = CHAT))
            repository.loadOlder(CHAT)

            val replied = repository.repliedMessage(CHAT, 10)

            assertEquals(Outcome.Success(loaded(3)), replied)
            assertTrue(client.sentOf<TdApi.GetRepliedMessage>().isEmpty())
        }

    @Test
    fun `a fetched replied message is kept with the chat's history`() =
        runTest {
            val repository = repositoryReplyingTo(3)

            repository.repliedMessage(CHAT, 10)
            val again = repository.repliedMessage(CHAT, 10)

            assertEquals(Outcome.Success(loaded(3)), again)
            assertEquals(1, client.sentOf<TdApi.GetRepliedMessage>().size)
        }

    @Test
    fun `a kept replied message follows its edits`() =
        runTest {
            val repository = repositoryReplyingTo(3)
            repository.repliedMessage(CHAT, 10)

            client.emit(updateMessageContent(CHAT, 3, messageText("Edited")))

            val replied = (repository.repliedMessage(CHAT, 10) as Outcome.Success).value
            assertEquals(message(id = 3, chatId = CHAT, content = messageText("Edited")).toMessage(), replied)
            assertEquals(1, client.sentOf<TdApi.GetRepliedMessage>().size)
        }

    @Test
    fun `a deleted replied message is forgotten`() =
        runTest {
            val repository = repositoryReplyingTo(3)
            repository.repliedMessage(CHAT, 10)

            client.emit(updateDeleteMessages(CHAT, 3))
            repository.repliedMessage(CHAT, 10)

            assertEquals(2, client.sentOf<TdApi.GetRepliedMessage>().size)
        }

    @Test
    fun `a replied message that does not exist fails with NotFound`() =
        runTest {
            client.on(TdApi.GetRepliedMessage::class, TdResult.Failure(TdError(404, "Not Found")))

            val replied = repository().repliedMessage(CHAT, 10)

            assertEquals(Outcome.Failure(DomainError.NotFound), replied)
        }

    @Test
    fun `a replied message of another chat is not kept`() =
        runTest {
            val repository = repositoryWith(10)
            client.on(TdApi.GetRepliedMessage::class, TdResult.Success(message(id = 3, chatId = OTHER_CHAT)))

            repository.repliedMessage(CHAT, 10)
            repository.repliedMessage(CHAT, 10)

            assertEquals(2, client.sentOf<TdApi.GetRepliedMessage>().size)
        }

    /**
     * A repository with [CHAT]'s newest message 10 loaded, replying to [repliedId], which getRepliedMessage answers
     * with.
     */
    private suspend fun TestScope.repositoryReplyingTo(repliedId: Long) =
        repository().also { repository ->
            answerHistory(message(id = 10, chatId = CHAT, replyTo = replyTo(repliedId)))
            repository.loadOlder(CHAT)
            client.on(TdApi.GetRepliedMessage::class, TdResult.Success(message(id = repliedId, chatId = CHAT)))
        }

    /** A reply to message [messageId] of [CHAT], without a quote. */
    private fun replyTo(messageId: Long) = TdApi.MessageReplyToMessage(CHAT, messageId, null, 0, "", null, 0, null)

    private companion object {
        const val CHAT = 100L
        const val OTHER_CHAT = 200L

        /** The domain message [serveHistory] answers with for [id] in [CHAT]. */
        fun loaded(id: Long) = message(id = id, chatId = CHAT).toMessage()
    }
}
