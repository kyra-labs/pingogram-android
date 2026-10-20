package dev.kyralabs.pingogram.feature.chat.ui.voice

import dev.kyralabs.pingogram.feature.chat.ui.content.decodeWaveform
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The levels of a recording: the recorder's amplitudes as heard, the 5-bit waveform Telegram gets, the meter's recent
 * levels and the length the recording bar shows.
 */
class VoiceLevelsTest {
    @Test
    fun `amplitudes map from 50 dB below full scale to full scale`() {
        assertEquals(0f, amplitudeLevel(0))
        assertEquals(0f, amplitudeLevel(-3))
        // 20·log10(100 / 32767) ≈ −50.3 dB: still silence.
        assertEquals(0f, amplitudeLevel(100))
        assertEquals(1f, amplitudeLevel(32_767))
        // −20 dB is three fifths of the way.
        assertEquals(0.6f, amplitudeLevel(3_277), 0.001f)
    }

    @Test
    fun `the waveform has 100 levels of 5 bits in 63 bytes, the loudest stretch at 31`() {
        val waveform = voiceNoteWaveform(FloatArray(200) { if (it < 100) 0.25f else 0.5f })

        val levels = decodeWaveform(waveform.bytes)
        assertEquals(63, waveform.bytes.size)
        assertEquals(List(50) { 16 } + List(50) { 31 }, levels.take(WAVEFORM_LEVELS))
    }

    @Test
    fun `each stretch keeps its loudest level, so short peaks show`() {
        val levels = FloatArray(1_000) { 0.1f }.apply { this[505] = 1f }

        val decoded = decodeWaveform(voiceNoteWaveform(levels).bytes)

        assertEquals(31, decoded[50])
        assertEquals(3, decoded[49])
        assertEquals(3, decoded[51])
    }

    @Test
    fun `fewer levels than stretches repeat`() {
        val decoded = decodeWaveform(voiceNoteWaveform(floatArrayOf(1f, 0.5f)).bytes).take(WAVEFORM_LEVELS)

        assertEquals(List(50) { 31 } + List(50) { 16 }, decoded)
    }

    @Test
    fun `no levels, or only silence, give a flat waveform`() {
        for (levels in listOf(FloatArray(0), FloatArray(30))) {
            assertArrayEquals(IntArray(WAVEFORM_LEVELS), decodeWaveform(voiceNoteWaveform(levels).bytes).copyOf(100))
        }
    }

    @Test
    fun `the meter keeps the newest levels oldest first, and adds none while paused`() {
        val meter = RecordingMeter()

        meter.update(50, 0.1f)
        meter.update(100, 0.2f)
        meter.update(150, level = null)
        meter.update(200, 0.3f)

        assertArrayEquals(floatArrayOf(0.2f, 0.3f), meter.recentLevels(2))
        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f), meter.recentLevels(10))
        assertEquals(200L, meter.elapsedMillis)
        assertEquals(0.3f, meter.level)
    }

    @Test
    fun `the meter forgets levels older than it keeps`() {
        val meter = RecordingMeter()

        repeat(RecordingMeter.RECENT_LEVELS + 3) { meter.update(it.toLong(), it.toFloat()) }

        val levels = meter.recentLevels(RecordingMeter.RECENT_LEVELS + 10)
        assertEquals(RecordingMeter.RECENT_LEVELS, levels.size)
        assertEquals(3f, levels.first())
        assertEquals((RecordingMeter.RECENT_LEVELS + 2).toFloat(), levels.last())
    }

    @Test
    fun `the length reads minutes and seconds, and hours from an hour on`() {
        assertEquals("0:00", recordingLength(0))
        assertEquals("0:07", recordingLength(7))
        assertEquals("1:05", recordingLength(65))
        assertEquals("59:59", recordingLength(3_599))
        assertEquals("1:02:03", recordingLength(3_723))
    }
}
