package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.domain.model.RichMessage
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.rich.isOpen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Rich messages in [ChatViewModel]: opening and closing their sections and showing all of a long one, copied into
 * their bubble only and kept across process death, and loading the full message, once, with its failure as a
 * snackbar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRichMessagesTest : ChatViewModelHarness() {
    private val effects = mutableListOf<ChatEffect>()

    private fun TestScope.conversation(
        rich: RichMessage = sample(),
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        users.setUsers(user(ME, firstName = "Me"), user(ADA, firstName = "Ada"))
        chats.setChats(groupChat)
        messages.setHistory(
            GROUP_CHAT,
            history(
                message(OTHER, GROUP_CHAT, MessageSender.User(ADA)),
                message(MESSAGE, GROUP_CHAT, MessageSender.User(ADA), content = MessageContent.RichMessage(rich)),
            ),
        )
        val viewModel = viewModel(GROUP_CHAT, handle = handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel
    }

    @Nested
    inner class Sections {
        @Test
        fun `a section starts as Telegram says, and a tap toggles it in its bubble only`() =
            runViewModelTest {
                val viewModel = conversation()
                val other = viewModel.bubble(OTHER)
                assertFalse(viewModel.bubble(MESSAGE).rich.isOpen(closed))
                assertTrue(viewModel.bubble(MESSAGE).rich.isOpen(open))

                send(viewModel, RichMessageEvent.ToggleSection(MESSAGE, closed.id))
                send(viewModel, RichMessageEvent.ToggleSection(MESSAGE, open.id))

                val bubble = viewModel.bubble(MESSAGE)
                assertTrue(bubble.rich.isOpen(closed))
                assertFalse(bubble.rich.isOpen(open))
                assertSame(other, viewModel.bubble(OTHER))
            }

        @Test
        fun `a second tap closes it again`() =
            runViewModelTest {
                val viewModel = conversation()

                send(
                    viewModel,
                    RichMessageEvent.ToggleSection(MESSAGE, closed.id),
                    RichMessageEvent.ToggleSection(MESSAGE, closed.id),
                )

                assertFalse(viewModel.bubble(MESSAGE).rich.isOpen(closed))
            }

        @Test
        fun `opened sections and a message shown whole survive process death`() =
            runViewModelTest {
                val handle = SavedStateHandle()
                val first = conversation(handle = handle)
                send(
                    first,
                    RichMessageEvent.ToggleSection(MESSAGE, closed.id),
                    RichMessageEvent.ShowAll(MESSAGE),
                )
                clear(first)

                val restored =
                    conversation(handle = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))

                val rich = restored.bubble(MESSAGE).rich
                assertEquals(persistentSetOf(closed.id), rich?.toggledSections)
                assertEquals(true, rich?.showsAll)
                assertTrue(rich.isOpen(closed))
            }

        @Test
        fun `a message nobody changed carries no rich state`() =
            runViewModelTest {
                assertNull(conversation().bubble(MESSAGE).rich)
            }
    }

    @Nested
    inner class FullMessage {
        @Test
        fun `Show full message loads it once, showing that it loads meanwhile`() =
            runViewModelTest {
                val answer = CompletableDeferred<Outcome<Unit>>()
                messages.onLoadFullRichMessage = { _, _ -> answer.await() }
                val viewModel = conversation(sample(isFull = false))

                send(viewModel, RichMessageEvent.LoadFull(MESSAGE), RichMessageEvent.LoadFull(MESSAGE))

                assertEquals(listOf(GROUP_CHAT to MESSAGE), messages.fullRichMessageRequests)
                assertEquals(true, viewModel.bubble(MESSAGE).rich?.isLoadingFull)

                answer.complete(Outcome.Success(Unit))
                runCurrent()

                assertFalse(viewModel.bubble(MESSAGE).rich?.isLoadingFull == true)
                assertTrue(effects.isEmpty())
            }

        @Test
        fun `a failed load is a snackbar, and a later tap tries again`() =
            runViewModelTest {
                messages.onLoadFullRichMessage = { _, _ -> Outcome.Failure(DomainError.Network) }
                val viewModel = conversation(sample(isFull = false))

                send(viewModel, RichMessageEvent.LoadFull(MESSAGE))
                send(viewModel, RichMessageEvent.LoadFull(MESSAGE))

                assertEquals(List(2) { ChatEffect.ShowError(DomainError.Network) }, effects)
                assertEquals(2, messages.fullRichMessageRequests.size)
                assertFalse(viewModel.bubble(MESSAGE).rich?.isLoadingFull == true)
            }
    }

    private companion object {
        const val OTHER = 10L
        const val MESSAGE = 11L

        fun text(value: String) = FormattedText(value, persistentListOf())

        val closed =
            RichBlock.Details(0, text("Features"), persistentListOf(RichBlock.Paragraph(text("Fast"))), isOpen = false)
        val open =
            RichBlock.Details(1, text("Changes"), persistentListOf(RichBlock.Paragraph(text("New"))), isOpen = true)

        fun sample(isFull: Boolean = true) =
            RichMessage(
                persistentListOf(RichBlock.Title(text("Supernova")), closed, open),
                isRtl = false,
                isFull = isFull,
            )
    }
}
