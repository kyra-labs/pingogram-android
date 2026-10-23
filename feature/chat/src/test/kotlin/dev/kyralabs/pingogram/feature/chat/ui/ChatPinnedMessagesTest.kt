package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.CHANNEL_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedSheet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pinned messages bar of [ChatViewModel] (`REQUIREMENTS.md` FR-C9): what it shows, following the messages on screen
 * and cycling through the pins as its taps show each in the list, unpinning or hiding, and the sheet of every pinned
 * message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatPinnedMessagesTest : ChatViewModelHarness() {
    private val messagesOfAda = (1L..5L).map { message(it) }

    /** The private chat with Ada, messages 1–5 loaded, [pinned] of them pinned, its sheet collected. */
    private fun TestScope.pinning(vararg pinned: Long): ChatViewModel {
        users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"))
        pinnedMessages.setPinned(PRIVATE_CHAT, *messagesOfAda.filter { it.id in pinned }.toTypedArray())
        val viewModel = showing(chat(), history(*messagesOfAda.toTypedArray()))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.pinnedSheet.collect {} }
        runCurrent()
        return viewModel
    }

    private val ChatViewModel.bar get() = state.pinnedBar

    private fun TestScope.visible(
        viewModel: ChatViewModel,
        vararg messageIds: Long,
    ) = send(viewModel, ChatEvent.MessagesVisible(messageIds.toSet()))

    @Test
    fun `shows no bar without pinned messages`() =
        runViewModelTest {
            assertNull(pinning().bar)
        }

    @Test
    fun `shows the newest pinned message, the last of them, with its sender, and that the user may unpin it`() =
        runViewModelTest {
            val bar = checkNotNull(pinning(2, 4).bar)

            assertEquals(listOf<Any>(4L, 1, 2), listOf(bar.messageId, bar.position, bar.count))
            assertEquals(UiText.Plain("Ada Lovelace").bidiIsolated(), bar.sender)
            assertEquals(UiText.Plain("Message 4"), bar.preview)
            assertTrue(bar.canUnpin)
        }

    @Test
    fun `the bar shows the pinned message closest above the messages on screen as the list scrolls`() =
        runViewModelTest {
            val viewModel = pinning(1, 2, 4)

            visible(viewModel, 4, 5)
            assertEquals(listOf<Any>(4L, 2), viewModel.bar?.let { listOf(it.messageId, it.position) })
            visible(viewModel, 3)
            assertEquals(listOf<Any>(2L, 1), viewModel.bar?.let { listOf(it.messageId, it.position) })
            visible(viewModel, 1, 2)
            assertEquals(2L, viewModel.bar?.messageId)
            visible(viewModel, 1)
            assertEquals(listOf<Any>(1L, 0), viewModel.bar?.let { listOf(it.messageId, it.position) })
            visible(viewModel, 5)
            assertEquals(4L, viewModel.bar?.messageId)
        }

    @Test
    fun `above every pinned message the bar shows the oldest`() =
        runViewModelTest {
            val viewModel = pinning(2, 4)

            visible(viewModel, 1)

            assertEquals(listOf<Any>(2L, 0), viewModel.bar?.let { listOf(it.messageId, it.position) })
        }

    @Test
    fun `a tap shows the message in the list and moves on to the next older one, then back to the newest`() =
        runViewModelTest {
            val viewModel = pinning(2, 4)
            visible(viewModel, 5)

            send(viewModel, PinnedEvent.ShowCurrent)
            assertEquals(AnchorTarget.Message(4), viewModel.anchor.target)
            visible(viewModel, 3, 4)
            assertEquals(listOf<Any>(2L, 0), viewModel.bar?.let { listOf(it.messageId, it.position) })

            send(viewModel, PinnedEvent.ShowCurrent)
            assertEquals(AnchorTarget.Message(2), viewModel.anchor.target)
            visible(viewModel, 1, 2)
            assertEquals(listOf<Any>(4L, 1), viewModel.bar?.let { listOf(it.messageId, it.position) })
        }

    @Test
    fun `after a tap the bar follows the screen again once the user scrolls`() =
        runViewModelTest {
            val viewModel = pinning(2, 4)
            visible(viewModel, 5)
            send(viewModel, PinnedEvent.ShowCurrent)
            visible(viewModel, 4, 5)
            assertEquals(2L, viewModel.bar?.messageId)

            send(viewModel, PinnedEvent.ListDragged)

            assertEquals(4L, viewModel.bar?.messageId)
        }

    @Test
    fun `the bar goes back to the newest when its message is unpinned`() =
        runViewModelTest {
            val viewModel = pinning(2, 4)
            send(viewModel, PinnedEvent.ShowCurrent)

            pinnedMessages.setPinned(PRIVATE_CHAT, messagesOfAda[3])
            runCurrent()

            assertEquals(listOf<Any>(4L, 0, 1), viewModel.bar?.let { listOf(it.messageId, it.position, it.count) })
        }

    @Test
    fun `closing unpins the shown message where the user may`() =
        runViewModelTest {
            val viewModel = pinning(2, 4)
            send(viewModel, PinnedEvent.ShowCurrent)

            send(viewModel, PinnedEvent.Close)

            assertTrue("unpin:$PRIVATE_CHAT:2" in messageActions.calls)
            assertTrue(hiddenPins.hidden.value.isEmpty())
        }

    @Test
    fun `closing hides the bar for others, until a newer message is pinned`() =
        runViewModelTest {
            messageActions.onProperties =
                { Outcome.Success(FakeMessageActionRepository.ALL_ALLOWED.copy(canBePinned = false)) }
            val viewModel = pinning(2, 4)
            assertFalse(checkNotNull(viewModel.bar).canUnpin)

            viewModel.effects.test {
                send(viewModel, PinnedEvent.Close)
                assertEquals(ChatEffect.PinnedHidden, awaitItem())
            }
            assertNull(viewModel.bar)
            assertTrue(messageActions.calls.none { it.startsWith("unpin") })

            pinnedMessages.setPinned(PRIVATE_CHAT, messagesOfAda[1], messagesOfAda[3], messagesOfAda[4])
            runCurrent()
            assertEquals(5L, viewModel.bar?.messageId)
        }

    @Test
    fun `a bar that cannot be hidden stays, with the error`() =
        runViewModelTest {
            messageActions.onProperties =
                { Outcome.Success(FakeMessageActionRepository.ALL_ALLOWED.copy(canBePinned = false)) }
            hiddenPins.onHide = { _, _ -> Outcome.Failure(DomainError.Unknown(0, "Disk full")) }
            val viewModel = pinning(4)

            viewModel.effects.test {
                send(viewModel, PinnedEvent.Close)
                assertEquals(ChatEffect.ShowError(DomainError.Unknown(0, "Disk full")), awaitItem())
            }
            assertEquals(4L, viewModel.bar?.messageId)
        }

    @Test
    fun `an unpin that fails says why`() =
        runViewModelTest {
            messageActions.onAction = { Outcome.Failure(DomainError.Network) }
            val viewModel = pinning(4)

            viewModel.effects.test {
                send(viewModel, PinnedEvent.Close)
                assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
            }
        }

    @Test
    fun `the sheet lists every pinned message oldest first, and a tapped one shows in the list and in the bar`() =
        runViewModelTest {
            val viewModel = pinning(1, 2, 4)
            assertNull(viewModel.pinnedSheet.value)

            send(viewModel, PinnedEvent.OpenList)
            val sheet = checkNotNull(viewModel.pinnedSheet.value)
            assertEquals(listOf(1L, 2L, 4L), sheet.rows.map { it.messageId })
            assertEquals(4L, sheet.shownId)

            send(viewModel, PinnedEvent.ShowFromList(1))
            assertNull(viewModel.pinnedSheet.value)
            assertEquals(AnchorTarget.Message(1), viewModel.anchor.target)
            assertEquals(listOf<Any>(1L, 0), viewModel.bar?.let { listOf(it.messageId, it.position) })
        }

    @Test
    fun `dismissing the sheet closes it`() =
        runViewModelTest {
            val viewModel = pinning(4)
            send(viewModel, PinnedEvent.OpenList)

            send(viewModel, PinnedEvent.DismissList)

            assertEquals(null as PinnedSheet?, viewModel.pinnedSheet.value)
        }

    @Test
    fun `a channel's own posts name no sender`() =
        runViewModelTest {
            val post = message(7, chatId = CHANNEL_CHAT, sender = MessageSender.Chat(CHANNEL_CHAT))
            pinnedMessages.setPinned(CHANNEL_CHAT, post)

            val viewModel = showing(channelChat, history(post))

            assertNull(viewModel.bar?.sender)
            assertEquals(7L, viewModel.bar?.messageId)
        }
}
