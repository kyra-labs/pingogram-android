package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatViewModel.Companion.MAX_OPEN_PAGES
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [ChatViewModel] opened at a message, as search opens it: the message is found in the history, loading older pages up
 * to [MAX_OPEN_PAGES], then scrolled to and highlighted ([ChatEffect.ScrollToMessage]); not found, the conversation
 * stays at the newest messages with [ChatEffect.MessageNotLoaded]. It happens once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatOpenAtMessageTest : ChatViewModelHarness() {
    /** Messages [ids] of the private chat, newest first, not at the start of the history. */
    private fun page(vararg ids: Long) = history(*ids.map { message(it) }.toTypedArray(), reachedStart = false)

    /** A view model of the private chat showing messages [ids], opened at [messageId]. */
    private fun TestScope.openedAt(
        messageId: Long,
        vararg ids: Long,
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        chats.setChats(chat())
        messages.setHistory(PRIVATE_CHAT, page(*ids))
        return viewModel(PRIVATE_CHAT, handle, messageId = messageId)
    }

    @Test
    fun `a loaded message is scrolled to and highlighted`() =
        runTest {
            val viewModel = openedAt(10, 10, 11)

            viewModel.effects.test { assertEquals(ChatEffect.ScrollToMessage(10), awaitItem()) }
            assertTrue(messages.loadRequests.isEmpty())
        }

    @Test
    fun `older pages load until the message is there`() =
        runTest {
            messages.answerLoads(
                PRIVATE_CHAT,
                Outcome.Success(page(8, 9, 10, 11)),
                Outcome.Success(page(6, 7, 8, 9, 10, 11)),
            )

            val viewModel = openedAt(6, 10, 11)

            viewModel.effects.test { assertEquals(ChatEffect.ScrollToMessage(6), awaitItem()) }
            runCurrent()
            assertEquals(2, messages.loadRequests.size)
            assertFalse(viewModel.content.isLoadingOlder)
        }

    @Test
    fun `the search loads up to its own cap, more than a quote's, then stays at the newest messages and says so`() =
        runTest {
            // Every page succeeds without the message, and the start never comes.
            val viewModel = openedAt(5, 10, 11)

            viewModel.effects.test { assertEquals(ChatEffect.MessageNotLoaded, awaitItem()) }
            assertEquals(MAX_OPEN_PAGES, messages.loadRequests.size)
            assertTrue(MAX_OPEN_PAGES > ChatViewModel.MAX_REPLY_PAGES)
        }

    @Test
    fun `a message missing among the loaded ones was deleted, and nothing loads for it`() =
        runTest {
            val viewModel = openedAt(11, 10, 12)

            viewModel.effects.test { assertEquals(ChatEffect.MessageNotLoaded, awaitItem()) }
            assertTrue(messages.loadRequests.isEmpty())
        }

    @Test
    fun `a page that fails shows its error`() =
        runTest {
            messages.answerLoads(PRIVATE_CHAT, Outcome.Failure(DomainError.Network))

            val viewModel = openedAt(5, 10, 11)

            viewModel.effects.test { assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem()) }
        }

    @Test
    fun `the unread divider stays where it was`() =
        runTest {
            chats.setChats(chat(unreadCount = 1, lastReadInboxMessageId = 10))
            messages.setHistory(PRIVATE_CHAT, page(11, 10))

            val viewModel = viewModel(PRIVATE_CHAT, messageId = 10)

            assertTrue("unread" in viewModel.outline())
        }

    @Test
    fun `it jumps once, and not again after process death`() =
        runTest {
            val handle = SavedStateHandle()
            openedAt(10, 10, 11, handle = handle).effects.test {
                assertEquals(ChatEffect.ScrollToMessage(10), awaitItem())
            }

            // The view model made again from the saved state, as after process death.
            val saved = handle.keys().associateWith { handle.get<Any>(it) }
            val restored = viewModel(PRIVATE_CHAT, SavedStateHandle(saved), messageId = 10)

            restored.effects.test { expectNoEvents() }
        }

    @Test
    fun `without a message to open at, it jumps nowhere`() =
        runTest {
            chats.setChats(chat())
            messages.setHistory(PRIVATE_CHAT, page(10, 11))

            viewModel().effects.test { expectNoEvents() }
        }

    @Test
    fun `a quote tapped meanwhile replaces the search`() =
        runViewModelTest {
            messages.onLoadOlder = loadsForever
            val viewModel = openedAt(5, 10, 11)

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenReply(11))
                assertEquals(ChatEffect.ScrollToMessage(11), awaitItem())
                expectNoEvents()
            }
        }
}
