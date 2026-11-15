package dev.kyralabs.pingogram.core.data.transcription

import kotlin.math.floor

/**
 * Turns decoded audio into what Android's speech recognizer reads by default from an audio source it is handed
 * (`RecognizerIntent.EXTRA_AUDIO_SOURCE`): 16-bit PCM, little-endian, one channel at [OUTPUT_RATE] Hz.
 *
 * Fed chunk by chunk ([convert]) as the decoder gives them: interleaved 16-bit samples of [channels] channels at
 * [inputRate] Hz. The channels are averaged into one, and the rate changed by linear interpolation, carrying where it
 * stands across chunks, so the output is one continuous stream. Not thread-safe: one stream, one thread.
 */
internal class SpeechPcm(
    private val inputRate: Int,
    private val channels: Int,
) {
    init {
        require(inputRate > 0 && channels > 0) { "No audio format" }
    }

    /** Input samples (one channel) per output sample. */
    private val step = inputRate.toDouble() / OUTPUT_RATE

    /** Where the next output sample falls, in input samples after [previous]. */
    private var position = 0.0

    /** The last mono sample of the chunk before, which the next interpolates from; null before the first. */
    private var previous: Int? = null

    /** The bytes to hand the recognizer for [samples], interleaved input samples (a partial frame is dropped). */
    fun convert(samples: ShortArray): ByteArray {
        val frames = samples.size / channels
        if (frames == 0) return ByteArray(0)
        val mono =
            IntArray(frames) { frame ->
                (0 until channels).sumOf { samples[frame * channels + it].toInt() } /
                    channels
            }
        // This chunk's mono samples, after the previous chunk's last one (index 0, where there is one).
        val start = previous
        val stream = if (start == null) mono else intArrayOf(start) + mono
        val out = ArrayList<Short>((frames / step).toInt() + 2)
        while (position <= stream.size - 1) {
            val index = floor(position).toInt()
            val fraction = position - index
            val next = if (index + 1 < stream.size) stream[index + 1] else stream[index]
            out += (stream[index] + (next - stream[index]) * fraction).toInt().toShort()
            position += step
        }
        // Positions are measured from the previous chunk's last sample, which becomes this chunk's last one.
        position -= stream.size - 1
        previous = mono.last()
        return out.toLittleEndian()
    }

    companion object {
        /** The recognizer's default sampling rate for an audio source. */
        const val OUTPUT_RATE = 16_000
    }
}

private fun List<Short>.toLittleEndian(): ByteArray {
    val bytes = ByteArray(size * 2)
    forEachIndexed { index, sample ->
        val value = sample.toInt()
        bytes[index * 2] = (value and BYTE_MASK).toByte()
        bytes[index * 2 + 1] = (value shr BITS_PER_BYTE and BYTE_MASK).toByte()
    }
    return bytes
}

private const val BYTE_MASK = 0xFF
private const val BITS_PER_BYTE = 8
