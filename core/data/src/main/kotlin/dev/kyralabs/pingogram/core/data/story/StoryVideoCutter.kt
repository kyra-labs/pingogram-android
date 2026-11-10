package dev.kyralabs.pingogram.core.data.story

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/** A part of a cut video: its file and its length in seconds. */
internal data class VideoPart(
    val path: String,
    val durationSeconds: Double,
)

/**
 * Cuts a video into parts no longer than a story takes (`docs/adr/0036-posting-stories.md`), on the device. A seam, so
 * posting is tested on the JVM; [MediaStoryVideoCutter] on Android's media framework.
 */
internal fun interface StoryVideoCutter {
    /**
     * The video at [path] as parts of at most [maxSeconds] each, in order, each in a new upload directory; null when it
     * cannot be cut, leaving nothing behind. Blocking IO: call it off the main thread.
     */
    suspend fun cut(
        path: String,
        maxSeconds: Int,
    ): List<VideoPart>?
}

/**
 * [StoryVideoCutter] with the platform's [MediaExtractor] and [MediaMuxer]: the video track's samples are read without
 * decoding, cut at key frames ([storyVideoCuts]) and written with the audio track into new MP4 files, keeping the
 * video's rotation. Nothing is encoded again, so no quality is lost and no codec runs.
 */
internal class MediaStoryVideoCutter(
    private val files: UploadFiles,
) : StoryVideoCutter {
    override suspend fun cut(
        path: String,
        maxSeconds: Int,
    ): List<VideoPart>? {
        val written = mutableListOf<File>()
        val parts =
            try {
                Tracks.of(path)?.let { tracks ->
                    storyVideoCuts(tracks.syncTimesUs, tracks.durationUs, maxSeconds * MICROS)?.map { range ->
                        currentCoroutineContext().ensureActive()
                        val file = File(files.newUploadDirectory(), PART_NAME).also(written::add)
                        writePart(path, tracks, range, file)
                        VideoPart(file.path, (range.last + 1 - range.first).toDouble() / MICROS)
                    }
                }
            } catch (_: IOException) {
                null
            } catch (_: IllegalStateException) {
                // MediaMuxer refuses a track or a sample it cannot write.
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        if (parts == null) written.forEach { it.parentFile?.deleteRecursively() }
        return parts
    }

    /** Writes the samples of [range] (in the video's time) of the video at [path] into [target], from time 0. */
    private fun writePart(
        path: String,
        tracks: Tracks,
        range: LongRange,
        target: File,
    ) {
        val muxer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            muxer.setOrientationHint(tracks.rotation)
            val indexes = tracks.formats.mapValues { (_, format) -> muxer.addTrack(format) }
            muxer.start()
            tracks.formats.keys.forEach { track -> copySamples(path, track, range, muxer, indexes.getValue(track)) }
            muxer.stop()
        } finally {
            muxer.release()
        }
    }

    /** Copies track [track]'s samples in [range] from the video at [path] to [muxer]'s track [index]. */
    private fun copySamples(
        path: String,
        track: Int,
        range: LongRange,
        muxer: MediaMuxer,
        index: Int,
    ) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            extractor.selectTrack(track)
            extractor.seekTo(range.first, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val info = MediaCodec.BufferInfo()
            while (extractor.sampleTime in 0..range.last) {
                val time = extractor.sampleTime
                val size = extractor.readSampleData(buffer, 0)
                if (size >= 0 && time >= range.first) {
                    info.set(0, size, time - range.first, extractor.sampleFlags.toBufferFlags())
                    muxer.writeSampleData(index, buffer, info)
                }
                extractor.advance()
            }
        } finally {
            extractor.release()
        }
    }

    /** The video's tracks to keep (its video and audio), its key frames, its length and its rotation. */
    private class Tracks(
        val formats: Map<Int, MediaFormat>,
        val syncTimesUs: List<Long>,
        val durationUs: Long,
        val rotation: Int,
    ) {
        companion object {
            /** The tracks of the video at [path]; null without a video track or a length. */
            fun of(path: String): Tracks? {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(path)
                    val formats =
                        (0 until extractor.trackCount)
                            .associateWith(extractor::getTrackFormat)
                            .filterValues {
                                it.mime()?.startsWith(
                                    VIDEO,
                                ) == true || it.mime()?.startsWith(AUDIO) == true
                            }
                    val video = formats.entries.firstOrNull { it.value.mime()?.startsWith(VIDEO) == true }
                    val duration = video?.value?.longOrNull(MediaFormat.KEY_DURATION)?.takeIf { it > 0 }
                    return if (video == null || duration == null) {
                        null
                    } else {
                        extractor.selectTrack(video.key)
                        val syncTimes = generateSequence { extractor.nextSyncTime() }.toList()
                        Tracks(formats, syncTimes, duration, video.value.intOrNull(MediaFormat.KEY_ROTATION) ?: 0)
                    }
                } finally {
                    extractor.release()
                }
            }
        }
    }
}

/** The time of the next key frame, advancing past the samples before it; null at the end. */
private fun MediaExtractor.nextSyncTime(): Long? {
    while (sampleTime >= 0) {
        val time = sampleTime
        val sync = sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
        advance()
        if (sync) return time
    }
    return null
}

private fun Int.toBufferFlags(): Int =
    if (this and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0

private fun MediaFormat.mime(): String? = getString(MediaFormat.KEY_MIME)

private fun MediaFormat.longOrNull(key: String): Long? = if (containsKey(key)) getLong(key) else null

private fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null

private const val MICROS = 1_000_000L
private const val PART_NAME = "story_part.mp4"
private const val VIDEO = "video/"
private const val AUDIO = "audio/"

/** Big enough for one sample of a phone's video at its usual bitrates. */
private const val BUFFER_SIZE = 4 * 1024 * 1024
