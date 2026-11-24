package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.FoundEvent
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.TextFindings
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [ChatViewModel]'s message actions for what the device finds in a message's text (`REQUIREMENTS.md` FR-X3): asked
 * only when the actions open, "Add to calendar" for a date or time and "Open in maps" for an address, which open the
 * calendar's new event and a maps app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatFoundTextTest : ChatViewModelHarness() {
    private fun TestScope.chatWith(vararg texts: String): ChatViewModel {
        users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"))
        chats.setChats(chat())
        val items =
            texts.mapIndexed { index, words -> message(index + 1L, content = MessageContent.Text(text(words), null)) }
        messages.setHistory(PRIVATE_CHAT, history(*items.toTypedArray()))
        return viewModel()
    }

    @Test
    fun `the text is classified only when the actions open`() =
        runViewModelTest {
            val viewModel = chatWith(PARTY)

            assertTrue(textFinder.asked.isEmpty())
            send(viewModel, ChatEvent.ShowMessageActions(1))

            assertEquals(listOf(PARTY), textFinder.asked)
        }

    @Test
    fun `a date and an address offer the calendar and maps, after translating`() =
        runViewModelTest {
            textFinder.findings[PARTY] = TextFindings(FoundEvent(START, isAllDay = false), ADDRESS)
            val viewModel = chatWith(PARTY)

            send(viewModel, ChatEvent.ShowMessageActions(1))

            val actions = viewModel.state.messageActions!!.actions
            val calendar = actions.indexOf(MessageAction.AddToCalendar)
            assertEquals(calendar + 1, actions.indexOf(MessageAction.OpenInMaps))
            assertTrue(calendar > actions.indexOf(MessageAction.Copy))
        }

    @Test
    fun `the actions open without waiting for the classifier, and what it finds joins them`() =
        runViewModelTest {
            textFinder.findings[PARTY] = TextFindings(FoundEvent(START, isAllDay = false), ADDRESS)
            val classifier = CompletableDeferred<Unit>()
            textFinder.held = classifier
            val viewModel = chatWith(PARTY)

            send(viewModel, ChatEvent.ShowMessageActions(1))

            val shown = viewModel.state.messageActions!!.actions
            assertTrue(MessageAction.Copy in shown)
            assertFalse(MessageAction.AddToCalendar in shown)

            classifier.complete(Unit)
            runCurrent()

            val actions = viewModel.state.messageActions!!.actions
            assertTrue(MessageAction.AddToCalendar in actions && MessageAction.OpenInMaps in actions)
        }

    @Test
    fun `nothing found offers neither`() =
        runViewModelTest {
            val viewModel = chatWith("See you")

            send(viewModel, ChatEvent.ShowMessageActions(1))

            val actions = viewModel.state.messageActions!!.actions
            assertFalse(MessageAction.AddToCalendar in actions || MessageAction.OpenInMaps in actions)
        }

    @Test
    fun `add to calendar opens a new event titled with the first line, and open in maps the address`() =
        runViewModelTest {
            textFinder.findings[PARTY] = TextFindings(FoundEvent(START, isAllDay = false), ADDRESS)
            val viewModel = chatWith(PARTY)

            viewModel.effects.test {
                send(viewModel, ChatEvent.ShowMessageActions(1))
                send(viewModel, MessageAction.AddToCalendar.eventFor(viewModel.state.messageActions!!))
                assertEquals(ReplyEffect.AddToCalendar("Party!", PARTY, START, isAllDay = false), awaitItem())
                assertNull(viewModel.state.messageActions)

                send(viewModel, ChatEvent.ShowMessageActions(1))
                send(viewModel, MessageAction.OpenInMaps.eventFor(viewModel.state.messageActions!!))
                assertEquals(ReplyEffect.OpenInMaps(ADDRESS), awaitItem())
            }
        }

    private companion object {
        const val PARTY = "Party!\nFriday 20 November at 19:00, 10 Downing Street, London"
        const val ADDRESS = "10 Downing Street, London"
        const val START = 1_763_665_200_000L
    }
}
