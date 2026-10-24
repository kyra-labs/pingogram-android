package dev.kyralabs.pingogram.feature.media.pip

import app.cash.turbine.test
import dev.kyralabs.pingogram.feature.media.ui.player.PlaySource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PictureInPictureVideosTest {
    private val videos = PictureInPictureVideos()
    private val video = PipVideo(1, 2, PlaySource.Stream(3), 4_000, 1280, 720)

    @Test
    fun `the window takes the video it was handed once`() {
        videos.start(video)

        assertEquals(video, videos.take())
        assertNull(videos.take())
    }

    @Test
    fun `a video given back reopens its viewer once, which takes up its position`() =
        runTest {
            videos.returns.test {
                assertNull(awaitItem())

                videos.giveBack(video)
                assertEquals(video, awaitItem())

                videos.reopened(video)
                assertEquals(video.copy(reopened = true), awaitItem())

                assertNull(videos.takeReturn(1, 99))
                assertEquals(video.copy(reopened = true), videos.takeReturn(1, 2))
                assertNull(awaitItem())
            }
        }

    @Test
    fun `handing over another video drops one given back and not reopened`() {
        videos.giveBack(video)
        videos.start(video.copy(messageId = 5))

        assertNull(videos.returns.value)
    }

    @Test
    fun `the window's buttons reach the activity`() =
        runTest {
            videos.actions.test {
                videos.onAction(PipAction.PlayPause)

                assertEquals(PipAction.PlayPause, awaitItem())
            }
        }
}
