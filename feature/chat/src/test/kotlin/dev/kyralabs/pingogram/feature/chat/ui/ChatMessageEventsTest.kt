package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.basicGroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollSheet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The events [ChatViewModel.onEvent] hands on rather than routing itself, through [ChatMessageEvents],
 * [ChatAttachEvents] and [dispatch]: each reaches its part, past the parts that do not take it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatMessageEventsTest : ChatViewModelHarness() {
    private val effects = mutableListOf<ChatEffect>()

    /** A group showing message [MESSAGE]; the effects it sends go into [effects]. */
    private fun TestScope.group(): ChatViewModel {
        groups.basicGroupMap.value = mapOf(ChatFixtures.BASIC_GROUP_ID to basicGroup(3))
        val viewModel = showing(groupChat, history(message(MESSAGE, GROUP_CHAT)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.collect { effects += it } }
        return viewModel
    }

    @Test
    fun `the message actions take their events`() =
        runViewModelTest {
            val viewModel = group()

            send(viewModel, ChatEvent.ShowMessageActions(MESSAGE))
            assertEquals(MESSAGE, viewModel.state.messageActions?.messageId)

            send(viewModel, ChatEvent.DismissMessageActions)
            assertNull(viewModel.state.messageActions)
        }

    @Test
    fun `the attachments take theirs, past the message actions`() =
        runViewModelTest {
            val viewModel = group()

            send(viewModel, ChatEvent.OpenPoll)
            assertEquals(PollSheet(isChannel = false, canSchedule = true), viewModel.state.poll)

            send(viewModel, ChatEvent.DismissPoll)
            assertNull(viewModel.state.poll)
        }

    @Test
    fun `a poll's statistics open through the effects`() =
        runViewModelTest {
            val viewModel = group()

            send(viewModel, PollEvent.OpenStatistics(MESSAGE))

            assertEquals(listOf(ChatEffect.OpenPollStatistics(GROUP_CHAT, MESSAGE)), effects)
        }

    private companion object {
        const val MESSAGE = 7L
    }
}
