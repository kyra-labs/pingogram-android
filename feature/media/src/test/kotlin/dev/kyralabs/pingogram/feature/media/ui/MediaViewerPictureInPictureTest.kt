package dev.kyralabs.pingogram.feature.media.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.feature.media.pip.PipVideo
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.CHAT
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.chat
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.fullFileId
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.message
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.videoContent
import dev.kyralabs.pingogram.feature.media.ui.player.PlaySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The viewer's video going into picture-in-picture and coming back (`docs/DESIGN_SYSTEM.md` §6, media viewer). */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewerPictureInPictureTest : MediaViewerViewModelHarness() {
    private val videoPath = "/files/video.mp4"

    private fun TestScope.playingVideo(): MediaViewerViewModel {
        media.serve(listOf(message(1), message(2, videoContent(2))))
        files.setFile(fullFileId(2), FileState.Downloaded(videoPath, 12_000_000))
        return viewModel(messageId = 2).also { send(it, MediaViewerEvent.Started) }
    }

    @Test
    fun `picture-in-picture takes the playing video where it is, and the viewer closes`() =
        runViewModelTest {
            val viewModel = playingVideo()
            player.positionMillis = 12_000

            viewModel.effects.test {
                send(viewModel, MediaViewerEvent.PictureInPicture)

                assertEquals(MediaViewerEffect.OpenPictureInPicture, awaitItem())
            }
            assertEquals(listOf("play:$videoPath:Video", "stop"), player.calls)
            assertEquals(
                PipVideo(CHAT, 2, PlaySource.File(videoPath), 12_000, 1920, 1080),
                pictureInPicture.take(),
            )
            assertNull(viewModel.uiState.value.let { (it as MediaViewerUiState.Content).playback })
        }

    @Test
    fun `a video given back plays on from where picture-in-picture left it`() =
        runViewModelTest {
            pictureInPicture.giveBack(PipVideo(CHAT, 2, PlaySource.File(videoPath), 30_000, 1920, 1080))
            playingVideo()

            assertEquals(listOf("play:$videoPath:Video", "seek:30000"), player.calls)
            assertNull(pictureInPicture.takeReturn(CHAT, 2))
        }

    @Test
    fun `a secret chat's video and a video not playing stay in the viewer`() =
        runViewModelTest {
            chats.chats.value = mapOf(CHAT to chat(CHAT, "Ada").copy(type = ChatType.Secret(7, userId = 42)))
            val secret = playingVideo()
            // The screen collects it the whole time, for the secure window.
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { secret.isSecretChat.collect {} }
            runCurrent()

            send(secret, MediaViewerEvent.PictureInPicture)

            assertEquals(listOf("play:$videoPath:Video"), player.calls)
            assertNull(pictureInPicture.take())
        }
}
