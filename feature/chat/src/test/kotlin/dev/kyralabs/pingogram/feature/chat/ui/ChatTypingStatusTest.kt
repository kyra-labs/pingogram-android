package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import dev.kyralabs.pingogram.core.domain.model.ChatAction
import dev.kyralabs.pingogram.core.domain.model.Document
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FileUpload
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * What [ChatViewModel] tells the chat the user is doing (`REQUIREMENTS.md` FR-X16): typing, recording a voice or round
 * video message, uploading a photo or a file; sent again at most every five seconds while it lasts, ended when it
 * stops, and never anything while "Send typing status" is off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatTypingStatusTest : ChatViewModelHarness() {
    private fun TestScope.privateChat(
        video: Boolean = false,
        topic: MessageTopic? = null,
    ): ChatViewModel {
        composerSettings.video.value = video
        chats.setChats(chat())
        messages.setHistory(ADA, history(message(1)))
        return viewModel(topic = topic)
    }

    /** Types [text] as the user does, at the cursor. */
    private fun TestScope.type(
        viewModel: ChatViewModel,
        text: String,
    ) {
        viewModel.composerText.insert(text)
        settle()
    }

    private fun TestScope.settle() {
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private fun TestScope.wait(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private val sentActions get() = actions.sent.map { it.third }

    @Test
    fun `typing tells the chat once, again after five seconds, and sending ends it`() =
        runViewModelTest {
            val viewModel = privateChat()

            type(viewModel, "H")
            wait(1_000)
            type(viewModel, "i")
            assertEquals(listOf(ChatAction.Typing), sentActions)

            wait(ChatActionSender.RESEND_MILLIS)
            type(viewModel, "!")
            send(viewModel, ChatEvent.Send())
            settle()

            assertEquals(listOf(ChatAction.Typing, ChatAction.Typing, null), sentActions)
            assertEquals(ADA, actions.sent.first().first)
        }

    @Test
    fun `clearing what was typed ends it`() =
        runViewModelTest {
            val viewModel = privateChat()
            type(viewModel, "H")

            viewModel.composerText.state.edit { selection = TextRange(0, length) }
            viewModel.composerText.deleteBackward()
            settle()

            assertEquals(listOf(ChatAction.Typing, null), sentActions)
        }

    @Test
    fun `with Send typing status off nothing is sent, not even the end`() =
        runViewModelTest {
            val viewModel = privateChat()
            type(viewModel, "H")
            typingStatus.setEnabled(false)
            runCurrent()

            send(viewModel, ChatEvent.Send())
            settle()
            type(viewModel, "Hi")
            wait(ChatActionSender.RESEND_MILLIS)
            type(viewModel, "!")
            send(viewModel, VoiceEvent.Start())
            wait(ChatActionSender.RESEND_MILLIS)

            assertEquals(listOf(ChatAction.Typing), sentActions)
        }

    @Test
    fun `a text put in by the app or an edit of a sent message is no typing`() =
        runViewModelTest {
            val viewModel = privateChat()

            viewModel.composerText.setContent(FormattedText("Draft", persistentListOf()))
            settle()
            send(viewModel, ChatEvent.Edit(1))
            type(viewModel, "!")

            assertEquals(emptyList<ChatAction?>(), sentActions)
        }

    @Test
    fun `recording a voice message tells the chat every five seconds, and sending it ends it`() =
        runViewModelTest {
            val viewModel = privateChat()

            send(viewModel, VoiceEvent.Start())
            wait(ChatActionSender.RESEND_MILLIS - 100)
            assertEquals(listOf(ChatAction.RecordingVoiceNote), sentActions)

            wait(200)
            send(viewModel, VoiceEvent.Send)

            assertEquals(listOf(ChatAction.RecordingVoiceNote, ChatAction.RecordingVoiceNote, null), sentActions)
        }

    @Test
    fun `recording a round video message tells the chat, in the conversation's topic`() =
        runViewModelTest {
            val viewModel = privateChat(video = true, topic = MessageTopic.Forum(7))

            send(viewModel, VoiceEvent.Start())

            assertEquals(listOf(Triple(ADA, MessageTopic.Forum(7), ChatAction.RecordingVideoNote)), actions.sent)
        }

    @Test
    fun `a photo being sent tells the chat its upload, which ends once it is sent`() =
        runViewModelTest {
            chats.setChats(chat())
            messages.setHistory(ADA, history(pending(-1, photo)))
            viewModel()

            files.uploads.value = mapOf(UPLOAD_FILE to FileUpload(500, 2_000, isActive = true, isCompleted = false))
            runCurrent()
            messages.setHistory(ADA, history(message(2, isOutgoing = true, content = photo)))
            runCurrent()

            assertEquals(listOf(ChatAction.UploadingPhoto(0), null), sentActions)
        }

    @Test
    fun `a file being sent tells the chat its upload, resent with its progress five seconds on`() =
        runViewModelTest {
            chats.setChats(chat())
            messages.setHistory(ADA, history(pending(-1, document)))
            viewModel()

            files.uploads.value = mapOf(UPLOAD_FILE to FileUpload(500, 2_000, isActive = true, isCompleted = false))
            runCurrent()
            wait(ChatActionSender.RESEND_MILLIS)

            assertEquals(listOf(ChatAction.UploadingDocument(0), ChatAction.UploadingDocument(25)), sentActions)
        }

    private fun pending(
        id: Long,
        content: MessageContent,
    ) = message(id, isOutgoing = true, content = content, sendingState = SendingState.Pending)

    private companion object {
        const val UPLOAD_FILE = 77

        val photo =
            MessageContent.Photo(
                photo =
                    Photo(
                        sizes = persistentListOf(PhotoSize("i", 800, 600, FileRef(id = UPLOAD_FILE, size = 2_000))),
                        minithumbnail = null,
                    ),
                caption = FormattedText.Empty,
                hasSpoiler = false,
                isSecret = false,
                showCaptionAboveMedia = false,
            )

        val document =
            MessageContent.Document(
                Document(
                    fileName = "Report 2026.pdf",
                    mimeType = "application/pdf",
                    minithumbnail = null,
                    thumbnail = null,
                    file = FileRef(UPLOAD_FILE, 2_000),
                ),
                caption = FormattedText.Empty,
            )
    }
}
