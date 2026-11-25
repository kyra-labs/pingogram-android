package dev.kyralabs.pingogram.feature.media.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.FixedTrackSelection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [localFileLoadControl]: a video TDLib is still downloading (`tdlib-file://`, which Media3 takes for a stream) is
 * buffered as a local file is, time first, at least a second ahead, then no further than about 19 MB, where Media3's
 * stream sizing would keep loading up to 50 s and 131 MB.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class PlayerBuffersTest {
    private val control = localFileLoadControl().apply { onPrepared(PlayerId.UNSET) }

    /** Media3's own sizing, for a stream from a server. */
    private val stock = DefaultLoadControl().apply { onPrepared(PlayerId.UNSET) }

    /** A video track, which Media3 sizes a buffer for. */
    private val video = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build())

    private val timeline =
        SinglePeriodTimeline(
            VIDEO_US,
            true,
            false,
            false,
            null,
            MediaItem.fromUri("tdlib-file://42"),
        )

    /** Whether the player loads more with [bufferedMs] ahead and [megabytes] of [control]'s buffer taken. */
    private fun loadsMore(
        bufferedMs: Long,
        megabytes: Int,
        control: DefaultLoadControl = this.control,
    ): Boolean {
        val allocator = control.getAllocator(PlayerId.UNSET)
        val segments = List(megabytes * SEGMENTS_PER_MB) { allocator.allocate() }
        val parameters =
            LoadControl.Parameters(
                PlayerId.UNSET,
                timeline,
                MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0)),
                0,
                bufferedMs * US_PER_MS,
                1f,
                true,
                false,
                C.TIME_UNSET,
                C.TIME_UNSET,
            )
        // As the player does once it knows the video's tracks: the buffer's target is set then.
        control.onTracksSelected(parameters, TrackGroupArray(video), arrayOf(FixedTrackSelection(video, 0)))
        return control.shouldContinueLoading(parameters).also { segments.forEach(allocator::release) }
    }

    @Test
    fun `a downloading video loads at least a second ahead, whatever its buffer holds`() {
        assertTrue(loadsMore(bufferedMs = 500, megabytes = 30))
    }

    @Test
    fun `once loading, it loads on past a second until about 19 MB are buffered, not 131`() {
        assertTrue(loadsMore(bufferedMs = 0, megabytes = 0))

        // Between the least and the most it keeps on as it was (Media3's hysteresis), until the bytes run out.
        assertTrue(loadsMore(bufferedMs = 10_000, megabytes = 1))
        assertFalse(loadsMore(bufferedMs = 10_000, megabytes = 30))
    }

    @Test
    fun `Media3's stream sizing would have loaded on at 30 MB`() {
        assertTrue(loadsMore(bufferedMs = 0, megabytes = 0, control = stock))

        assertTrue(loadsMore(bufferedMs = 10_000, megabytes = 30, control = stock))
    }

    private companion object {
        const val VIDEO_US = 600_000_000L
        const val US_PER_MS = 1_000L

        /** Media3's buffer segments are 64 KB: 16 to a megabyte. */
        const val SEGMENTS_PER_MB = 16
    }
}
