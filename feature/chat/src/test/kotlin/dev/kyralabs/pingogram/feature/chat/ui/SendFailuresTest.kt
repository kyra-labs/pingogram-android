package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.outgoing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A message being sent that fails ([SendFailures]) gives the screen a `Reject` haptic ([ChatEffect.SendFailed]), only
 * while it is shown; a failed message that loads, or was there already, gives none.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendFailuresTest : ChatViewModelHarness() {
    private val failed = SendingState.Failed(DomainError.Network, canRetry = true, retryAfter = null)

    @Test
    fun `a message being sent that fails is a failure`() {
        val failures = SendFailures()

        assertFalse(failures.failedSince(history(message(1), outgoing(2, sendingState = SendingState.Pending))))
        // TDLib gives the failed message a new id.
        assertTrue(failures.failedSince(history(message(1), outgoing(3, sendingState = failed))))
    }

    @Test
    fun `failed messages already there, or loaded while nothing is sent, are no failure`() {
        val failures = SendFailures()

        assertFalse(failures.failedSince(history(outgoing(2, sendingState = failed))))
        assertFalse(
            failures.failedSince(history(outgoing(1, sendingState = failed), outgoing(2, sendingState = failed))),
        )
    }

    @Test
    fun `a message sent again that fails again is a failure`() {
        val failures = SendFailures()
        failures.failedSince(history(outgoing(2, sendingState = failed)))

        assertFalse(failures.failedSince(history(outgoing(3, sendingState = SendingState.Pending))))
        assertTrue(failures.failedSince(history(outgoing(4, sendingState = failed))))
    }

    @Test
    fun `a send that fails while the chat is shown is a reject haptic`() =
        runViewModelTest {
            val viewModel = showing(chat(), history(outgoing(1, sendingState = SendingState.Pending)))
            send(viewModel, ChatEvent.Started)

            viewModel.effects.test {
                messages.setHistory(PRIVATE_CHAT, history(outgoing(2, date = NOW, sendingState = failed)))
                runCurrent()

                assertEquals(ChatEffect.SendFailed, awaitItem())
            }
        }

    @Test
    fun `a send that fails while the screen is stopped is no haptic`() =
        runViewModelTest {
            val viewModel = showing(chat(), history(outgoing(1, sendingState = SendingState.Pending)))
            send(viewModel, ChatEvent.Started, ChatEvent.Stopped)

            viewModel.effects.test {
                messages.setHistory(PRIVATE_CHAT, history(outgoing(2, sendingState = failed)))
                runCurrent()

                expectNoEvents()
            }
        }
}
