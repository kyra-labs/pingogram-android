package dev.kyralabs.pingogram.core.data.voice

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Cuts a recorded voice message to a part of it, on the device: the trimming of a paused recording before it is sent.
 * A seam, so the repository is tested on the JVM; [MediaVoiceNoteCutter] on Android's media framework.
 */
internal fun interface VoiceNoteCutter {
    /**
     * Writes the audio of [source] from [startUs] to [endUs] (microseconds) into [target], a new file of the same
     * format; false when it cannot be cut, leaving [target] for the caller to delete. Blocking IO: call it off the main
     * thread.
     */
    fun cut(
        source: String,
        target: String,
        startUs: Long,
        endUs: Long,
    ): Boolean
}

/**
 * [VoiceNoteCutter] with the platform's [MediaExtractor] and [MediaMuxer]: the audio track's samples (every one a sync
 * sample) are copied without decoding, those from [VoiceNoteCutter.cut]'s start to its end, from time 0, into an Ogg
 * file for Ogg Opus (Android 10 and later, as recording it is) or an MP4 one for AAC. Nothing is encoded again.
 */
internal object MediaVoiceNoteCutter : VoiceNoteCutter {
    override fun cut(
        source: String,
        target: String,
        startUs: Long,
        endUs: Long,
    ): Boolean {
        val output = outputFormatOf(source) ?: return false
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(source)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).isAudio }
            track != null && write(extractor, track, MediaMuxer(target, output), startUs, endUs)
        } catch (_: IOException) {
            false
        } catch (_: IllegalStateException) {
            // MediaMuxer refuses a track or a sample it cannot write.
            false
        } catch (_: IllegalArgumentException) {
            false
        } finally {
            extractor.release()
        }
    }

    /** Writes [extractor]'s audio [track] from [startUs] to [endUs] with [muxer], releasing it; whether any was. */
    private fun write(
        extractor: MediaExtractor,
        track: Int,
        muxer: MediaMuxer,
        startUs: Long,
        endUs: Long,
    ): Boolean =
        try {
            extractor.selectTrack(track)
            val index = muxer.addTrack(extractor.getTrackFormat(track))
            muxer.start()
            val written = copySamples(extractor, muxer, index, startUs, endUs)
            muxer.stop()
            written
        } finally {
            muxer.release()
        }

    /** Copies the samples from [startUs] to [endUs] to [muxer]'s track [index]; whether any was written. */
    private fun copySamples(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        index: Int,
        startUs: Long,
        endUs: Long,
    ): Boolean {
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        val info = MediaCodec.BufferInfo()
        var written = false
        while (extractor.sampleTime in 0 until endUs) {
            val time = extractor.sampleTime
            val size = extractor.readSampleData(buffer, 0)
            if (size >= 0 && time >= startUs) {
                info.set(0, size, time - startUs, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                muxer.writeSampleData(index, buffer, info)
                written = true
            }
            extractor.advance()
        }
        return written
    }

    /** The container a voice message of [path]'s extension is written in; null for a format it cannot write. */
    private fun outputFormatOf(path: String): Int? =
        when {
            path.endsWith(".m4a") -> {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }

            path.endsWith(".ogg") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
            }

            else -> {
                null
            }
        }

    private val MediaFormat.isAudio: Boolean get() = getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true

    /** Far more than one Opus or AAC frame takes. */
    private const val BUFFER_SIZE = 64 * 1024
}
