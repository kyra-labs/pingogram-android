package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatDraft
import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.FakeVoiceNoteRepository.Companion.voiceNoteFile
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.content.decodeWaveform
import dev.kyralabs.pingogram.feature.chat.ui.voice.RecordingMeter
import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The voice recording of [ChatViewModel]'s composer (`REQUIREMENTS.md` FR-C5): idle → recording (held) → locked →
 * paused and resumed → sent or cancelled; the meter; too short recordings; failures; the screen stopping; clearing the
 * view model; and the microphone permission's snackbars.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRecorderTest : ChatViewModelHarness() {
    /** The private chat with Ada, replying to message 1 if [replyTo] is set (through the draft). */
    private fun TestScope.privateChat(replyTo: Long? = null): ChatViewModel {
        val draft = replyTo?.let { ChatDraft(DraftContent.Text(FormattedText.Empty), 0, it) }
        chats.setChats(chat(draft = draft))
        messages.setHistory(ADA, history(message(1)))
        return viewModel()
    }

    private val ChatViewModel.recording get() = voiceRecording.value

    /** Lets [millis] of recording pass, ticks included. */
    private fun TestScope.record(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `holding records into a new file in the recorder's format, held under the finger`() =
        runViewModelTest {
            val viewModel = privateChat()

            send(viewModel, VoiceEvent.Start())

            assertEquals(listOf("start:${voiceNoteFile("ogg")}:OggOpus"), recorder.calls)
            assertFalse(viewModel.recording!!.isLocked)
            assertFalse(viewModel.recording!!.isPaused)
        }

    @Test
    fun `without an Opus encoder it records AAC into an m4a file`() =
        runViewModelTest {
            recorder.format = VoiceFormat.Aac
            val viewModel = privateChat()

            send(viewModel, VoiceEvent.Start())

            assertEquals(listOf("start:${voiceNoteFile("m4a")}:Aac"), recorder.calls)
        }

    @Test
    fun `a recording started without touch is locked at once`() =
        runViewModelTest {
            val viewModel = privateChat()

            send(viewModel, VoiceEvent.Start(locked = true))

            assertTrue(viewModel.recording!!.isLocked)
        }

    @Test
    fun `the meter follows the recorder's level and how long it has recorded`() =
        runViewModelTest {
            recorder.levels = listOf(0.2f, 0.8f)
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start())

            record(1_000)

            // A tick at the start, then one every 50 ms.
            val meter = viewModel.recording!!.meter
            val levels = meter.recentLevels(RecordingMeter.RECENT_LEVELS)
            assertEquals(1_000L, meter.elapsedMillis)
            assertEquals(0.8f, meter.level)
            assertEquals(21, levels.size)
            assertEquals(listOf(0.2f, 0.8f), levels.take(2))
        }

    @Test
    fun `releasing sends the voice note with its length in seconds and its waveform`() =
        runViewModelTest {
            recorder.levels = listOf(0.1f, 0.4f, 0.8f)
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start())
            record(2_400)

            send(viewModel, VoiceEvent.Send)

            val (voiceNote, replyTo) = voiceNotes.sentVoiceNotes.single()
            assertEquals(voiceNoteFile("ogg"), voiceNote.path)
            assertEquals(2, voiceNote.duration)
            val levels = decodeWaveform(voiceNote.waveform.bytes)
            assertEquals(31, levels.max())
            assertEquals(4, levels.first())
            assertNull(replyTo)
            assertEquals("stop", recorder.calls.last())
            assertNull(viewModel.recording)
        }

    @Test
    fun `a voice note takes the composer's reply along, and gives it back when refused`() =
        runViewModelTest {
            voiceNotes.onSendVoiceNote = { Outcome.Failure(DomainError.SendingNotAllowed) }
            val viewModel = privateChat(replyTo = 1)
            send(viewModel, VoiceEvent.Start())
            record(1_500)

            viewModel.effects.test {
                send(viewModel, VoiceEvent.Send)

                assertEquals(ChatEffect.ShowError(DomainError.SendingNotAllowed), awaitItem())
            }
            assertEquals(1L, voiceNotes.sentVoiceNotes.single().second)
            assertEquals(1L, (viewModel.state.composer as ComposerUiState.Available).bar?.messageId)
        }

    @Test
    fun `a recording shorter than a second is thrown away, saying to hold`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start())
            record(600)

            viewModel.effects.test {
                send(viewModel, VoiceEvent.Send)

                assertEquals(VoiceEffect.HoldToRecord, awaitItem())
            }
            assertTrue(voiceNotes.sentVoiceNotes.isEmpty())
            assertEquals(listOf(voiceNoteFile("ogg")), voiceNotes.discardedVoiceNotes)
            assertNull(viewModel.recording)
        }

    @Test
    fun `a release right after the press waits for the microphone, then counts as too short`() =
        runViewModelTest {
            val viewModel = privateChat()

            viewModel.onEvent(VoiceEvent.Start())
            viewModel.onEvent(VoiceEvent.Send)
            runCurrent()

            assertEquals(listOf("start:${voiceNoteFile("ogg")}:OggOpus", "stop"), recorder.calls)
            assertEquals(listOf(voiceNoteFile("ogg")), voiceNotes.discardedVoiceNotes)
            assertNull(viewModel.recording)
        }

    @Test
    fun `sliding to cancel gives the recording up and deletes its file`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start())
            record(3_000)

            send(viewModel, VoiceEvent.Cancel)

            assertEquals("cancel", recorder.calls.last())
            assertEquals(listOf(voiceNoteFile("ogg")), voiceNotes.discardedVoiceNotes)
            assertTrue(voiceNotes.sentVoiceNotes.isEmpty())
            assertNull(viewModel.recording)
        }

    @Test
    fun `locked, a pause is left out of the length, and the meter stands still meanwhile`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start(), VoiceEvent.Lock)
            record(1_000)

            send(viewModel, VoiceEvent.Pause)
            record(5_000)

            assertTrue(viewModel.recording!!.isPaused)
            assertEquals(1_000L, viewModel.recording!!.meter.elapsedMillis)
            assertEquals(0f, viewModel.recording!!.meter.level)
            send(viewModel, VoiceEvent.Resume)
            record(1_000)
            send(viewModel, VoiceEvent.Send)

            assertEquals(listOf("pause", "resume", "stop"), recorder.calls.drop(1))
            assertEquals(
                2,
                voiceNotes.sentVoiceNotes
                    .single()
                    .first.duration,
            )
        }

    @Test
    fun `a paused recording can be sent`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start(locked = true))
            record(2_000)
            send(viewModel, VoiceEvent.Pause)

            send(viewModel, VoiceEvent.Send)

            assertEquals(
                2,
                voiceNotes.sentVoiceNotes
                    .single()
                    .first.duration,
            )
        }

    @Test
    fun `a microphone that cannot be used ends the recording with a snackbar and deletes the file`() =
        runViewModelTest {
            recorder.starts = false
            val viewModel = privateChat()

            viewModel.effects.test {
                send(viewModel, VoiceEvent.Start())

                assertEquals(VoiceEffect.RecordingFailed, awaitItem())
            }
            assertEquals(listOf(voiceNoteFile("ogg")), voiceNotes.discardedVoiceNotes)
            assertNull(viewModel.recording)
        }

    @Test
    fun `no file to record into ends the recording with the error`() =
        runViewModelTest {
            voiceNotes.onNewVoiceNoteFile = { Outcome.Failure(DomainError.FileUnavailable) }
            val viewModel = privateChat()

            viewModel.effects.test {
                send(viewModel, VoiceEvent.Start())

                assertEquals(ChatEffect.ShowError(DomainError.FileUnavailable), awaitItem())
            }
            assertTrue(recorder.calls.isEmpty())
            assertNull(viewModel.recording)
        }

    @Test
    fun `a recording that cannot be finished is not sent`() =
        runViewModelTest {
            recorder.stops = false
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start())
            record(2_000)

            viewModel.effects.test {
                send(viewModel, VoiceEvent.Send)

                assertEquals(VoiceEffect.RecordingFailed, awaitItem())
            }
            assertTrue(voiceNotes.sentVoiceNotes.isEmpty())
            assertEquals(listOf(voiceNoteFile("ogg")), voiceNotes.discardedVoiceNotes)
        }

    @Test
    fun `the screen stopping locks and pauses the recording, to be found as it was left`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, ChatEvent.Started, VoiceEvent.Start())
            record(1_500)

            send(viewModel, ChatEvent.Stopped)

            assertTrue(viewModel.recording!!.isLocked)
            assertTrue(viewModel.recording!!.isPaused)
            assertEquals("pause", recorder.calls.last())
        }

    @Test
    fun `leaving the conversation throws the recording away and frees the recorder`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start())

            clear(viewModel)

            assertEquals(listOf("cancel", "release"), recorder.calls.drop(1))
            assertEquals(listOf(voiceNoteFile("ogg")), voiceNotes.discardedVoiceNotes)
        }

    @Test
    fun `a second start while recording changes nothing`() =
        runViewModelTest {
            val viewModel = privateChat()

            send(viewModel, VoiceEvent.Start(), VoiceEvent.Start(locked = true))

            assertEquals(1, recorder.calls.count { it.startsWith("start:") })
            assertFalse(viewModel.recording!!.isLocked)
        }

    @Test
    fun `without the permission a press explains it, a refusal offers the settings and a grant says to hold`() =
        runViewModelTest {
            val viewModel = privateChat()

            viewModel.effects.test {
                send(viewModel, VoiceEvent.PermissionNeeded)
                assertEquals(VoiceEffect.ExplainMicrophone, awaitItem())

                send(viewModel, VoiceEvent.PermissionAnswered(granted = false))
                assertEquals(VoiceEffect.MicrophoneDenied, awaitItem())

                send(viewModel, VoiceEvent.PermissionAnswered(granted = true))
                assertEquals(VoiceEffect.HoldToRecord, awaitItem())
            }
            assertTrue(recorder.calls.isEmpty())
            assertNull(viewModel.recording)
        }
}
