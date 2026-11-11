package dev.kyralabs.pingogram.feature.media.ui

import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.Video
import dev.kyralabs.pingogram.feature.media.pip.PipVideo
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.CHAT
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.fullFileId
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.message
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.photoContent
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.videoContent
import dev.kyralabs.pingogram.feature.media.ui.player.PlaySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Videos that start where their sender marked (td_api.tl `messageVideo.start_timestamp`) and live photos, whose motion
 * (`messagePhoto.video`) plays over the still as a GIF does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewerMotionTest : MediaViewerViewModelHarness() {
    private val videoPath = "/files/video.mp4"
    private val motionPath = "/files/motion.mp4"
    private val motionFileId = 991

    private fun livePhoto(messageId: Long) =
        photoContent(messageId).copy(
            motion =
                Video(
                    duration = 3,
                    width = 1280,
                    height = 960,
                    minithumbnail = null,
                    thumbnail = null,
                    supportsStreaming = false,
                    file = FileRef(motionFileId, 2_000_000),
                ),
        )

    @Test
    fun `a video starts where its sender marked`() =
        runViewModelTest {
            media.serve(listOf(message(1), message(2, videoContent(2).copy(startSeconds = 83))))
            files.setFile(fullFileId(2), FileState.Downloaded(videoPath, 12_000_000))

            send(viewModel(messageId = 2), MediaViewerEvent.Started)

            assertEquals(listOf("play:$videoPath:Video", "seek:83000"), player.calls)
        }

    @Test
    fun `the start time applies once, so playing again starts from the beginning`() =
        runViewModelTest {
            media.serve(listOf(message(1), message(2, videoContent(2).copy(startSeconds = 83))))
            files.setFile(fullFileId(2), FileState.Downloaded(videoPath, 12_000_000))
            val viewModel = viewModel(messageId = 2)
            send(viewModel, MediaViewerEvent.Started)

            send(viewModel, MediaViewerEvent.PageSettled(1))
            send(viewModel, MediaViewerEvent.PageSettled(2))

            assertEquals(1, player.calls.count { it.startsWith("seek:") })
        }

    @Test
    fun `picture-in-picture's position wins over the sender's start time`() =
        runViewModelTest {
            pictureInPicture.giveBack(PipVideo(CHAT, 2, PlaySource.File(videoPath), 30_000, 1920, 1080))
            media.serve(listOf(message(1), message(2, videoContent(2).copy(startSeconds = 83))))
            files.setFile(fullFileId(2), FileState.Downloaded(videoPath, 12_000_000))

            send(viewModel(messageId = 2), MediaViewerEvent.Started)

            assertEquals(listOf("play:$videoPath:Video", "seek:30000"), player.calls)
        }

    @Test
    fun `a live photo downloads its motion after the still, and plays it as a GIF once there`() =
        runViewModelTest {
            media.serve(listOf(message(1, livePhoto(1))))
            files.setFile(fullFileId(1), FileState.Downloaded("/files/photo.jpg", 200_000))
            files.setFile(motionFileId, FileState.NotDownloaded(2_000_000, canBeDownloaded = true))
            val viewModel = viewModel(messageId = 1)
            send(viewModel, MediaViewerEvent.Started)
            assertTrue(files.calls.any { it.startsWith("download:$motionFileId:") })
            assertEquals(emptyList<String>(), player.calls)

            files.setFile(motionFileId, FileState.Downloaded(motionPath, 2_000_000))
            runCurrent()

            assertEquals(listOf("play:$motionPath:Gif"), player.calls)
        }

    @Test
    fun `a still photo plays nothing`() =
        runViewModelTest {
            media.serve(listOf(message(1)))
            files.setFile(fullFileId(1), FileState.Downloaded("/files/photo.jpg", 200_000))

            send(viewModel(messageId = 1), MediaViewerEvent.Started)

            assertEquals(emptyList<String>(), player.calls)
        }
}
