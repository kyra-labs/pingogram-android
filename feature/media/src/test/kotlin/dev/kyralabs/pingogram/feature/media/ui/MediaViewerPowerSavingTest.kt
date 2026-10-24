package dev.kyralabs.pingogram.feature.media.ui

import dev.kyralabs.pingogram.core.domain.model.ChatMediaFilter
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.PowerSaving
import dev.kyralabs.pingogram.core.domain.model.PowerSavingPart
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.fullFileId
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.gifContent
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.message
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.videoContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Power saving in the viewer: videos and GIFs wait for their play button instead of playing by themselves. */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewerPowerSavingTest : MediaViewerViewModelHarness() {
    private val videoPath = "/files/video.mp4"

    @Test
    fun `a video waits for a tap while power saving turns autoplay off`() =
        runViewModelTest {
            powerSaving.value = PowerSaving(setOf(PowerSavingPart.Autoplay))
            media.serve(listOf(message(1, videoContent(1))))
            files.setFile(fullFileId(1), FileState.Downloaded(videoPath, 12_000_000))
            val viewModel = viewModel(messageId = 1)

            send(viewModel, MediaViewerEvent.Started)
            assertEquals(emptyList<String>(), player.calls)
            assertTrue(viewModel.content.playback!!.waitsForTap)

            send(viewModel, MediaViewerEvent.PlayPause)

            assertEquals(listOf("play:$videoPath:Video"), player.calls)
            assertFalse(viewModel.content.playback!!.waitsForTap)
        }

    @Test
    fun `a GIF waits for a tap too`() =
        runViewModelTest {
            powerSaving.value = PowerSaving(setOf(PowerSavingPart.Autoplay))
            media.serve(listOf(message(1, gifContent(1))), ChatMediaFilter.Gifs)
            files.setFile(fullFileId(1), FileState.Downloaded("/files/gif.mp4", 400_000))
            val viewModel = viewModel(messageId = 1)

            send(viewModel, MediaViewerEvent.Started)

            assertEquals(emptyList<String>(), player.calls)
            assertTrue(viewModel.content.playback!!.waitsForTap)
        }

    @Test
    fun `power saving turning off plays what waits`() =
        runViewModelTest {
            powerSaving.value = PowerSaving(setOf(PowerSavingPart.Autoplay))
            media.serve(listOf(message(1, videoContent(1))))
            files.setFile(fullFileId(1), FileState.Downloaded(videoPath, 12_000_000))
            val viewModel = viewModel(messageId = 1)
            send(viewModel, MediaViewerEvent.Started)

            powerSaving.value = PowerSaving.None
            runCurrent()

            assertEquals(listOf("play:$videoPath:Video"), player.calls)
        }

    @Test
    fun `power saving that keeps autoplay plays as usual`() =
        runViewModelTest {
            powerSaving.value = PowerSaving(setOf(PowerSavingPart.AnimatedStickers))
            media.serve(listOf(message(1, videoContent(1))))
            files.setFile(fullFileId(1), FileState.Downloaded(videoPath, 12_000_000))

            send(viewModel(messageId = 1), MediaViewerEvent.Started)

            assertEquals(listOf("play:$videoPath:Video"), player.calls)
        }
}
