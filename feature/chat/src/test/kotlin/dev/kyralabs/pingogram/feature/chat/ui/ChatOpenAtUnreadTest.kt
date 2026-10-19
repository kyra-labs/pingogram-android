package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [ChatViewModel] opening a chat with unread messages: around the last message read
 * ([FakeMessageRepository.loadAround]), so the unread divider is there however many messages are unread, anchored
 * near the top ([AnchorTarget.Unread]); at the newest message when nothing is unread. Then newer pages load near the
 * newest loaded message until the end, and the jump to the newest message loads the newest page.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatOpenAtUnreadTest : ChatViewModelHarness() {
    /** Incoming messages [range] of the private chat. */
    private fun ids(range: LongRange) = range.map { message(it) }.toTypedArray()

    /** The private chat with [unread] messages unread after message [lastRead]. */
    private fun unreadChat(
        unread: Int,
        lastRead: Long,
    ) = chat(unreadCount = unread, lastReadInboxMessageId = lastRead)

    // Opening

    @Test
    fun `with many unread messages it opens around the last one read, not at the newest page`() =
        runTest {
            // 101 unread: the newest page in memory does not reach the first unread message, 101.
            chats.setChats(unreadChat(unread = 101, lastRead = 100))
            messages.setHistory(PRIVATE_CHAT, history(*ids(152L..201L)))
            messages.answerAround(PRIVATE_CHAT, window(81L..130L))

            val viewModel = viewModel()

            assertEquals(listOf("around:100"), messages.loads)
            assertEquals(AnchorTarget.Unread, viewModel.anchor.target)
            val outline = viewModel.outline()
            assertEquals(outline.indexOf("m101") + 1, outline.indexOf("unread"))
            assertEquals(outline.indexOf("m100") - 1, outline.indexOf("unread"))
            assertFalse(viewModel.content.reachedEnd)
        }

    @Test
    fun `with a few unread messages it opens at the divider, in a history that reaches the newest message`() =
        runTest {
            chats.setChats(unreadChat(unread = 2, lastRead = 10))
            messages.setHistory(PRIVATE_CHAT, history(message(9), message(10), message(11), message(12)))

            val viewModel = viewModel()

            assertEquals(listOf("around:10"), messages.loads)
            assertEquals(AnchorTarget.Unread, viewModel.anchor.target)
            assertEquals(listOf("m12", "m11", "unread", "m10", "m9"), viewModel.outline().take(5))
            assertTrue(viewModel.content.reachedEnd)
        }

    @Test
    fun `with nothing unread it opens at the newest message, without a divider`() =
        runTest {
            chats.setChats(chat())
            messages.setHistory(PRIVATE_CHAT, history(message(9), message(10)))

            val viewModel = viewModel()

            assertEquals(listOf("newest"), messages.loads)
            assertEquals(AnchorTarget.Newest, viewModel.anchor.target)
            assertFalse("unread" in viewModel.outline())
        }

    @Test
    fun `a message search found wins over the unread divider`() =
        runTest {
            chats.setChats(unreadChat(unread = 5, lastRead = 100))
            messages.answerAround(PRIVATE_CHAT, window(40L..60L))

            val viewModel = viewModel(messageId = 50)

            assertEquals(listOf("around:50"), messages.loads)
            assertEquals(AnchorTarget.Message(50), viewModel.anchor.target)
        }

    @Test
    fun `the divider stays where it was while the messages are read`() =
        runTest {
            chats.setChats(unreadChat(unread = 2, lastRead = 10))
            messages.setHistory(PRIVATE_CHAT, history(message(10), message(11), message(12)))
            val viewModel = viewModel()

            chats.setChats(unreadChat(unread = 0, lastRead = 12))
            send(viewModel, ChatEvent.MessagesVisible(setOf(11, 12)))

            assertEquals(listOf("m12", "m11", "unread", "m10"), viewModel.outline().take(4))
        }

    @Test
    fun `after process death the divider stays, and the list opens where it was`() =
        runTest {
            val handle = SavedStateHandle()
            chats.setChats(unreadChat(unread = 101, lastRead = 100))
            messages.answerAround(PRIVATE_CHAT, window(81L..130L))
            val first = viewModel(handle = handle)
            send(first, ChatEvent.MessagesVisible(setOf(98, 99, 100, 101, 102)))
            // Read meanwhile.
            chats.setChats(unreadChat(unread = 99, lastRead = 102))

            val saved = handle.keys().associateWith { handle.get<Any>(it) }
            val restored = viewModel(handle = SavedStateHandle(saved))

            assertEquals("around:102", messages.loads.last())
            assertEquals(AnchorTarget.Message(102, highlight = false), restored.anchor.target)
            val outline = restored.outline()
            assertEquals(outline.indexOf("m101") + 1, outline.indexOf("unread"))
        }

    @Test
    fun `a rotation keeps the view model, and with it the anchor`() =
        runTest {
            chats.setChats(unreadChat(unread = 2, lastRead = 10))
            messages.setHistory(PRIVATE_CHAT, history(message(10), message(11), message(12)))
            val viewModel = viewModel()
            val anchor = viewModel.anchor

            send(viewModel, ChatEvent.Stopped, ChatEvent.Started)

            assertEquals(anchor, viewModel.anchor)
            assertEquals(listOf("around:10"), messages.loads)
        }

    // Newer pages

    @Test
    fun `near the newest loaded message, newer pages load until the end`() =
        runTest {
            chats.setChats(unreadChat(unread = 50, lastRead = 100))
            messages.answerAround(PRIVATE_CHAT, window(81L..130L))
            val pages = ArrayDeque(listOf(window(81L..140L), history(*ids(81L..150L))))
            messages.onLoadNewer = { chatId ->
                pages.removeFirstOrNull()?.let { messages.setHistory(chatId, it.copy(reachedStart = false)) }
                Outcome.Success(Unit)
            }
            val viewModel = viewModel()

            send(viewModel, ChatEvent.NearNewestChanged(true))

            assertEquals(listOf("around:100", "newer", "newer"), messages.loads)
            assertTrue(viewModel.content.reachedEnd)
            assertEquals(150L, viewModel.bubbles.first().id)
        }

    @Test
    fun `moving away from the newest loaded message stops loading after the page`() =
        runTest {
            chats.setChats(unreadChat(unread = 50, lastRead = 100))
            messages.answerAround(PRIVATE_CHAT, window(81L..130L))
            val viewModel = viewModel()
            val page = CompletableDeferred<Outcome<Unit>>()
            messages.onLoadNewer = { page.await() }
            send(viewModel, ChatEvent.NearNewestChanged(true))

            send(viewModel, ChatEvent.NearNewestChanged(false))
            page.complete(Outcome.Success(Unit))
            runCurrent()

            assertEquals(listOf("around:100", "newer"), messages.loads)
        }

    @Test
    fun `nothing newer loads once the history reaches the newest message`() =
        runTest {
            val viewModel = showing(chat(), history(message(1), message(2)))

            send(viewModel, ChatEvent.NearNewestChanged(true))

            assertFalse("newer" in messages.loads)
        }

    @Test
    fun `a failed newer page keeps the messages and shows the error, and retry loads it again`() =
        runTest {
            chats.setChats(unreadChat(unread = 50, lastRead = 100))
            messages.answerAround(PRIVATE_CHAT, window(81L..130L))
            messages.onLoadNewer = { Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()

            send(viewModel, ChatEvent.NearNewestChanged(true))
            assertEquals(DomainError.Network, viewModel.content.newerError)
            assertFalse(viewModel.content.isLoadingNewer)
            assertEquals(130L, viewModel.bubbles.first().id)

            messages.onLoadNewer = { chatId ->
                messages.setHistory(chatId, history(*ids(81L..140L), reachedStart = false))
                Outcome.Success(Unit)
            }
            send(viewModel, ChatEvent.Retry)

            assertNull(viewModel.content.newerError)
            assertEquals(140L, viewModel.bubbles.first().id)
        }

    // The newest message

    @Test
    fun `the jump to the newest message loads the newest page in place of the loaded one, anchored there`() =
        runTest {
            chats.setChats(unreadChat(unread = 101, lastRead = 100))
            messages.answerAround(PRIVATE_CHAT, window(81L..130L))
            val viewModel = viewModel()
            val before = viewModel.anchor.id
            messages.onLoadNewest = { chatId ->
                messages.replaceHistory(chatId, history(*ids(152L..201L), reachedStart = false))
                Outcome.Success(Unit)
            }

            send(viewModel, ChatEvent.ShowNewest)

            assertEquals("newest", messages.loads.last())
            assertEquals(ListAnchor(before + 1, AnchorTarget.Newest), viewModel.anchor)
            assertEquals(201L, viewModel.bubbles.first().id)
            assertTrue(viewModel.content.reachedEnd)
        }

    @Test
    fun `a jump to the newest message that fails keeps the messages and says why`() =
        runTest {
            chats.setChats(unreadChat(unread = 101, lastRead = 100))
            messages.answerAround(PRIVATE_CHAT, window(81L..130L))
            val viewModel = viewModel()
            val before = viewModel.anchor
            messages.onLoadNewest = { Outcome.Failure(DomainError.Network) }

            send(viewModel, ChatEvent.ShowNewest)

            assertEquals(before, viewModel.anchor)
            assertEquals(130L, viewModel.bubbles.first().id)
            assertFalse(viewModel.content.isLoadingNewer)
        }
}
