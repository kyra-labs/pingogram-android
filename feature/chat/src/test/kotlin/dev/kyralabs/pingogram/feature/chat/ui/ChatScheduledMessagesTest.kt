package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageSchedule
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.ScheduledMessage
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.outgoing
import dev.kyralabs.pingogram.feature.chat.ui.scheduled.ScheduledBar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The scheduled messages bar of [ChatViewModel] (`REQUIREMENTS.md` FR-C5): how many the chat holds and when the first
 * goes out, only while TDLib says it holds some, following them as they are sent; and a sent one joining the history.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatScheduledMessagesTest : ChatViewModelHarness() {
    private val withScheduled = chat().copy(hasScheduledMessages = true)

    private fun scheduledAt(
        id: Long,
        epochSecond: Long,
    ) = ScheduledMessage(outgoing(id), MessageSchedule.At(epochSecond))

    @Test
    fun `counts the scheduled messages and says when the first goes out`() =
        runViewModelTest {
            scheduledMessages.setScheduled(
                PRIVATE_CHAT,
                scheduledAt(11, TODAY_EVENING),
                ScheduledMessage(outgoing(12), MessageSchedule.WhenOnline),
            )

            val viewModel = showing(withScheduled, history(message(1)))

            val today = UiText.resource(R.string.chat_date_today)
            val next =
                UiText.resource(
                    R.string.scheduled_bar_next,
                    UiText.resource(R.string.scheduled_day_time, today, "6:30 PM"),
                )
            assertEquals(ScheduledBar(2, next), viewModel.state.scheduledBar)
        }

    @Test
    fun `asks for nothing and shows no bar while TDLib says the chat holds none`() =
        runViewModelTest {
            scheduledMessages.setScheduled(PRIVATE_CHAT, scheduledAt(11, TODAY_EVENING))

            val viewModel = showing(chat(), history(message(1)))

            assertNull(viewModel.state.scheduledBar)
            assertEquals(emptyList<Long>(), scheduledMessages.collected)
        }

    @Test
    fun `the bar follows the messages Telegram sends, and leaves with the last`() =
        runViewModelTest {
            scheduledMessages.setScheduled(
                PRIVATE_CHAT,
                scheduledAt(11, TODAY_EVENING),
                scheduledAt(
                    12,
                    TODAY_EVENING + 60,
                ),
            )
            val viewModel = showing(withScheduled, history(message(1)))
            assertEquals(2, viewModel.state.scheduledBar?.count)

            // Telegram sent 11 at its time: it leaves the scheduled messages and joins the history.
            scheduledMessages.setScheduled(PRIVATE_CHAT, scheduledAt(12, TODAY_EVENING + 60))
            messages.setHistory(PRIVATE_CHAT, history(message(1), outgoing(2)))
            runCurrent()
            assertEquals(1, viewModel.state.scheduledBar?.count)
            assertEquals(listOf(2L, 1L), viewModel.bubbleIds())

            scheduledMessages.setScheduled(PRIVATE_CHAT)
            chats.setChats(chat())
            runCurrent()
            assertNull(viewModel.state.scheduledBar)
        }

    @Test
    fun `a failed load shows no bar`() =
        runViewModelTest {
            scheduledMessages.loadError = Outcome.Failure(DomainError.Network)

            val viewModel = showing(withScheduled, history(message(1)))

            assertNull(viewModel.state.scheduledBar)
        }

    @Test
    fun `a forum topic's conversation has no bar`() =
        runViewModelTest {
            scheduledMessages.setScheduled(PRIVATE_CHAT, scheduledAt(11, TODAY_EVENING))
            chats.setChats(withScheduled)
            messages.setHistory(PRIVATE_CHAT, history(message(1)))

            val viewModel = viewModel(topic = MessageTopic.Forum(3))

            assertNull(viewModel.state.scheduledBar)
        }

    private fun ChatViewModel.bubbleIds() =
        (state.content as ChatContent.Content).items.filterIsInstance<ChatItem.Bubble>().map { it.id }

    private companion object {
        /** 6:30 PM on NOW's day, UTC. */
        val TODAY_EVENING = NOW.epochSecond - NOW.epochSecond % 86_400 + 18 * 3_600 + 30 * 60
    }
}
