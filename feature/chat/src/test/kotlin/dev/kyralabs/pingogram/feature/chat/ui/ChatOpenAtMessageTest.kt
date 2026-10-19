package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [ChatViewModel] opened at a message, as search opens it: the messages around it load
 * ([FakeMessageRepository.loadAround]), wherever it is in the history, and show with an anchor that centres and
 * highlights it; until then the conversation shows nothing older or newer first. A message that is gone says so. After
 * process death the conversation opens where the list was instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatOpenAtMessageTest : ChatViewModelHarness() {
    /** A view model of the private chat, opened at [messageId], whose messages around it are [around]. */
    private fun TestScope.openedAt(
        messageId: Long,
        around: LongRange,
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        chats.setChats(chat())
        messages.answerAround(PRIVATE_CHAT, window(around))
        return viewModel(PRIVATE_CHAT, handle, messageId = messageId)
    }

    @Test
    fun `the messages around the found message load, and the list centres and highlights it`() =
        runTest {
            val viewModel = openedAt(50, 40L..60L)

            assertEquals(listOf("around:50"), messages.loads)
            assertEquals(AnchorTarget.Message(50, highlight = true), viewModel.anchor.target)
            assertEquals((60L downTo 40L).toList(), viewModel.bubbles.map { it.id })
            assertTrue(messages.loadRequests.isEmpty())
        }

    @Test
    fun `the newest messages in memory do not show before the found message's`() =
        runViewModelTest {
            chats.setChats(chat())
            messages.setHistory(PRIVATE_CHAT, history(message(100), message(101)))
            val page = CompletableDeferred<Outcome<Unit>>()
            messages.onLoadAround = { _, _ -> page.await() }

            val viewModel = viewModel(PRIVATE_CHAT, messageId = 50)
            assertEquals(ChatContent.Loading, viewModel.state.content)

            messages.replaceHistory(PRIVATE_CHAT, window(40L..60L))
            page.complete(Outcome.Success(Unit))
            runCurrent()
            assertEquals((60L downTo 40L).toList(), viewModel.bubbles.map { it.id })
        }

    @Test
    fun `a found message that no longer exists says so, and the list shows where it was`() =
        runTest {
            chats.setChats(chat())
            messages.answerAround(PRIVATE_CHAT, history(message(49), message(51), reachedStart = false))

            val viewModel = viewModel(PRIVATE_CHAT, messageId = 50)

            viewModel.effects.test { assertEquals(ChatEffect.MessageUnavailable, awaitItem()) }
            assertEquals(AnchorTarget.Message(50), viewModel.anchor.target)
        }

    @Test
    fun `a load that fails shows the error, and retry opens again`() =
        runTest {
            chats.setChats(chat())
            messages.answerAround(PRIVATE_CHAT, window(40L..60L), failure = Outcome.Failure(DomainError.Network))
            val viewModel = viewModel(PRIVATE_CHAT, messageId = 50)
            assertEquals(ChatContent.Error(DomainError.Network), viewModel.state.content)

            messages.answerAround(PRIVATE_CHAT, window(40L..60L))
            send(viewModel, ChatEvent.Retry)

            assertEquals(listOf("around:50", "around:50"), messages.loads)
            assertEquals(AnchorTarget.Message(50), viewModel.anchor.target)
        }

    @Test
    fun `the unread divider stays where it was`() =
        runTest {
            chats.setChats(chat(unreadCount = 1, lastReadInboxMessageId = 10))
            messages.answerAround(PRIVATE_CHAT, history(message(11), message(10), reachedStart = false))

            val viewModel = viewModel(PRIVATE_CHAT, messageId = 10)

            assertEquals(listOf("m11", "unread", "m10"), viewModel.outline().take(3))
        }

    @Test
    fun `it is highlighted once, and after process death the list opens where it was, without highlight`() =
        runTest {
            val handle = SavedStateHandle()
            val first = openedAt(50, 40L..60L, handle)
            send(first, ChatEvent.MessagesVisible(setOf(52, 53, 54)))

            // The view model made again from the saved state, as after process death.
            val saved = handle.keys().associateWith { handle.get<Any>(it) }
            messages.answerAround(PRIVATE_CHAT, window(44L..64L))
            val restored = viewModel(PRIVATE_CHAT, SavedStateHandle(saved), messageId = 50)

            assertEquals("around:54", messages.loads.last())
            assertEquals(ListAnchor(first.anchor.id + 1, AnchorTarget.Message(54, highlight = false)), restored.anchor)
            restored.effects.test { expectNoEvents() }
        }

    @Test
    fun `without a message to open at nor anything unread, it opens at the newest message`() =
        runTest {
            chats.setChats(chat())
            messages.setHistory(PRIVATE_CHAT, history(message(10), message(11)))

            val viewModel = viewModel()

            assertEquals(listOf("newest"), messages.loads)
            assertEquals(AnchorTarget.Newest, viewModel.anchor.target)
            viewModel.effects.test { expectNoEvents() }
        }
}
