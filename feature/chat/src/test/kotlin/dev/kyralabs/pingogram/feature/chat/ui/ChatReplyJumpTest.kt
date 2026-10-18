package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.replyTo
import dev.kyralabs.pingogram.feature.chat.ui.ChatViewModel.Companion.MAX_REPLY_PAGES
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
 * Tapping a reply quote in [ChatViewModel] ([ChatEvent.OpenReply]): the quote knows its message; a loaded one is
 * scrolled to; older pages load until it is there, the start is reached or the cap is hit; and a new tap or leaving
 * the screen cancels the search.
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

    // Found

    @Test
    fun `a loaded message is scrolled to`() =
        runTest {
            val viewModel = showing(chat(), history(message(1), message(2, replyTo = replyTo(1))))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenReply(1))
                assertEquals(ChatEffect.ScrollToMessage(1), awaitItem())
            }
            assertTrue(messages.loadRequests.isEmpty())
        }

    @Test
    fun `an item of an album is scrolled to through its album`() =
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

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenReply(1))
                assertEquals(ChatEffect.ScrollToMessage(1), awaitItem())
            }
        }

    @Test
    fun `older pages load until the message is there`() =
        runTest {
            val viewModel = showing(chat(), page(10, 11))
            messages.answerLoads(
                PRIVATE_CHAT,
                Outcome.Success(page(8, 9, 10, 11)),
                Outcome.Success(page(6, 7, 8, 9, 10, 11)),
            )

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenReply(6))
                assertEquals(ChatEffect.ScrollToMessage(6), awaitItem())
            }
            runCurrent()
            assertEquals(2, messages.loadRequests.size)
            assertFalse(viewModel.content.isLoadingOlder)
        }

    @Test
    fun `the older pages' indicator shows while the search loads them`() =
        runViewModelTest {
            messages.onLoadOlder = loadsForever
            val viewModel = showing(chat(), page(10, 11))

            send(viewModel, ChatEvent.OpenReply(5))

            assertTrue(viewModel.content.isLoadingOlder)
        }

    // Not found

    @Test
    fun `a message missing among the loaded ones was deleted, and nothing loads for it`() =
        runTest {
            val viewModel = showing(chat(), page(10, 12))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenReply(11))
                assertEquals(ChatEffect.MessageUnavailable, awaitItem())
            }
            assertTrue(messages.loadRequests.isEmpty())
        }

    @Test
    fun `reaching the start without the message says it is no longer available`() =
        runTest {
            val viewModel = showing(chat(), page(10, 11))
            // The first load reaches the start.
            messages.answerLoads(PRIVATE_CHAT)

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenReply(5))
                assertEquals(ChatEffect.MessageUnavailable, awaitItem())
            }
            runCurrent()
            assertEquals(1, messages.loadRequests.size)
            assertFalse(viewModel.content.isLoadingOlder)
        }

    @Test
    fun `the search gives up after its cap of pages`() =
        runTest {
            // Every page succeeds without the message, and the start never comes.
            val viewModel = showing(chat(), page(10, 11))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenReply(5))
                assertEquals(ChatEffect.MessageUnavailable, awaitItem())
            }
            assertEquals(MAX_REPLY_PAGES, messages.loadRequests.size)
        }

    @Test
    fun `a page that fails shows its error`() =
        runTest {
            val viewModel = showing(chat(), page(10, 11))
            messages.answerLoads(PRIVATE_CHAT, Outcome.Failure(DomainError.Network))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenReply(5))
                assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
            }
        }

    // Cancellation

    @Test
    fun `a new tap cancels the search`() =
        runViewModelTest {
            messages.onLoadOlder = loadsForever
            val viewModel = showing(chat(), page(10, 11))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenReply(5), ChatEvent.OpenReply(11))
                assertEquals(ChatEffect.ScrollToMessage(11), awaitItem())
                expectNoEvents()
            }
            runCurrent()
            assertFalse(viewModel.content.isLoadingOlder)
        }

    @Test
    fun `leaving the screen cancels the search`() =
        runViewModelTest {
            val gate = CompletableDeferred<Unit>()
            messages.onLoadOlder = {
                gate.await()
                messages.setHistory(PRIVATE_CHAT, page(5, 10, 11))
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

    private companion object {
        const val ALBUM = 7L
    }
}
