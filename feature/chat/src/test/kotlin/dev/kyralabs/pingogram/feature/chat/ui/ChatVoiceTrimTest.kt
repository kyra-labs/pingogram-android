package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.FakeVoiceNoteRepository.Companion.voiceNoteFile
import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceTrim
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Trimming a paused voice recording before it is sent (`REQUIREMENTS.md` FR-C5, [VoiceEvent.Trim]). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatVoiceTrimTest : ChatViewModelHarness() {
    private fun TestScope.paused(millis: Long): ChatViewModel {
        chats.setChats(chat())
        messages.setHistory(ADA, history(message(1)))
        val viewModel = viewModel()
        send(viewModel, VoiceEvent.Start(locked = true))
        advanceTimeBy(millis)
        runCurrent()
        send(viewModel, VoiceEvent.Pause)
        return viewModel
    }

    private val ChatViewModel.trim: VoiceTrim? get() = voiceRecording.value?.trim

    @Test
    fun `a paused recording offers all of itself to trim, a recording one nothing`() =
        runViewModelTest {
            recorder.levels = listOf(0.1f, 0.4f, 0.8f)
            val viewModel = paused(4_000)

            val trim = viewModel.trim!!
            assertEquals(4_000, trim.durationMillis)
            assertEquals(0L to 4_000L, trim.startMillis to trim.endMillis)
            assertTrue(trim.levels.isNotEmpty())

            send(viewModel, VoiceEvent.Resume)
            assertNull(viewModel.trim)
        }

    @Test
    fun `a trim keeps at least a second, within the recording`() =
        runViewModelTest {
            val viewModel = paused(4_000)

            send(viewModel, VoiceEvent.Trim(3_500, 3_600))
            assertEquals(3_000L to 4_000L, viewModel.trim!!.startMillis to viewModel.trim!!.endMillis)

            send(viewModel, VoiceEvent.Trim(-50, 9_000))
            assertEquals(0L to 4_000L, viewModel.trim!!.startMillis to viewModel.trim!!.endMillis)
        }

    @Test
    fun `a trimmed recording is cut before it is sent, with the kept part's length`() =
        runViewModelTest {
            val viewModel = paused(4_000)
            send(viewModel, VoiceEvent.Trim(1_000, 3_000))

            send(viewModel, VoiceEvent.Send)

            assertEquals(listOf(Triple(voiceNoteFile("ogg"), 1_000L, 3_000L)), voiceNotes.trimmedVoiceNotes)
            val voiceNote = voiceNotes.sentVoiceNotes.single().first
            assertEquals(voiceNoteFile("ogg").replace("voice.", "trimmed."), voiceNote.path)
            assertEquals(2, voiceNote.duration)
        }

    @Test
    fun `an untrimmed recording is sent as it is`() =
        runViewModelTest {
            val viewModel = paused(4_000)

            send(viewModel, VoiceEvent.Send)

            assertTrue(voiceNotes.trimmedVoiceNotes.isEmpty())
            assertEquals(
                voiceNoteFile("ogg"),
                voiceNotes.sentVoiceNotes
                    .single()
                    .first.path,
            )
        }

    @Test
    fun `resuming keeps all of the recording again`() =
        runViewModelTest {
            val viewModel = paused(4_000)
            send(viewModel, VoiceEvent.Trim(1_000, 3_000))

            send(viewModel, VoiceEvent.Resume)
            advanceTimeBy(1_000)
            runCurrent()
            send(viewModel, VoiceEvent.Send)

            assertTrue(voiceNotes.trimmedVoiceNotes.isEmpty())
            assertEquals(
                5,
                voiceNotes.sentVoiceNotes
                    .single()
                    .first.duration,
            )
        }

    @Test
    fun `a recording that cannot be cut is not sent whole, but says why`() =
        runViewModelTest {
            voiceNotes.onTrimVoiceNote = { Outcome.Failure(DomainError.FileUnavailable) }
            val viewModel = paused(4_000)
            send(viewModel, VoiceEvent.Trim(1_000, 3_000))

            viewModel.effects.test {
                send(viewModel, VoiceEvent.Send)

                assertEquals(ChatEffect.ShowError(DomainError.FileUnavailable), awaitItem())
            }
            assertTrue(voiceNotes.sentVoiceNotes.isEmpty())
            assertEquals(listOf(voiceNoteFile("ogg")), voiceNotes.discardedVoiceNotes)
        }
}
