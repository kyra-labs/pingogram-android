package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.replyTo
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The replied messages that are not loaded ([ChatViewModel], `docs/PERFORMANCE.md`): those of one page are asked for
 * together, and their answers change the conversation once, not once each.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepliedMessagesTest : ChatViewModelHarness() {
    @Test
    fun `the replied messages of one page are asked for together and show in one change`() =
        runTest {
            val answers = (1L..3L).associateWith { CompletableDeferred<Outcome<Message>>() }
            messages.onRepliedMessage = { _, replying -> answers.getValue(replying - 10).await() }
            val viewModel =
                showing(
                    chat(),
                    history(
                        message(11, replyTo = replyTo(1)),
                        message(12, replyTo = replyTo(2)),
                        message(13, replyTo = replyTo(3)),
                        reachedStart = false,
                    ),
                )
            assertEquals(listOf(11L, 12L, 13L), messages.repliedRequests.map { it.second }.sorted())

            answers.getValue(1).complete(answer(1, "One"))
            answers.getValue(2).complete(answer(2, "Two"))
            runCurrent()
            // Still loading: the page's answers come in one change, once all of them are there.
            assertEquals(res(R.string.chat_reply_loading), viewModel.bubble(11).reply?.text)

            answers.getValue(3).complete(answer(3, "Three"))
            runCurrent()

            assertEquals(
                listOf(UiText.Plain("One"), UiText.Plain("Two"), UiText.Plain("Three")),
                listOf(11L, 12L, 13L).map { viewModel.bubble(it).reply?.text },
            )
        }

    private fun answer(
        id: Long,
        said: String,
    ): Outcome<Message> = Outcome.Success(message(id, content = MessageContent.Text(text(said), null)))
}
