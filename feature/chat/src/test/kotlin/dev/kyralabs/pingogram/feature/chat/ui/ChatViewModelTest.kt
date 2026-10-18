package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The states of [ChatViewModel], paging, and opening and closing the chat. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest : ChatViewModelHarness() {
    // States

    @Test
    fun `loads, then shows the messages of the first page`() =
        runTest {
            chats.setChats(chat())
            val page = CompletableDeferred<Outcome<Unit>>()
            messages.onLoadOlder = { page.await() }
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(ChatContent.Loading, awaitItem().content)

                messages.setHistory(PRIVATE_CHAT, history(message(2), message(1), reachedStart = false))
                page.complete(Outcome.Success(Unit))

                val content = awaitItem().content as ChatContent.Content
                assertEquals(listOf(2L, 1L), content.items.filterIsInstance<ChatItem.Bubble>().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the first page loads at once when nothing is loaded`() =
        runTest {
            chats.setChats(chat())

            viewModel()

            assertEquals(listOf(PRIVATE_CHAT), messages.loadRequests)
        }

    @Test
    fun `nothing loads by itself when the history is there already`() =
        runTest {
            showing(chat(), history(message(1), reachedStart = false))

            assertTrue(messages.loadRequests.isEmpty())
        }

    @Test
    fun `a chat without messages is empty`() =
        runTest {
            chats.setChats(chat())
            messages.answerLoads(PRIVATE_CHAT, Outcome.Success(history(reachedStart = true)))

            val viewModel = viewModel()

            assertEquals(ChatContent.Empty, viewModel.state.content)
        }

    @Test
    fun `a first page that fails shows the error, and retry loads it again`() =
        runTest {
            chats.setChats(chat())
            messages.answerLoads(
                PRIVATE_CHAT,
                Outcome.Failure(DomainError.Network),
                Outcome.Success(history(message(1), reachedStart = true)),
            )
            val viewModel = viewModel()
            assertEquals(ChatContent.Error(DomainError.Network), viewModel.state.content)

            send(viewModel, ChatEvent.Retry)

            assertEquals(listOf(1L), viewModel.bubbles.map { it.id })
            assertEquals(2, messages.loadRequests.size)
        }

    @Test
    fun `a chat TDLib does not know is not found`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(ChatContent.NotFound, viewModel.state.content)
            assertNull(viewModel.state.header)
        }

    @Test
    fun `a chat TDLib has not announced yet is fetched`() =
        runTest {
            chats.onGetChat = { Outcome.Success(chat(title = "Ada")) }
            messages.setHistory(PRIVATE_CHAT, history(message(1)))

            val viewModel = viewModel()

            assertEquals(listOf(PRIVATE_CHAT), chats.getChatRequests)
            assertEquals(listOf(1L), viewModel.bubbles.map { it.id })
        }

    @Test
    fun `a failure finding the chat shows the error, and retry finds it`() =
        runTest {
            chats.onGetChat = { Outcome.Failure(DomainError.Network) }
            messages.setHistory(PRIVATE_CHAT, history(message(1)))
            val viewModel = viewModel()
            assertEquals(ChatContent.Error(DomainError.Network), viewModel.state.content)

            chats.onGetChat = { Outcome.Success(chat()) }
            send(viewModel, ChatEvent.Retry)

            assertEquals(listOf(1L), viewModel.bubbles.map { it.id })
            assertTrue(messages.loadRequests.isEmpty())
        }

    @Test
    fun `the jump button's badge counts the chat's unread messages`() =
        runTest {
            val viewModel = showing(chat(unreadCount = 3), history(message(1)))

            assertEquals(3, viewModel.state.unreadCount)
        }

    // Paging

    @Test
    fun `near the oldest message, older pages load until the start`() =
        runTest {
            val viewModel = showing(chat(), history(message(3), reachedStart = false))
            messages.answerLoads(
                PRIVATE_CHAT,
                Outcome.Success(history(message(3), message(2), reachedStart = false)),
                Outcome.Success(history(message(3), message(2), message(1), reachedStart = true)),
            )

            send(viewModel, ChatEvent.NearOldestChanged(true))

            assertEquals(2, messages.loadRequests.size)
            assertEquals(listOf(3L, 2L, 1L), viewModel.bubbles.map { it.id })
            assertTrue(viewModel.content.reachedStart)
        }

    @Test
    fun `moving away from the oldest message stops loading after the page`() =
        runTest {
            val viewModel = showing(chat(), history(message(3), reachedStart = false))
            val page = CompletableDeferred<Outcome<Unit>>()
            messages.onLoadOlder = { page.await() }
            send(viewModel, ChatEvent.NearOldestChanged(true))

            send(viewModel, ChatEvent.NearOldestChanged(false))
            page.complete(Outcome.Success(Unit))
            runCurrent()

            assertEquals(1, messages.loadRequests.size)
        }

    @Test
    fun `nothing loads once the start is reached`() =
        runTest {
            val viewModel = showing(chat(), history(message(1), reachedStart = true))

            send(viewModel, ChatEvent.NearOldestChanged(true))

            assertTrue(messages.loadRequests.isEmpty())
        }

    @Test
    fun `one page loads at a time`() =
        runTest {
            val viewModel = showing(chat(), history(message(3), reachedStart = false))
            messages.onLoadOlder = loadsForever

            send(viewModel, ChatEvent.NearOldestChanged(true), ChatEvent.NearOldestChanged(false))
            send(viewModel, ChatEvent.NearOldestChanged(true))

            assertEquals(1, messages.loadRequests.size)
            assertTrue(viewModel.content.isLoadingOlder)
        }

    @Test
    fun `a failed older page keeps the messages and shows the error, without retrying by itself`() =
        runTest {
            val viewModel = showing(chat(), history(message(3), reachedStart = false))
            messages.onLoadOlder = { Outcome.Failure(DomainError.FloodWait(seconds = 3)) }

            send(viewModel, ChatEvent.NearOldestChanged(true))
            runCurrent()

            assertEquals(listOf(3L), viewModel.bubbles.map { it.id })
            assertEquals(DomainError.FloodWait(seconds = 3), viewModel.content.olderError)
            assertEquals(1, messages.loadRequests.size)
        }

    @Test
    fun `after a failed page, retry or coming near the oldest message again loads it`() =
        runTest {
            val viewModel = showing(chat(), history(message(3), reachedStart = false))
            messages.onLoadOlder = { Outcome.Failure(DomainError.Network) }
            send(viewModel, ChatEvent.NearOldestChanged(true))

            send(viewModel, ChatEvent.Retry)
            send(viewModel, ChatEvent.NearOldestChanged(false), ChatEvent.NearOldestChanged(true))

            assertEquals(3, messages.loadRequests.size)
        }

    // Opening and closing

    @Test
    fun `a started screen opens the chat, and a stopped one closes it`() =
        runTest {
            val viewModel = showing(chat(), history(message(1)))

            viewModel.onEvent(ChatEvent.Started)
            viewModel.onEvent(ChatEvent.Stopped)

            assertEquals(listOf("open:$PRIVATE_CHAT", "close:$PRIVATE_CHAT"), chats.openCalls)
        }

    @Test
    fun `opening is sent before the event returns, so leaving at once still closes`() =
        runTest {
            val viewModel = showing(chat(), history(message(1)))

            viewModel.onEvent(ChatEvent.Started)

            // No runCurrent: undispatched.
            assertEquals(listOf("open:$PRIVATE_CHAT"), chats.openCalls)
        }

    @Test
    fun `starting again without a stop opens once`() =
        runTest {
            val viewModel = showing(chat(), history(message(1)))

            send(viewModel, ChatEvent.Started, ChatEvent.Started, ChatEvent.Stopped, ChatEvent.Stopped)

            assertEquals(listOf("open:$PRIVATE_CHAT", "close:$PRIVATE_CHAT"), chats.openCalls)
        }

    @Test
    fun `a rotation stops and starts the screen, which leaves the chat open`() =
        runTest {
            val viewModel = showing(chat(), history(message(1)))

            send(viewModel, ChatEvent.Started, ChatEvent.Stopped, ChatEvent.Started)

            assertEquals(listOf("open:$PRIVATE_CHAT", "close:$PRIVATE_CHAT", "open:$PRIVATE_CHAT"), chats.openCalls)
        }
}
