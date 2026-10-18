package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.MessageViews.Companion.VIEW_INTERVAL_MILLIS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What the conversation shows goes to TDLib as viewed, which reads it ([MessageViews] in [ChatViewModel]): batched
 * at most every 300 ms of virtual time, each message once, only while the screen is started; and the jump button's
 * badge follows the chat's unread count while the unread divider stays.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatReadTest : ChatViewModelHarness() {
    private fun TestScope.chatOfFive() = showing(chat(), history(*(1L..5L).map { message(it) }.toTypedArray()))

    private fun visible(vararg ids: Long) = ChatEvent.MessagesVisible(ids.toSet())

    private fun TestScope.wait(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `the messages on screen are viewed once the screen is started, after the chat is opened`() =
        runTest {
            var openWhenViewed: List<String>? = null
            chats.onViewMessages = {
                openWhenViewed = chats.openCalls.toList()
                Outcome.Success(Unit)
            }
            val viewModel = chatOfFive()

            send(viewModel, visible(4, 5))
            assertTrue(chats.viewed.isEmpty())
            send(viewModel, ChatEvent.Started)

            assertEquals(listOf(listOf(4L, 5L)), chats.viewed)
            assertEquals(listOf("open:${ChatFixtures.PRIVATE_CHAT}"), openWhenViewed)
        }

    @Test
    fun `what comes into view within 300 ms goes in one batch`() =
        runTest {
            val viewModel = chatOfFive()
            send(viewModel, ChatEvent.Started, visible(5))

            wait(VIEW_INTERVAL_MILLIS / 3)
            send(viewModel, visible(4, 5))
            wait(VIEW_INTERVAL_MILLIS / 3)
            send(viewModel, visible(3))
            assertEquals(listOf(listOf(5L)), chats.viewed)
            wait(VIEW_INTERVAL_MILLIS / 3)

            assertEquals(listOf(listOf(5L), listOf(3L, 4L)), chats.viewed)
        }

    @Test
    fun `each message is viewed once`() =
        runTest {
            val viewModel = chatOfFive()
            send(viewModel, ChatEvent.Started, visible(4, 5))
            wait(VIEW_INTERVAL_MILLIS)

            send(viewModel, visible(3, 4))
            wait(VIEW_INTERVAL_MILLIS)
            send(viewModel, visible(4, 5))
            wait(VIEW_INTERVAL_MILLIS)

            assertEquals(listOf(listOf(4L, 5L), listOf(3L)), chats.viewed)
        }

    @Test
    fun `nothing is viewed while the screen is stopped, and what showed meanwhile is once it starts again`() =
        runTest {
            val viewModel = chatOfFive()
            send(viewModel, ChatEvent.Started, visible(5))
            send(viewModel, ChatEvent.Stopped)

            send(viewModel, visible(4))
            wait(VIEW_INTERVAL_MILLIS * 2)
            assertEquals(listOf(listOf(5L)), chats.viewed)

            send(viewModel, ChatEvent.Started)
            assertEquals(listOf(listOf(5L), listOf(4L)), chats.viewed)
        }

    @Test
    fun `a failure is logged without ids and not retried`() =
        runTest {
            chats.onViewMessages = { Outcome.Failure(DomainError.Network) }
            val viewModel = chatOfFive()

            send(viewModel, ChatEvent.Started, visible(5))
            wait(VIEW_INTERVAL_MILLIS * 2)

            assertEquals(listOf(listOf(5L)), chats.viewed)
            assertEquals(listOf("viewMessages failed: Network"), logged)
        }

    @Test
    fun `the unread badge follows the chat while the divider stays where it was`() =
        runTest {
            val viewModel =
                showing(
                    chat(unreadCount = 3, lastReadInboxMessageId = 2),
                    history(*(1L..5L).map { message(it) }.toTypedArray()),
                )
            val outline = viewModel.outline()
            assertEquals(3, viewModel.state.unreadCount)

            // TDLib read messages 3 and 4.
            chats.setChats(chat(unreadCount = 1, lastReadInboxMessageId = 4))
            runCurrent()

            assertEquals(1, viewModel.state.unreadCount)
            assertEquals(outline, viewModel.outline())
            assertTrue("unread" in outline)
        }
}
