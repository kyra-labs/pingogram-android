package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.ChatAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [ChatActionSender] when the conversation closes ([ChatActionSender.leave]): what the user was doing ends at once, in
 * the app's scope since the view model's is gone, once, and nothing is sent after; nothing at all with the switch off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatActionSenderTest {
    private val enabled = MutableStateFlow(true)
    private val sent = mutableListOf<ChatAction?>()
    private val writing = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private val ongoing = MutableStateFlow<ChatAction?>(null)

    /** A sender started in a scope of its own, the view model's, which the test cancels as clearing it does. */
    private fun TestScope.started(): Pair<ChatActionSender, CoroutineScope> {
        val own = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val sender = ChatActionSender(enabled, own, { testScheduler.currentTime }) { sent += it }
        sender.start(writing, ongoing)
        runCurrent()
        return sender to own
    }

    @Test
    fun `leaving during an upload ends it in the app's scope once, and sends nothing after`() =
        runTest {
            val (sender, own) = started()
            ongoing.value = ChatAction.UploadingVideo(10)
            runCurrent()

            own.cancel()
            sender.leave(backgroundScope)
            sender.leave(backgroundScope)
            ongoing.value = ChatAction.UploadingVideo(60)
            advanceTimeBy(ChatActionSender.RESEND_MILLIS * 2)
            runCurrent()

            assertEquals(listOf(ChatAction.UploadingVideo(10), null), sent)
        }

    @Test
    fun `leaving while typing ends it`() =
        runTest {
            val (sender, own) = started()
            writing.tryEmit(true)
            runCurrent()

            own.cancel()
            sender.leave(backgroundScope)
            runCurrent()

            assertEquals(listOf(ChatAction.Typing, null), sent)
        }

    @Test
    fun `leaving with nothing going on sends nothing`() =
        runTest {
            val (sender, own) = started()

            own.cancel()
            sender.leave(backgroundScope)
            runCurrent()

            assertEquals(emptyList<ChatAction?>(), sent)
        }

    @Test
    fun `with the switch off, leaving sends nothing, not even the end`() =
        runTest {
            val (sender, own) = started()
            writing.tryEmit(true)
            runCurrent()
            enabled.value = false

            own.cancel()
            sender.leave(backgroundScope)
            runCurrent()

            assertEquals(listOf<ChatAction?>(ChatAction.Typing), sent)
        }
}
