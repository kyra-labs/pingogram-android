package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Voice messages and songs playing in [ChatViewModel] ([ChatPlayback]) on a fake player: one at a time, downloading
 * first, the next voice message after one ends, the progress ticking without re-emitting the state, and the player
 * following the screen's lifecycle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatPlaybackTest : ChatViewModelHarness() {
    private fun voice(fileId: Int) =
        ContentPreviewData.voiceNote(isListened = true).let { content ->
            content.copy(voiceNote = content.voiceNote.copy(file = FileRef(fileId, size = 4)))
        }

    private val song = ContentPreviewData.audio

    private fun downloaded(fileId: Int) = files.setFile(fileId, FileState.Downloaded(path = "/f$fileId", size = 4))

    /** A chat with voice messages 1 and 3 (files 501 and 503), a text 2 and a song 4 (file 700), all downloaded. */
    private fun TestScope.chatWithAudio(): ChatViewModel {
        listOf(VOICE_1, VOICE_3, song.audio.file.id).forEach(::downloaded)
        return showing(
            chat(),
            history(
                message(1, content = voice(VOICE_1)),
                message(2),
                message(3, content = voice(VOICE_3)),
                message(4, content = song),
            ),
        )
    }

    private fun play(messageId: Long) = ChatEvent.PlayPause(messageId, fileId = 0)

    private val ChatViewModel.playback get() = state.playback

    private val ChatViewModel.progress get() = playback?.playback?.progress?.invoke()

    /** Lets the progress ticker run once. */
    private fun TestScope.tick() {
        advanceTimeBy(TICK)
        runCurrent()
    }

    // Play, pause and seek

    @Test
    fun `playing a downloaded voice message plays its file as speech`() =
        runViewModelTest {
            val viewModel = chatWithAudio()

            send(viewModel, play(1))

            assertEquals(listOf("play:/f$VOICE_1:Speech:0"), player.calls)
            assertEquals(1L, viewModel.playback?.messageId)
            assertTrue(viewModel.playback?.playback?.isPlaying == true)
        }

    @Test
    fun `a song plays as music`() =
        runViewModelTest {
            val viewModel = chatWithAudio()

            send(viewModel, play(4))

            assertEquals(listOf("play:/f${song.audio.file.id}:Music:0"), player.calls)
        }

    @Test
    fun `playing it again pauses, and once more plays on`() =
        runViewModelTest {
            val viewModel = chatWithAudio()

            send(viewModel, play(1), play(1))
            assertFalse(viewModel.playback!!.playback.isPlaying)
            send(viewModel, play(1))

            assertEquals(listOf("play:/f$VOICE_1:Speech:0", "pause", "resume"), player.calls)
            assertTrue(viewModel.playback!!.playback.isPlaying)
        }

    @Test
    fun `playing another message stops the one playing`() =
        runViewModelTest {
            val viewModel = chatWithAudio()

            send(viewModel, play(1), play(4))

            assertEquals(
                listOf("play:/f$VOICE_1:Speech:0", "stop", "play:/f${song.audio.file.id}:Music:0"),
                player.calls,
            )
            assertEquals(4L, viewModel.playback?.messageId)
        }

    @Test
    fun `seeking moves the player and shows the position at once`() =
        runViewModelTest {
            player.durationMillis = 40_000
            val viewModel = chatWithAudio()
            send(viewModel, play(1))

            send(viewModel, ChatEvent.Seek(messageId = 1, fraction = 0.25f))

            assertEquals("seek:10000", player.calls.last())
            assertEquals(0.25f, viewModel.progress)
        }

    @Test
    fun `seeking a message that is not playing plays it from there`() =
        runViewModelTest {
            val viewModel = chatWithAudio()

            // Its length is 42 seconds while the player does not know it.
            send(viewModel, ChatEvent.Seek(messageId = 3, fraction = 0.5f))

            assertEquals(listOf("play:/f$VOICE_3:Speech:21000"), player.calls)
            assertEquals(3L, viewModel.playback?.messageId)
        }

    // Progress

    @Test
    fun `the progress follows the player while it plays`() =
        runViewModelTest {
            player.durationMillis = 40_000
            val viewModel = chatWithAudio()
            send(viewModel, play(1))

            player.positionMillis = 10_000
            tick()

            assertEquals(0.25f, viewModel.progress)
        }

    @Test
    fun `ticks move the progress without emitting a new state`() =
        runViewModelTest {
            player.durationMillis = 40_000
            val viewModel = chatWithAudio()
            send(viewModel, play(1))
            val states = mutableListOf<ChatUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect(states::add) }

            repeat(SECOND / TICK.toInt()) { index ->
                player.positionMillis = index * TICK
                tick()
            }

            assertEquals(1, states.size)
            assertTrue(viewModel.progress!! > 0f)
        }

    @Test
    fun `nothing ticks while paused`() =
        runViewModelTest {
            player.durationMillis = 40_000
            val viewModel = chatWithAudio()
            send(viewModel, play(1))
            player.positionMillis = 10_000
            send(viewModel, play(1))

            player.positionMillis = 20_000
            advanceTimeBy(SECOND.toLong())

            assertEquals(0.25f, viewModel.progress)
        }

    // The end

    @Test
    fun `a voice message that ends plays the next newer voice message`() =
        runViewModelTest {
            val viewModel = chatWithAudio()
            send(viewModel, play(1))

            player.end()
            runCurrent()

            assertEquals(listOf("play:/f$VOICE_1:Speech:0", "stop", "play:/f$VOICE_3:Speech:0"), player.calls)
            assertEquals(3L, viewModel.playback?.messageId)
        }

    @Test
    fun `the newest voice message stops at its end`() =
        runViewModelTest {
            val viewModel = chatWithAudio()
            send(viewModel, play(3))

            player.end()
            runCurrent()

            assertEquals(listOf("play:/f$VOICE_3:Speech:0", "stop"), player.calls)
            assertNull(viewModel.playback)
        }

    @Test
    fun `a song that ends plays nothing after it`() =
        runViewModelTest {
            downloaded(VOICE_3)
            downloaded(song.audio.file.id)
            val viewModel =
                showing(chat(), history(message(1, content = song), message(3, content = voice(VOICE_3))))
            send(viewModel, play(1))

            player.end()
            runCurrent()

            assertEquals(listOf("play:/f${song.audio.file.id}:Music:0", "stop"), player.calls)
            assertNull(viewModel.playback)
        }

    // Downloads

    @Test
    fun `a file that is not on the device downloads first, then plays`() =
        runViewModelTest {
            files.setFile(VOICE_1, FileState.NotDownloaded(size = 4, canBeDownloaded = true))
            val viewModel = showing(chat(), history(message(1, content = voice(VOICE_1))))

            send(viewModel, play(1))

            assertEquals(listOf("download:$VOICE_1:Visible"), files.calls)
            assertTrue(player.calls.isEmpty())
            // Shown as playing meanwhile, so the button pauses.
            assertTrue(viewModel.playback!!.playback.isPlaying)

            downloaded(VOICE_1)
            runCurrent()

            assertEquals(listOf("play:/f$VOICE_1:Speech:0"), player.calls)
        }

    @Test
    fun `a file that already downloads is not asked for again`() =
        runViewModelTest {
            files.setFile(VOICE_1, FileState.Downloading(downloadedSize = 1, size = 4, isActive = true))
            val viewModel = showing(chat(), history(message(1, content = voice(VOICE_1))))

            send(viewModel, play(1))
            downloaded(VOICE_1)
            runCurrent()

            assertTrue(files.calls.isEmpty())
            assertEquals(listOf("play:/f$VOICE_1:Speech:0"), player.calls)
        }

    @Test
    fun `pausing while the file downloads stops waiting for it, not the download`() =
        runViewModelTest {
            files.setFile(VOICE_1, FileState.NotDownloaded(size = 4, canBeDownloaded = true))
            val viewModel = showing(chat(), history(message(1, content = voice(VOICE_1))))

            send(viewModel, play(1), play(1))
            downloaded(VOICE_1)
            runCurrent()

            assertEquals(listOf("download:$VOICE_1:Visible"), files.calls)
            assertTrue(player.calls.isEmpty())
            assertNull(viewModel.playback)
        }

    @Test
    fun `a download that cannot start shows its error and plays nothing`() =
        runViewModelTest {
            files.setFile(VOICE_1, FileState.NotDownloaded(size = 4, canBeDownloaded = true))
            files.onDownload = { Outcome.Failure(DomainError.Network) }
            val viewModel = showing(chat(), history(message(1, content = voice(VOICE_1))))

            viewModel.effects.test {
                send(viewModel, play(1))
                assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
            }
            assertNull(viewModel.playback)
            assertTrue(player.calls.isEmpty())
        }

    @Test
    fun `a message that is not a voice message or song plays nothing`() =
        runViewModelTest {
            val viewModel = showing(chat(), history(message(1, content = MessageContent.Unsupported)))

            send(viewModel, play(1))

            assertNull(viewModel.playback)
            assertTrue(player.calls.isEmpty())
        }

    // The player and the screen

    @Test
    fun `the player pausing itself shows as paused`() =
        runViewModelTest {
            val viewModel = chatWithAudio()
            send(viewModel, play(1))

            player.pauseBySystem()
            runCurrent()

            assertFalse(viewModel.playback!!.playback.isPlaying)
        }

    @Test
    fun `a file the player cannot play shows a snackbar and is logged without details`() =
        runViewModelTest {
            val viewModel = chatWithAudio()
            send(viewModel, play(1))

            viewModel.effects.test {
                player.fail()
                assertEquals(ChatEffect.PlaybackFailed, awaitItem())
            }
            runCurrent()
            assertNull(viewModel.playback)
            assertEquals(listOf("Playback failed: Speech"), logged)
        }

    @Test
    fun `stopping the screen pauses`() =
        runViewModelTest {
            val viewModel = chatWithAudio()
            send(viewModel, ChatEvent.Started, play(1))

            send(viewModel, ChatEvent.Stopped)

            assertEquals("pause", player.calls.last())
            assertFalse(viewModel.playback!!.playback.isPlaying)
        }

    @Test
    fun `stopping the screen while the file downloads plays nothing once it is there`() =
        runViewModelTest {
            files.setFile(VOICE_1, FileState.NotDownloaded(size = 4, canBeDownloaded = true))
            val viewModel = showing(chat(), history(message(1, content = voice(VOICE_1))))
            send(viewModel, ChatEvent.Started, play(1))

            send(viewModel, ChatEvent.Stopped)
            downloaded(VOICE_1)
            runCurrent()

            assertTrue(player.calls.isEmpty())
            assertNull(viewModel.playback)
        }

    @Test
    fun `the player is released with the view model`() =
        runViewModelTest {
            val viewModel = chatWithAudio()
            send(viewModel, play(1))

            clear(viewModel)

            assertEquals(listOf("play:/f$VOICE_1:Speech:0", "stop", "release"), player.calls)
            assertNull(player.listener)
        }

    private companion object {
        const val VOICE_1 = 501
        const val VOICE_3 = 503
        const val TICK = 32L
        const val SECOND = 1000
    }
}
