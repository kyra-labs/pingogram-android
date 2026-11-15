package dev.kyralabs.pingogram.core.data.transcription

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteOrder

/**
 * Writes the audio of a voice or round video message as the speech recognizer reads it ([SpeechPcm]): a seam, so the
 * transcriber is tested without Android's media framework; [MediaSpeechAudioDecoder] decodes with it.
 */
internal fun interface SpeechAudioDecoder {
    /**
     * Decodes the first audio track of [path] into [output] as 16-bit mono PCM at [SpeechPcm.OUTPUT_RATE] Hz, then
     * closes [output]; false where the file has no audio Android can decode. Blocking: off the main thread; stops
     * when its coroutine is cancelled.
     */
    suspend fun decode(
        path: String,
        output: OutputStream,
    ): Boolean
}

/**
 * [SpeechAudioDecoder] with the platform's [MediaExtractor] and [MediaCodec]: Ogg Opus voice messages and the AAC
 * track of MP4 round videos, both of which Android decodes on every version Pingogram transcribes on. The decoder's
 * 16-bit output goes through [SpeechPcm] chunk by chunk, so nothing but a buffer is held in memory.
 */
internal object MediaSpeechAudioDecoder : SpeechAudioDecoder {
    override suspend fun decode(
        path: String,
        output: OutputStream,
    ): Boolean =
        output.use { stream ->
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(path)
                val track = (0 until extractor.trackCount).firstOrNull { extractor.trackMime(it).startsWith("audio/") }
                if (track == null) return false
                val format = extractor.getTrackFormat(track)
                extractor.selectTrack(track)
                val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME).orEmpty())
                codec = decoder
                decoder.configure(format, null, null, 0)
                decoder.start()
                pump(extractor, decoder, format, stream)
                true
            } catch (_: IOException) {
                false
            } catch (_: IllegalStateException) {
                // A codec error, or the recognizer closed its end of the pipe.
                false
            } catch (_: IllegalArgumentException) {
                false
            } finally {
                codec?.release()
                extractor.release()
            }
        }

    /** Feeds [extractor]'s samples to [decoder] and writes what it decodes, until the end of the stream. */
    private suspend fun pump(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        inputFormat: MediaFormat,
        output: OutputStream,
    ) {
        val info = MediaCodec.BufferInfo()
        var pcm =
            SpeechPcm(
                inputRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
            )
        var inputDone = false
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!inputDone) inputDone = queueInput(extractor, decoder)
            val index = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = decoder.outputFormat
                    require(format.pcmEncoding() == AudioFormat.ENCODING_PCM_16BIT) { "Not 16-bit PCM" }
                    pcm =
                        SpeechPcm(
                            inputRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                        )
                }

                index >= 0 -> {
                    val buffer = decoder.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val samples = ShortArray(info.size / 2)
                        buffer.order(ByteOrder.nativeOrder()).asShortBuffer().get(samples)
                        output.write(pcm.convert(samples))
                    }
                    decoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /** Queues the next sample, or the end of the stream; whether that was the end. */
    private fun queueInput(
        extractor: MediaExtractor,
        decoder: MediaCodec,
    ): Boolean {
        val index = decoder.dequeueInputBuffer(TIMEOUT_US)
        val buffer = if (index >= 0) decoder.getInputBuffer(index) else null
        val size = buffer?.let { extractor.readSampleData(it, 0) }
        return if (size == null) {
            false
        } else if (size < 0) {
            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            true
        } else {
            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
            extractor.advance()
            false
        }
    }

    private fun MediaExtractor.trackMime(track: Int): String =
        getTrackFormat(track).getString(MediaFormat.KEY_MIME).orEmpty()

    /** The decoder's PCM encoding: 16-bit unless it says otherwise. */
    private fun MediaFormat.pcmEncoding(): Int =
        if (containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }

    /** How long a dequeue waits for a buffer, in microseconds. */
    private const val TIMEOUT_US = 10_000L
}
