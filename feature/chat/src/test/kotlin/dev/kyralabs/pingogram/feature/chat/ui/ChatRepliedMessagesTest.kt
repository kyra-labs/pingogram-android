package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The replied messages that are not loaded ([ChatViewModel], `docs/PERFORMANCE.md`): those of one page are asked for
 * together, and each shows as soon as its own answer comes, with any others that came meanwhile in the same change;
 * one that never answers (offline, TDLib waits for the server) holds back none of the others.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepliedMessagesTest : ChatViewModelHarness() {
    private val answers = (1L..3L).associateWith { CompletableDeferred<Outcome<Message>>() }

    private fun TestScope.showingThreeReplies(): ChatViewModel {
        messages.onRepliedMessage = { _, replying -> answers.getValue(replying - 10).await() }
        return showing(
            chat(),
            history(
                message(11, replyTo = replyTo(1)),
                message(12, replyTo = replyTo(2)),
                message(13, replyTo = replyTo(3)),
                reachedStart = false,
            ),
        )
    }

    @Test
    fun `the replied messages of one page are asked for together`() =
        runViewModelTest {
            showingThreeReplies()

            assertEquals(listOf(11L, 12L, 13L), messages.repliedRequests.map { it.second }.sorted())
        }

    @Test
    fun `a reply that never answers holds back none of the others`() =
        runViewModelTest {
            val viewModel = showingThreeReplies()

            answers.getValue(1).complete(answer(1, "One"))
            answers.getValue(2).complete(answer(2, "Two"))
            runCurrent()

            // The third is never answered, as one TDLib must ask the server for while offline.
            assertEquals(
                listOf(UiText.Plain("One"), UiText.Plain("Two"), res(R.string.chat_reply_loading)),
                listOf(11L, 12L, 13L).map { viewModel.bubble(it).reply?.text },
            )
        }

    @Test
    fun `each reply shows as soon as its own answer comes`() =
        runViewModelTest {
            val viewModel = showingThreeReplies()

            answers.getValue(2).complete(answer(2, "Two"))
            runCurrent()
            assertEquals(UiText.Plain("Two"), viewModel.bubble(12).reply?.text)

            answers.getValue(1).complete(answer(1, "One"))
            runCurrent()
            assertEquals(UiText.Plain("One"), viewModel.bubble(11).reply?.text)
            assertEquals(res(R.string.chat_reply_loading), viewModel.bubble(13).reply?.text)
        }

    @Test
    fun `answers that come together show together, in one new state`() =
        runViewModelTest {
            val viewModel = showingThreeReplies()

            viewModel.uiState.test {
                expectMostRecentItem()

                answers.getValue(1).complete(answer(1, "One"))
                answers.getValue(3).complete(answer(3, "Three"))
                runCurrent()

                val items = (awaitItem().content as ChatContent.Content).items.filterIsInstance<ChatItem.Bubble>()
                assertEquals(
                    listOf(UiText.Plain("One"), UiText.Plain("Three")),
                    listOf(11L, 13L).map { id -> items.single { it.id == id }.reply?.text },
                )
                expectNoEvents()
            }
        }

    private fun answer(
        id: Long,
        said: String,
    ): Outcome<Message> = Outcome.Success(message(id, content = MessageContent.Text(text(said), null)))
}
