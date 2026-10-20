package dev.kyralabs.pingogram.feature.chat.ui.voice

import dev.kyralabs.pingogram.core.domain.model.Waveform
import dev.kyralabs.pingogram.feature.chat.ui.content.encodeWaveform
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * How loud [amplitude] (the platform recorder's peak, 0–32767) sounds, from 0 to 1: its level in decibels below full
 * scale, mapped linearly from [SILENCE_DB] (0, and anything quieter) to full scale (1), as loudness is heard rather
 * than as the samples' size. Speech lands around the middle, so the live waveform moves with the voice instead of
 * staying flat between peaks.
 */
internal fun amplitudeLevel(amplitude: Int): Float {
    if (amplitude <= 0) return 0f
    val decibels = DECIBELS_PER_DECADE * log10(amplitude.toDouble() / MAX_AMPLITUDE)
    return ((decibels - SILENCE_DB) / -SILENCE_DB).toFloat().coerceIn(0f, 1f)
}

/**
 * The waveform Telegram shows for a voice message recorded at [levels] (each 0–1, one per sampling tick, in order): the
 * recording cut into [WAVEFORM_LEVELS] equal stretches, each its loudest level, scaled so the loudest stretch is 31,
 * and packed as 5-bit values ([encodeWaveform]). Fewer levels than stretches repeat; no levels, or only silence, give
 * a flat waveform.
 */
internal fun voiceNoteWaveform(levels: FloatArray): Waveform {
    val stretches =
        FloatArray(WAVEFORM_LEVELS) { index ->
            if (levels.isEmpty()) {
                0f
            } else {
                val start = (index.toLong() * levels.size / WAVEFORM_LEVELS).toInt()
                val end = maxOf(start + 1, ((index + 1).toLong() * levels.size / WAVEFORM_LEVELS).toInt())
                var loudest = 0f
                for (sample in start until end) loudest = maxOf(loudest, levels[sample])
                loudest
            }
        }
    val peak = stretches.max()
    val scaled = IntArray(WAVEFORM_LEVELS) { if (peak > 0f) (stretches[it] / peak * MAX_LEVEL).roundToInt() else 0 }
    return Waveform(encodeWaveform(scaled))
}

/** The levels of a voice note's waveform, as Telegram's apps send them (td_api.tl `inputVoiceNote.waveform`). */
internal const val WAVEFORM_LEVELS = 100

private const val MAX_LEVEL = 31
private const val MAX_AMPLITUDE = 32_767.0
private const val DECIBELS_PER_DECADE = 20.0

/** The quietest level the waveform shows above silence: a quiet room reads as about −60 dB, speech −30 to −10 dB. */
private const val SILENCE_DB = -50.0
