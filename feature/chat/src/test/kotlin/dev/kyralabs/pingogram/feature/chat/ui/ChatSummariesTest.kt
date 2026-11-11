package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.FakeTranslationRepository.Companion.summaryOf
import dev.kyralabs.pingogram.feature.chat.ui.translation.BubbleTranslation
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
 * Telegram's summaries of long messages in [ChatViewModel] (`ChatSummaries`, td_api.tl `summarizeMessage`): "Summarize"
 * only where Telegram can summarize, the summary in the bubble and "Show full text", the language asked for, an edit,
 * Telegram's refusals and what survives process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSummariesTest : ChatViewModelHarness() {
    private val post = "A long post about the release, with every change listed one by one."

    /** Message 1, which Telegram can summarize in [language]; message 2, which it cannot. */
    private fun conversation(language: String = "en"): Array<Message> =
        arrayOf(
            message(1, content = textContent(post)).copy(summaryLanguage = language),
            message(2, content = textContent("Short")),
        )

    private fun textContent(text: String) = MessageContent.Text(text(text), linkPreview = null)

    private fun TestScope.summarizing(
        language: String = "en",
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        chats.setChats(chat())
        messages.setHistory(PRIVATE_CHAT, history(*conversation(language)))
        return viewModel(PRIVATE_CHAT, handle)
    }

    private fun TestScope.actionsOf(
        viewModel: ChatViewModel,
        messageId: Long,
    ): List<MessageAction> {
        send(viewModel, ChatEvent.ShowMessageActions(messageId))
        val actions = checkNotNull(viewModel.state.messageActions).actions
        send(viewModel, ChatEvent.DismissMessageActions)
        return actions
    }

    @Test
    fun `offers Summarize only for a message Telegram can summarize, and asks nothing before the user chooses it`() =
        runViewModelTest {
            val viewModel = summarizing()

            assertTrue(MessageAction.Summarize in actionsOf(viewModel, 1))
            assertFalse(MessageAction.Summarize in actionsOf(viewModel, 2))
            assertTrue(translations.summaryRequests.isEmpty())
        }

    @Test
    fun `Summarize shows Telegram's summary in the bubble, then Show full text shows the post again`() =
        runViewModelTest {
            val viewModel = summarizing()

            send(viewModel, SummaryEvent.Summarize(1))

            val bubble = viewModel.bubble(1)
            assertEquals(BubbleTranslation.Summary(summaryOf(1)), bubble.translation)
            assertEquals(summaryOf(1), bubble.text)
            assertTrue(bubble.showsTime)
            assertEquals(listOf(1L to null), translations.summaryRequests)
            val actions = actionsOf(viewModel, 1)
            assertTrue(MessageAction.ShowFullText in actions)
            assertFalse(MessageAction.Summarize in actions || MessageAction.Translate in actions)

            send(viewModel, SummaryEvent.ShowFullText(1))
            assertNull(viewModel.bubble(1).translation)
            assertEquals(text(post), viewModel.bubble(1).text)

            // Shown again from memory, without asking Telegram again.
            send(viewModel, SummaryEvent.Summarize(1))
            assertEquals(BubbleTranslation.Summary(summaryOf(1)), viewModel.bubble(1).translation)
            assertEquals(1, translations.summaryRequests.size)
        }

    @Test
    fun `while Telegram summarizes, the bubble keeps its text and says so`() =
        runViewModelTest {
            val answer = CompletableDeferred<Outcome<FormattedText>>()
            translations.onSummarize = { _, _, _ -> answer.await() }
            val viewModel = summarizing()

            send(viewModel, SummaryEvent.Summarize(1))
            assertEquals(BubbleTranslation.Summarizing, viewModel.bubble(1).translation)
            assertEquals(text(post), viewModel.bubble(1).text)

            answer.complete(Outcome.Success(summaryOf(1)))
            runCurrent()
            assertEquals(BubbleTranslation.Summary(summaryOf(1)), viewModel.bubble(1).translation)
        }

    @Test
    fun `a post in a language the user does not read is summarized into theirs`() =
        runViewModelTest {
            val viewModel = summarizing(language = "es")

            send(viewModel, SummaryEvent.Summarize(1))

            assertEquals(listOf(1L to "en"), translations.summaryRequests)
        }

    @Test
    fun `a refusal is a snackbar, and the bubble shows its text`() =
        runViewModelTest {
            translations.onSummarize = { _, _, _ -> Outcome.Failure(DomainError.FloodWait(12)) }
            val viewModel = summarizing()

            viewModel.effects.test {
                send(viewModel, SummaryEvent.Summarize(1))

                assertEquals(TranslationEffect.SummaryFailed(DomainError.FloodWait(12)), awaitItem())
            }
            assertNull(viewModel.bubble(1).translation)
            assertTrue(MessageAction.Summarize in actionsOf(viewModel, 1))
        }

    @Test
    fun `an edited post is summarized again`() =
        runViewModelTest {
            val viewModel = summarizing()
            send(viewModel, SummaryEvent.Summarize(1))

            val edited = message(1, content = textContent("$post Updated.")).copy(summaryLanguage = "en")
            messages.setHistory(PRIVATE_CHAT, history(edited, *conversation().drop(1).toTypedArray()))
            runCurrent()

            assertEquals(2, translations.summaryRequests.size)
            assertEquals(BubbleTranslation.Summary(summaryOf(1)), viewModel.bubble(1).translation)
        }

    @Test
    fun `after process death the summarized posts are summarized again`() =
        runViewModelTest {
            val first = summarizing()
            send(first, SummaryEvent.Summarize(1))
            clear(first)
            translations.summaryRequests.clear()

            val restored = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) })
            val viewModel = summarizing(handle = restored)
            runCurrent()

            assertEquals(listOf(1L to null), translations.summaryRequests)
            assertEquals(BubbleTranslation.Summary(summaryOf(1)), viewModel.bubble(1).translation)
        }
}
