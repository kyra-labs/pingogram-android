package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.ChatDraft
import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.SharedContent
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.attach.PreviewItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What is shared into a chat (`REQUIREMENTS.md` FR-N2, direct share) through `ShareInbox`: once the conversation is on
 * screen, a text joins the composer's field, after the chat's draft, and files join the attach preview with the text as
 * their caption. Nothing is sent, and only one conversation takes a share.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSharesTest : ChatViewModelHarness() {
    private fun text(text: String) = FormattedText(text, persistentListOf())

    private fun TestScope.privateChat(draft: String? = null): ChatViewModel {
        val chatDraft = draft?.let { ChatDraft(DraftContent.Text(text(it)), date = 0) }
        chats.setChats(chat(draft = chatDraft))
        messages.setHistory(ADA, history(message(1)))
        return viewModel()
    }

    @Test
    fun `a shared text waits for the screen, then fills the composer, unsent`() =
        runViewModelTest {
            val viewModel = privateChat()
            val bold = TextEntity(0, 4, TextEntityType.Bold)
            shareInbox.deliver(
                PRIVATE_CHAT,
                SharedContent(FormattedText("Look https://example.com", persistentListOf(bold))),
            )
            runCurrent()
            assertEquals("", viewModel.composerText.content.text)

            send(viewModel, ChatEvent.Started)

            assertEquals("Look https://example.com", viewModel.composerText.content.text)
            assertEquals(listOf(bold), viewModel.composerText.content.entities)
            assertTrue(messages.sent.isEmpty())
        }

    @Test
    fun `a shared text follows the chat's draft on a line of its own`() =
        runViewModelTest {
            val viewModel = privateChat(draft = "Hi")
            send(viewModel, ChatEvent.Started)

            shareInbox.deliver(PRIVATE_CHAT, SharedContent(text("https://example.com")))
            runCurrent()

            assertEquals("Hi\nhttps://example.com", viewModel.composerText.content.text)
        }

    @Test
    fun `shared files join the attach preview with the text as caption`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, ChatEvent.Started)

            shareInbox.deliver(
                PRIVATE_CHAT,
                SharedContent(text("Holidays"), persistentListOf("content://a", "content://b"), AttachmentKind.Media),
            )
            runCurrent()

            assertEquals(
                listOf("content://a" to AttachmentKind.Media, "content://b" to AttachmentKind.Media),
                attachments.prepared,
            )
            assertEquals(
                2,
                viewModel.state.attachPreview
                    ?.items
                    ?.count { it is PreviewItem.Ready },
            )
            assertEquals("Holidays", viewModel.attachCaption.content.text)
            assertEquals("", viewModel.composerText.content.text)
            assertTrue(attachments.sent.isEmpty())
        }

    @Test
    fun `a share goes to its own chat only, once`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, ChatEvent.Started)

            shareInbox.deliver(PRIVATE_CHAT + 1, SharedContent(text("Elsewhere")))
            runCurrent()
            assertEquals("", viewModel.composerText.content.text)

            shareInbox.deliver(PRIVATE_CHAT, SharedContent(text("Here")))
            runCurrent()
            send(viewModel, ChatEvent.Stopped, ChatEvent.Started)

            assertEquals("Here", viewModel.composerText.content.text)
            assertNull(viewModel.state.attachPreview)
        }
}
