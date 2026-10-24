package dev.kyralabs.pingogram.feature.media.pip

import dev.kyralabs.pingogram.feature.media.ui.FakeVideoPlayer
import dev.kyralabs.pingogram.feature.media.ui.player.PlaySource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoPipViewModelTest {
    private val videos = PictureInPictureVideos()
    private val player = FakeVideoPlayer()
    private val video = PipVideo(1, 2, PlaySource.Stream(3), 4_000, 1280, 720)

    private fun viewModel(): VideoPipViewModel {
        videos.start(video)
        return VideoPipViewModel(videos, player)
    }

    @Test
    fun `plays the video handed over from where the viewer left it`() {
        val viewModel = viewModel()

        assertEquals(listOf("play:stream:3:Video", "seek:4000"), player.calls)
        assertEquals(PipState(video, isPlaying = true, ended = false), viewModel.state.value)
    }

    @Test
    fun `the window's button pauses and plays on`() {
        val viewModel = viewModel()

        viewModel.onAction(PipAction.PlayPause)
        viewModel.onAction(PipAction.PlayPause)

        assertEquals(listOf("play:stream:3:Video", "seek:4000", "pause", "resume"), player.calls)
        assertTrue(viewModel.state.value!!.isPlaying)
    }

    @Test
    fun `expanding the window gives the video back where it is, from the start once it ended`() {
        val viewModel = viewModel()
        player.positionMillis = 9_000

        viewModel.giveBack()
        assertEquals(video.copy(positionMillis = 9_000), videos.returns.value)

        player.listener?.onEnded()
        viewModel.giveBack()
        assertEquals(video.copy(positionMillis = 0), videos.returns.value)
    }

    @Test
    fun `nothing to play, or a video the player cannot play, closes the window`() {
        assertNull(VideoPipViewModel(videos, player).state.value)

        val viewModel = viewModel()
        player.listener?.onError()

        assertNull(viewModel.state.value)
    }

    @Test
    fun `the window keeps to the aspect ratios picture-in-picture allows`() {
        assertEquals(1280 to 720, VideoPipActivity.aspectRatio(1280, 720))
        assertEquals(2390 to 1000, VideoPipActivity.aspectRatio(4000, 1000))
        assertEquals(1000 to 2390, VideoPipActivity.aspectRatio(300, 2000))
        assertEquals(16 to 9, VideoPipActivity.aspectRatio(0, 0))
    }
}
