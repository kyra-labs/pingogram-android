package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AttachmentThumbnail
import dev.kyralabs.pingogram.core.domain.model.ChatDraft
import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.FakeVideoNoteRecorder.Companion.LENGTH
import dev.kyralabs.pingogram.feature.chat.ui.FakeVideoNoteRecorder.Companion.THUMBNAIL
import dev.kyralabs.pingogram.feature.chat.ui.FakeVideoNoteRepository.Companion.FILES
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.voice.CameraRationale
import dev.kyralabs.pingogram.feature.chat.ui.voice.VideoRecordingUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * The round video messages of [ChatViewModel]'s composer (`docs/adr/0032-round-video-messages.md`): switching the
 * record button, the permissions' rationale, recording held and locked, flipping the camera, pausing, sending (also at
 * the minute's limit), too short recordings, cancelling, failures, the screen stopping, and clearing the view model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatVideoRecorderTest : ChatViewModelHarness() {
    /** The private chat with Ada, its record button on video ([video]), replying to message 1 if [replyTo] is set. */
    private fun TestScope.privateChat(
        video: Boolean = true,
        replyTo: Long? = null,
        handle: SavedStateHandle = savedState,
        topic: MessageTopic? = null,
    ): ChatViewModel {
        composerSettings.video.value = video
        val draft = replyTo?.let { ChatDraft(DraftContent.Text(FormattedText.Empty), 0, ReplyTarget(it)) }
        chats.setChats(chat(draft = draft))
        messages.setHistory(ADA, history(message(1)))
        return viewModel(handle = handle, topic = topic)
    }

    private val ChatViewModel.recording get() = voiceRecording.value

    private val ChatViewModel.mode get() = recordMode.value

    /** Lets [millis] of recording pass, ticks included. */
    private fun TestScope.record(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `the record button records voice until a tap switches it to video, and back, kept in the settings`() =
        runViewModelTest {
            val viewModel = privateChat(video = false)
            assertFalse(viewModel.mode.video)
            assertTrue(viewModel.mode.canSwitch)

            send(viewModel, VideoNoteEvent.SwitchMode)
            assertTrue(viewModel.mode.video)
            assertTrue(composerSettings.video.value)

            send(viewModel, VideoNoteEvent.SwitchMode)
            assertFalse(viewModel.mode.video)
            assertFalse(composerSettings.video.value)
        }

    @Test
    fun `without a camera the button stays a microphone and cannot switch, whatever was saved`() =
        runViewModelTest {
            camera.hasCamera = false
            val viewModel = privateChat(video = true)

            send(viewModel, VideoNoteEvent.SwitchMode, VideoNoteEvent.PermissionNeeded(switching = true))

            assertEquals(false, viewModel.mode.video)
            assertEquals(false, viewModel.mode.canSwitch)
            assertNull(viewModel.mode.rationale)
            send(viewModel, VoiceEvent.Start())
            assertEquals("start:${FakeVoiceNoteRepository.voiceNoteFile("ogg")}:OggOpus", recorder.calls.single())
            assertTrue(camera.calls.isEmpty())
        }

    @Test
    fun `a switch that cannot be saved says so and changes nothing`() =
        runViewModelTest {
            val viewModel = privateChat(video = false)
            composerSettings.failure = DomainError.Unknown(0, "disk full")

            viewModel.effects.test {
                send(viewModel, VideoNoteEvent.SwitchMode)

                assertEquals(ChatEffect.ShowError(DomainError.Unknown(0, "disk full")), awaitItem())
            }
            assertFalse(viewModel.mode.video)
        }

    @Test
    fun `holding records a video into new files from the front camera, held under the finger`() =
        runViewModelTest {
            val viewModel = privateChat()

            send(viewModel, VoiceEvent.Start())

            assertEquals(listOf("start:${FILES.video}:front"), camera.calls)
            assertTrue(recorder.calls.isEmpty())
            assertFalse(viewModel.recording!!.isLocked)
            assertEquals(VideoRecordingUiState(isFront = true, canFlip = true), viewModel.recording!!.video)
        }

    @Test
    fun `the meter follows the camera's microphone and how long it has recorded`() =
        runViewModelTest {
            camera.level = 0.7f
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start())

            record(2_000)

            assertEquals(2_000L, viewModel.recording!!.meter.elapsedMillis)
            assertEquals(0.7f, viewModel.recording!!.meter.level)
        }

    @Test
    fun `releasing sends the video note with its length, side, thumbnail and the composer's reply`() =
        runViewModelTest {
            val viewModel = privateChat(replyTo = 1)
            send(viewModel, VoiceEvent.Start())
            record(7_400)

            send(viewModel, VoiceEvent.Send)

            val (videoNote, options, replyTo) = videoNotes.sentVideoNotes.single()
            assertEquals(FILES.video, videoNote.path)
            assertEquals(7, videoNote.duration)
            assertEquals(LENGTH, videoNote.length)
            assertEquals(AttachmentThumbnail(THUMBNAIL, 320, 320), videoNote.thumbnail)
            assertNull(options.topic)
            assertEquals(1L, replyTo)
            assertEquals("stop:${FILES.thumbnail}", camera.calls.last())
            assertNull(viewModel.recording)
            assertNull((viewModel.state.composer as ComposerUiState.Available).bar)
        }

    @Test
    fun `in a forum topic the video note goes into the topic`() =
        runViewModelTest {
            val viewModel = privateChat(topic = MessageTopic.Forum(7))
            send(viewModel, VoiceEvent.Start())
            record(2_000)

            send(viewModel, VoiceEvent.Send)

            assertEquals(
                MessageTopic.Forum(7),
                videoNotes.sentVideoNotes
                    .single()
                    .second.topic,
            )
        }

    @Test
    fun `a video note is sent by itself once it reaches a minute`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start(), VoiceEvent.Lock)

            record(60_000)

            assertEquals(
                60,
                videoNotes.sentVideoNotes
                    .single()
                    .first.duration,
            )
            assertNull(viewModel.recording)
            // The finger lifting later sends nothing more.
            send(viewModel, VoiceEvent.Send)
            assertEquals(1, videoNotes.sentVideoNotes.size)
        }

    @Test
    fun `a recording shorter than a second is thrown away, saying to hold, with the camera released`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start())
            record(600)

            viewModel.effects.test {
                send(viewModel, VoiceEvent.Send)

                assertEquals(VoiceEffect.HoldToRecord, awaitItem())
            }
            assertEquals("stop:${FILES.thumbnail}", camera.calls.last())
            assertEquals(listOf(FILES.video), videoNotes.discardedVideoNotes)
            assertTrue(videoNotes.sentVideoNotes.isEmpty())
        }

    @Test
    fun `locked, the camera flips and back, from the camera used last next time`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start(), VoiceEvent.Lock)

            send(viewModel, VideoNoteEvent.FlipCamera)
            assertFalse(viewModel.recording!!.video!!.isFront)

            record(1_500)
            send(viewModel, VoiceEvent.Send, VoiceEvent.Start())

            assertEquals(listOf("flip:back"), camera.calls.filter { it.startsWith("flip:") })
            assertEquals("start:${FILES.video}:back", camera.calls.last())
        }

    @Test
    fun `a camera that cannot be flipped to keeps the one before, with a snackbar`() =
        runViewModelTest {
            camera.flips = false
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start(), VoiceEvent.Lock)

            viewModel.effects.test {
                send(viewModel, VideoNoteEvent.FlipCamera)

                assertEquals(VideoNoteEffect.FlipFailed, awaitItem())
            }
            assertTrue(viewModel.recording!!.video!!.isFront)
        }

    @Test
    fun `with one camera there is no flip`() =
        runViewModelTest {
            camera.canFlip = false
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start(), VoiceEvent.Lock)

            send(viewModel, VideoNoteEvent.FlipCamera)

            assertFalse(viewModel.recording!!.video!!.canFlip)
            assertTrue(camera.calls.none { it.startsWith("flip:") })
        }

    @Test
    fun `locked, a pause is left out of the length, and the recording resumes`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start(), VoiceEvent.Lock)
            record(2_000)

            send(viewModel, VoiceEvent.Pause)
            record(5_000)
            assertTrue(viewModel.recording!!.isPaused)
            send(viewModel, VoiceEvent.Resume)
            record(1_000)
            send(viewModel, VoiceEvent.Send)

            assertEquals(listOf("pause", "resume"), camera.calls.filter { it == "pause" || it == "resume" })
            assertEquals(
                3,
                videoNotes.sentVideoNotes
                    .single()
                    .first.duration,
            )
        }

    @Test
    fun `sliding to cancel gives the recording up, releasing the camera and deleting the files`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start())
            record(2_000)

            send(viewModel, VoiceEvent.Cancel)

            assertEquals("cancel", camera.calls.last())
            assertEquals(listOf(FILES.video), videoNotes.discardedVideoNotes)
            assertNull(viewModel.recording)
        }

    @Test
    fun `a camera that cannot be used ends the recording with a snackbar and deletes the files`() =
        runViewModelTest {
            camera.starts = false
            val viewModel = privateChat()

            viewModel.effects.test {
                send(viewModel, VoiceEvent.Start())

                assertEquals(VideoNoteEffect.RecordingFailed, awaitItem())
            }
            assertEquals(listOf(FILES.video), videoNotes.discardedVideoNotes)
            assertNull(viewModel.recording)
        }

    @Test
    fun `no files to record into end the recording with the error`() =
        runViewModelTest {
            videoNotes.onNewVideoNoteFiles = { Outcome.Failure(DomainError.FileUnavailable) }
            val viewModel = privateChat()

            viewModel.effects.test {
                send(viewModel, VoiceEvent.Start())

                assertEquals(ChatEffect.ShowError(DomainError.FileUnavailable), awaitItem())
            }
            assertTrue(camera.calls.isEmpty())
            assertNull(viewModel.recording)
        }

    @Test
    fun `a recording that cannot be finished is not sent`() =
        runViewModelTest {
            camera.stops = null
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start())
            record(3_000)

            viewModel.effects.test {
                send(viewModel, VoiceEvent.Send)

                assertEquals(VideoNoteEffect.RecordingFailed, awaitItem())
            }
            assertEquals(listOf(FILES.video), videoNotes.discardedVideoNotes)
            assertTrue(videoNotes.sentVideoNotes.isEmpty())
        }

    @ParameterizedTest
    @MethodSource("sendErrors")
    fun `a refused video note gives the reply back with the error`(error: DomainError) =
        runViewModelTest {
            videoNotes.onSendVideoNote = { Outcome.Failure(error) }
            val viewModel = privateChat(replyTo = 1)
            send(viewModel, VoiceEvent.Start())
            record(2_000)

            viewModel.effects.test {
                send(viewModel, VoiceEvent.Send)

                assertEquals(ChatEffect.ShowError(error), awaitItem())
            }
            assertEquals(1L, (viewModel.state.composer as ComposerUiState.Available).bar?.messageId)
        }

    @Test
    fun `the screen stopping ends the recording, which waits locked and stopped to be sent as it is`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, ChatEvent.Started, VoiceEvent.Start())
            record(4_000)

            send(viewModel, ChatEvent.Stopped)

            assertEquals("stop:${FILES.thumbnail}", camera.calls.last())
            val recording = viewModel.recording!!
            assertTrue(recording.isLocked)
            assertTrue(recording.isPaused)
            assertEquals(VideoRecordingUiState(isFront = true, canFlip = false, isStopped = true), recording.video)

            send(viewModel, ChatEvent.Started, VoiceEvent.Resume, VideoNoteEvent.FlipCamera)
            record(3_000)
            send(viewModel, VoiceEvent.Send)

            assertEquals(1, camera.calls.count { it.startsWith("stop:") })
            assertTrue(camera.calls.none { it == "resume" || it.startsWith("flip:") })
            assertEquals(
                4,
                videoNotes.sentVideoNotes
                    .single()
                    .first.duration,
            )
        }

    @Test
    fun `a stopped recording can be deleted without touching the camera again`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, ChatEvent.Started, VoiceEvent.Start())
            record(2_000)
            send(viewModel, ChatEvent.Stopped)

            send(viewModel, VoiceEvent.Cancel)

            assertTrue(camera.calls.none { it == "cancel" })
            assertEquals(listOf(FILES.video), videoNotes.discardedVideoNotes)
            assertNull(viewModel.recording)
        }

    @Test
    fun `leaving the conversation throws the recording away and frees both recorders`() =
        runViewModelTest {
            val viewModel = privateChat()
            send(viewModel, VoiceEvent.Start())

            clear(viewModel)

            assertEquals(listOf("cancel", "release"), camera.calls.drop(1))
            assertEquals(listOf("release"), recorder.calls)
            assertEquals(listOf(FILES.video), videoNotes.discardedVideoNotes)
        }

    @Test
    fun `switching to video without the permissions explains them first, and a grant switches`() =
        runViewModelTest {
            val viewModel = privateChat(video = false)

            send(viewModel, VideoNoteEvent.PermissionNeeded(switching = true))
            assertEquals(CameraRationale(deniedForGood = false), viewModel.mode.rationale)
            assertFalse(viewModel.mode.video)

            send(viewModel, VideoNoteEvent.PermissionAnswered(granted = true, canAskAgain = true))

            assertNull(viewModel.mode.rationale)
            assertTrue(viewModel.mode.video)
        }

    @Test
    fun `a grant from a press while recording video does not switch the button`() =
        runViewModelTest {
            val viewModel = privateChat(video = true)

            send(
                viewModel,
                VideoNoteEvent.PermissionNeeded(switching = false),
                VideoNoteEvent.PermissionAnswered(granted = true, canAskAgain = true),
            )

            assertTrue(viewModel.mode.video)
            assertNull(viewModel.mode.rationale)
        }

    @Test
    fun `a refusal keeps the rationale, which offers the settings once Android no longer asks, after process death`() =
        runViewModelTest {
            val viewModel = privateChat(video = false)
            send(viewModel, VideoNoteEvent.PermissionNeeded(switching = true))

            send(viewModel, VideoNoteEvent.PermissionAnswered(granted = false, canAskAgain = true))
            assertEquals(CameraRationale(deniedForGood = false), viewModel.mode.rationale)

            send(viewModel, VideoNoteEvent.PermissionAnswered(granted = false, canAskAgain = false))
            assertEquals(CameraRationale(deniedForGood = true), viewModel.mode.rationale)
            assertFalse(viewModel.mode.video)

            val restored =
                privateChat(video = false, handle = SavedStateHandle(savedState.keys().associateWith(savedState::get)))
            send(restored, VideoNoteEvent.PermissionNeeded(switching = true))
            assertEquals(CameraRationale(deniedForGood = true), restored.mode.rationale)
        }

    @Test
    fun `not now closes the rationale and leaves the button as it was`() =
        runViewModelTest {
            val viewModel = privateChat(video = false)
            send(viewModel, VideoNoteEvent.PermissionNeeded(switching = true))

            send(viewModel, VideoNoteEvent.DismissRationale)

            assertNull(viewModel.mode.rationale)
            assertFalse(viewModel.mode.video)
        }

    @Test
    fun `nothing switches while a recording goes on`() =
        runViewModelTest {
            val viewModel = privateChat(video = true)
            send(viewModel, VoiceEvent.Start())

            send(viewModel, VideoNoteEvent.SwitchMode)

            assertTrue(viewModel.mode.video)
        }

    companion object {
        @JvmStatic
        fun sendErrors() = listOf(DomainError.FloodWait(30), DomainError.Network, DomainError.Unauthorized)
    }
}
