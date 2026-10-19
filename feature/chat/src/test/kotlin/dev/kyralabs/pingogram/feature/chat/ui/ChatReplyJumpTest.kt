package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.Event
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.replyTo
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.window
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
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
 * Tapping a reply quote in [ChatViewModel] ([ChatEvent.OpenReply]): the quote knows its message; a loaded one gets an
 * anchor at once; one far back loads the messages around it ([FakeMessageRepository.loadAround]) and shows them with
 * the anchor; a deleted one says so; a new tap or leaving the screen cancels the load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatReplyJumpTest : ChatViewModelHarness() {
    /** Messages [ids] of the private chat, newest first, not at the start of the history. */
    private fun page(vararg ids: Long) = history(*ids.map { message(it) }.toTypedArray(), reachedStart = false)

    // The quote

    @Test
    fun `the quote of a reply in this chat opens it, one of another chat does not`() =
        runTest {
            val viewModel =
                showing(
                    chat(),
                    history(
                        message(1),
                        message(2, replyTo = replyTo(1)),
                        message(3, replyTo = replyTo(9, chatId = ChatFixtures.GROUP_CHAT)),
                    ),
                )

            assertEquals(1L, viewModel.bubble(2).reply?.messageId)
            assertNull(viewModel.bubble(3).reply?.messageId)
        }

    // Loaded

    @Test
    fun `a loaded message gets a new anchor at once, highlighted, and nothing loads`() =
        runTest {
            val viewModel = showing(chat(), history(message(1), message(2, replyTo = replyTo(1))))
            val before = viewModel.anchor.id

            send(viewModel, ChatEvent.OpenReply(1))

            assertEquals(ListAnchor(before + 1, AnchorTarget.Message(1)), viewModel.anchor)
            assertEquals(listOf("newest"), messages.loads)
        }

    @Test
    fun `tapping the same quote again anchors the list again`() =
        runTest {
            val viewModel = showing(chat(), history(message(1), message(2, replyTo = replyTo(1))))

            send(viewModel, ChatEvent.OpenReply(1))
            val first = viewModel.anchor.id
            send(viewModel, ChatEvent.OpenReply(1))

            assertEquals(first + 1, viewModel.anchor.id)
        }

    @Test
    fun `an item of an album is anchored by its own message, which its album shows`() =
        runTest {
            val photo = ContentPreviewData.photo(minithumbnail = null)
            val viewModel =
                showing(
                    chat(),
                    history(
                        message(1, content = photo, mediaAlbumId = ALBUM),
                        message(2, content = photo, mediaAlbumId = ALBUM),
                        message(3, replyTo = replyTo(1)),
                    ),
                )

            send(viewModel, ChatEvent.OpenReply(1))

            assertEquals(AnchorTarget.Message(1), viewModel.anchor.target)
            assertTrue(viewModel.items.single { it.showsMessage(1) } is ChatItem.Bubble)
        }

    // Far back

    @Test
    fun `a message far back loads the messages around it in place of the loaded ones`() =
        runTest {
            val viewModel = showing(chat(), page(100, 101))
            messages.answerAround(PRIVATE_CHAT, window(4L..6L))

            send(viewModel, ChatEvent.OpenReply(5))

            assertEquals("around:5", messages.loads.last())
            assertEquals(AnchorTarget.Message(5), viewModel.anchor.target)
            assertEquals(listOf(6L, 5L, 4L), viewModel.bubbles.map { it.id })
            assertTrue(messages.loadRequests.isEmpty())
        }

    @Test
    fun `the messages around it come with their anchor, in one state`() =
        runTest {
            val viewModel = showing(chat(), page(100, 101))
            val before = viewModel.anchor
            messages.answerAround(PRIVATE_CHAT, window(4L..6L))

            viewModel.uiState.test {
                awaitItem()
                send(viewModel, ChatEvent.OpenReply(5))
                val contents =
                    cancelAndConsumeRemainingEvents().mapNotNull { event ->
                        (event as? Event.Item)?.value?.content as? ChatContent.Content
                    }
                val withNew = contents.filter { it.shows(5) }
                val withOld = contents.filter { it.shows(100) }

                assertTrue(withNew.isNotEmpty())
                assertTrue(withNew.all { it.anchor.target == AnchorTarget.Message(5) })
                assertTrue(withOld.all { it.anchor == before })
            }
        }

    @Test
    fun `the older pages' indicator shows while the messages around it load`() =
        runViewModelTest {
            messages.onLoadAround = { _, _ -> CompletableDeferred<Outcome<Unit>>().await() }
            val viewModel = showing(chat(), page(10, 11))

            send(viewModel, ChatEvent.OpenReply(5))

            assertTrue(viewModel.content.isLoadingOlder)
            assertEquals(listOf(11L, 10L), viewModel.bubbles.map { it.id })
        }

    // Gone

    @Test
    fun `a message missing among the loaded ones was deleted, and nothing loads for it`() =
        runTest {
            val viewModel = showing(chat(), page(10, 12))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenReply(11))
                assertEquals(ChatEffect.MessageUnavailable, awaitItem())
            }
            assertEquals(listOf("newest"), messages.loads)
        }

    @Test
    fun `a message far back that no longer exists says so, and the list shows where it was`() =
        runTest {
            val viewModel = showing(chat(), page(100, 101))
            val withoutIt = history(message(4), message(6), reachedStart = false, reachedEnd = false)
            messages.answerAround(PRIVATE_CHAT, withoutIt)

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenReply(5))
                assertEquals(ChatEffect.MessageUnavailable, awaitItem())
            }
            assertEquals(AnchorTarget.Message(5), viewModel.anchor.target)
            assertEquals(listOf(6L, 4L), viewModel.bubbles.map { it.id })
        }

    @Test
    fun `messages around it that fail to load show the error and keep the loaded ones`() =
        runTest {
            val viewModel = showing(chat(), page(10, 11))
            val before = viewModel.anchor
            messages.answerAround(PRIVATE_CHAT, page(5), failure = Outcome.Failure(DomainError.Network))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenReply(5))
                assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
            }
            assertEquals(before, viewModel.anchor)
            assertEquals(listOf(11L, 10L), viewModel.bubbles.map { it.id })
            assertFalse(viewModel.content.isLoadingOlder)
        }

    // Cancellation

    @Test
    fun `a new tap cancels the load`() =
        runViewModelTest {
            messages.onLoadAround = { _, _ -> CompletableDeferred<Outcome<Unit>>().await() }
            val viewModel = showing(chat(), page(10, 11))

            send(viewModel, ChatEvent.OpenReply(5), ChatEvent.OpenReply(11))

            assertEquals(AnchorTarget.Message(11), viewModel.anchor.target)
            runCurrent()
            assertFalse(viewModel.content.isLoadingOlder)
        }

    @Test
    fun `leaving the screen cancels the load`() =
        runViewModelTest {
            val gate = CompletableDeferred<Unit>()
            messages.onLoadAround = { _, _ ->
                gate.await()
                messages.replaceHistory(PRIVATE_CHAT, window(4L..6L))
                Outcome.Success(Unit)
            }
            val viewModel = showing(chat(), page(10, 11))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenReply(5))
                clear(viewModel)
                gate.complete(Unit)
                runCurrent()
                expectNoEvents()
            }
        }

    /** The content shows message [id]. */
    private fun ChatContent.Content.shows(id: Long) = items.any { it.showsMessage(id) }

    private companion object {
        const val ALBUM = 7L
    }
}
