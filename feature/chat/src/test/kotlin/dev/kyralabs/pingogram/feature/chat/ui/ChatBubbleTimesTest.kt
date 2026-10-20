package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.outgoing
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * What the bubbles of [ChatViewModel] say about time and sending: the time on the last bubble of a group and on a
 * message alone, or where it was tapped to show; messages being sent and failed ones in every chat; and which failed
 * messages can be sent again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatBubbleTimesTest : ChatViewModelHarness() {
    @Test
    fun `channels and Saved Messages still show messages being sent and failed ones`() =
        runTest {
            val failed = SendingState.Failed(DomainError.Network, canRetry = true, retryAfter = null)
            val channel =
                showing(
                    channelChat,
                    history(
                        outgoing(1, chatId = channelChat.id, sendingState = failed),
                        outgoing(2, chatId = channelChat.id, sendingState = SendingState.Pending),
                    ),
                )
            assertEquals(listOf(OutgoingStatus.Pending, OutgoingStatus.Failed), channel.bubbles.map { it.status })

            val saved = showing(chat(id = ME), history(outgoing(3, chatId = ME, sendingState = failed)))

            assertEquals(OutgoingStatus.Failed, saved.bubble(3).status)
        }

    @Test
    fun `a failed message offers to send it again where TDLib can`() =
        runTest {
            val retryable = SendingState.Failed(DomainError.Network, canRetry = true, retryAfter = null)
            val final = SendingState.Failed(DomainError.Unknown(400, "MESSAGE_EMPTY"), canRetry = false, null)
            val viewModel =
                showing(
                    chat(),
                    history(
                        outgoing(1, sendingState = retryable),
                        outgoing(2, date = NOW.plusSeconds(1), sendingState = final),
                        outgoing(3, date = NOW.plusSeconds(2)),
                    ),
                )

            assertEquals(listOf(false, false, true), viewModel.bubbles.map { it.canResend })
        }

    // Times

    @Test
    fun `the time shows on the last bubble of a group and on a message alone`() =
        runTest {
            val viewModel =
                showing(
                    chat(),
                    history(
                        message(1, date = minutesAgo(30)),
                        message(2, date = minutesAgo(10)),
                        message(3, date = minutesAgo(9)),
                        message(4, date = minutesAgo(8)),
                    ),
                )

            // Newest first: the last of the group, its middle and first, then the message alone.
            assertEquals(listOf(true, false, false, true), viewModel.bubbles.map { it.showsTime })
        }

    @Test
    fun `emoji-only messages show the time per group too`() =
        runTest {
            val party = MessageContent.Text(text("🎉"), null)
            val viewModel =
                showing(
                    chat(),
                    history(message(1, date = minutesAgo(2), content = party), message(2, date = minutesAgo(1))),
                )

            assertEquals(listOf(true, false), viewModel.bubbles.map { it.showsTime })
            assertTrue(viewModel.bubble(1).isEmojiOnly)
        }

    @Test
    fun `tapping a bubble shows its time, and tapping it again hides it`() =
        runTest {
            val viewModel = showing(chat(), history(message(1, date = minutesAgo(2)), message(2, date = minutesAgo(1))))

            send(viewModel, ChatEvent.ToggleTime(1))
            assertEquals(listOf(true, true), viewModel.bubbles.map { it.showsTime })

            send(viewModel, ChatEvent.ToggleTime(1))
            assertEquals(listOf(true, false), viewModel.bubbles.map { it.showsTime })
        }

    @Test
    fun `tapping a bubble maps only that bubble again`() =
        runTest {
            val viewModel =
                showing(
                    chat(),
                    history(
                        message(1, date = minutesAgo(3)),
                        message(2, date = minutesAgo(2)),
                        message(3, date = minutesAgo(1)),
                    ),
                )
            val before = viewModel.bubbles

            send(viewModel, ChatEvent.ToggleTime(2))

            val after = viewModel.bubbles
            assertSame(before[0], after[0])
            assertSame(before[2], after[2])
            assertTrue(after[1].showsTime)
        }

    private fun minutesAgo(minutes: Long): Instant = NOW.minus(Duration.ofMinutes(minutes))
}
