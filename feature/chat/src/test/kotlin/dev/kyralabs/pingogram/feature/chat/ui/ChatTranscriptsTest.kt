package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Transcript
import dev.kyralabs.pingogram.core.domain.model.TranscriptionResult
import dev.kyralabs.pingogram.core.domain.model.TranscriptionSettings
import dev.kyralabs.pingogram.core.domain.model.VoiceNote
import dev.kyralabs.pingogram.core.domain.model.Waveform
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.transcription.BubbleTranscript
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
 * Voice message transcription in [ChatViewModel] (`ChatTranscripts`, the owner's board 28): on the device, on a tap or
 * automatically for others' messages, "On this phone" on the first transcript only, the microphone permission Android's
 * recognizer needs, a language not on the device, failures, and what survives process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatTranscriptsTest : ChatViewModelHarness() {
    private fun voice(
        id: Long,
        isOutgoing: Boolean = false,
    ): Message =
        message(
            id,
            isOutgoing = isOutgoing,
            content =
                MessageContent.VoiceNote(
                    VoiceNote(duration = 12, waveform = Waveform(ByteArray(0)), file = FileRef(fileOf(id), size = 1)),
                    caption = ChatFixtures.text(""),
                    isListened = true,
                ),
        )

    private fun fileOf(messageId: Long) = (FILE_BASE + messageId).toInt()

    private fun TestScope.conversation(
        vararg messages: Message = arrayOf(voice(1), voice(2), voice(3, isOutgoing = true)),
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        messages.forEach { files.setFile(fileOf(it.id), FileState.Downloaded("/files/voice${it.id}.ogg", size = 1)) }
        chats.setChats(chat())
        this@ChatTranscriptsTest.messages.setHistory(PRIVATE_CHAT, history(*messages))
        return viewModel(PRIVATE_CHAT, handle)
    }

    @Test
    fun `the device's recognizer shows the button, and nothing is transcribed before a tap`() =
        runViewModelTest {
            val viewModel = conversation()

            assertTrue(viewModel.state.canTranscribe)
            assertNull(viewModel.bubble(1).transcript)
            assertTrue(transcriber.transcribed.isEmpty())
        }

    @Test
    fun `a device without an on-device recognizer shows no button`() =
        runViewModelTest {
            transcriber.isAvailable = false
            val viewModel = conversation()

            assertFalse(viewModel.state.canTranscribe)
        }

    @Test
    fun `Transcribe shows the text, the first one saying On this phone, and keeps it on the device`() =
        runViewModelTest {
            val viewModel = conversation()

            send(viewModel, TranscriptionEvent.Transcribe(1))

            assertEquals(listOf("/files/voice1.ogg"), transcriber.transcribed)
            assertEquals(
                BubbleTranscript.Shown(TEXT, isAutomatic = false, showsNotice = true),
                viewModel.bubble(1).transcript,
            )
            assertEquals(Transcript(TEXT, isFirst = true), transcripts.saved.value[PRIVATE_CHAT]?.get(1))
            assertTrue(transcriptionSettings.settings.value.noticeShown)

            send(viewModel, TranscriptionEvent.Transcribe(2))

            assertEquals(BubbleTranscript.Shown(TEXT, showsNotice = false), viewModel.bubble(2).transcript)
        }

    @Test
    fun `the bubble says it is transcribing while the recognizer works`() =
        runViewModelTest {
            val gate = CompletableDeferred<Unit>()
            transcriber.gate = gate
            val viewModel = conversation()

            send(viewModel, TranscriptionEvent.Transcribe(1))
            assertEquals(BubbleTranscript.Transcribing, viewModel.bubble(1).transcript)

            gate.complete(Unit)
            runCurrent()
            assertTrue(viewModel.bubble(1).transcript is BubbleTranscript.Shown)
        }

    @Test
    fun `Hide text hides it, and Transcribe shows the kept text without asking the recognizer again`() =
        runViewModelTest {
            val viewModel = conversation()
            send(viewModel, TranscriptionEvent.Transcribe(1))

            send(viewModel, TranscriptionEvent.Hide(1))
            assertNull(viewModel.bubble(1).transcript)

            send(viewModel, TranscriptionEvent.Transcribe(1))
            assertTrue(viewModel.bubble(1).transcript is BubbleTranscript.Shown)
            assertEquals(1, transcriber.transcribed.size)
        }

    @Test
    fun `Show more shows a long transcript whole`() =
        runViewModelTest {
            val viewModel = conversation()
            send(viewModel, TranscriptionEvent.Transcribe(1), TranscriptionEvent.ShowMore(1))

            assertEquals(true, (viewModel.bubble(1).transcript as BubbleTranscript.Shown).isExpanded)
        }

    @Test
    fun `a language the device has no speech language for links to Android's settings`() =
        runViewModelTest {
            transcriber.result = TranscriptionResult.LanguageUnavailable
            val viewModel = conversation()

            send(viewModel, TranscriptionEvent.Transcribe(1))
            assertEquals(BubbleTranscript.LanguageUnavailable, viewModel.bubble(1).transcript)

            viewModel.effects.test {
                send(viewModel, TranscriptionEvent.OpenLanguageSettings)
                assertEquals(TranscriptionEffect.OpenLanguageSettings, awaitItem())
            }
        }

    @Test
    fun `a failure offers Try again, which transcribes again`() =
        runViewModelTest {
            transcriber.result = TranscriptionResult.Failed
            val viewModel = conversation()

            send(viewModel, TranscriptionEvent.Transcribe(1))
            assertEquals(BubbleTranscript.Failed, viewModel.bubble(1).transcript)

            transcriber.result = TranscriptionResult.Recognized(TEXT)
            send(viewModel, TranscriptionEvent.Transcribe(1))
            assertTrue(viewModel.bubble(1).transcript is BubbleTranscript.Shown)
        }

    @Test
    fun `without the microphone permission it explains, and transcribes once the permission is granted`() =
        runViewModelTest {
            transcriber.result = TranscriptionResult.NeedsMicrophonePermission
            val viewModel = conversation()

            viewModel.effects.test {
                send(viewModel, TranscriptionEvent.Transcribe(1))
                assertEquals(TranscriptionEffect.ExplainMicrophone, awaitItem())
                assertNull(viewModel.bubble(1).transcript)

                transcriber.result = TranscriptionResult.Recognized(TEXT)
                send(viewModel, TranscriptionEvent.PermissionAnswered(granted = true))
                assertTrue(viewModel.bubble(1).transcript is BubbleTranscript.Shown)
            }
        }

    @Test
    fun `a refused permission offers the settings`() =
        runViewModelTest {
            transcriber.result = TranscriptionResult.NeedsMicrophonePermission
            val viewModel = conversation()

            viewModel.effects.test {
                send(viewModel, TranscriptionEvent.Transcribe(1))
                assertEquals(TranscriptionEffect.ExplainMicrophone, awaitItem())
                send(viewModel, TranscriptionEvent.PermissionAnswered(granted = false))
                assertEquals(TranscriptionEffect.MicrophoneDenied, awaitItem())
            }
        }

    @Test
    fun `automatic transcription takes others' messages on screen only, once each`() =
        runViewModelTest {
            transcriptionSettings.settings.value = TranscriptionSettings(automatic = true)
            val viewModel = conversation()

            send(viewModel, ChatEvent.MessagesVisible(setOf(1, 3)))
            send(viewModel, ChatEvent.MessagesVisible(setOf(1, 3)))

            assertEquals(listOf("/files/voice1.ogg"), transcriber.transcribed)
            assertEquals(true, (viewModel.bubble(1).transcript as BubbleTranscript.Shown).isAutomatic)
            assertNull(viewModel.bubble(3).transcript)
        }

    @Test
    fun `automatic transcription never asks for the permission`() =
        runViewModelTest {
            transcriptionSettings.settings.value = TranscriptionSettings(automatic = true)
            transcriber.result = TranscriptionResult.NeedsMicrophonePermission
            val viewModel = conversation()

            viewModel.effects.test {
                send(viewModel, ChatEvent.MessagesVisible(setOf(1)))
                expectNoEvents()
            }
            assertNull(viewModel.bubble(1).transcript)
        }

    @Test
    fun `an automatic transcript hidden stays hidden`() =
        runViewModelTest {
            transcriptionSettings.settings.value = TranscriptionSettings(automatic = true)
            val viewModel = conversation()
            send(viewModel, ChatEvent.MessagesVisible(setOf(1)), TranscriptionEvent.Hide(1))

            assertNull(viewModel.bubble(1).transcript)
        }

    @Test
    fun `the transcripts shown survive process death`() =
        runViewModelTest {
            val handle = SavedStateHandle()
            conversation(handle = handle).also { send(it, TranscriptionEvent.Transcribe(1)) }

            val restored = conversation(handle = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))

            assertTrue(restored.bubble(1).transcript is BubbleTranscript.Shown)
            assertEquals(1, transcriber.transcribed.size)
        }

    private companion object {
        const val TEXT = "Hello from the voice message"
        const val FILE_BASE = 500L
    }
}
