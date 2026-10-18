package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.ui.unit.LayoutDirection
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WaveformTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `decodes 5-bit levels packed least significant bit first`() {
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 6, 7, 8), decodeWaveform(bytes(0x41, 0x0C, 0x52, 0xCC, 0x41)))
        assertArrayEquals(
            intArrayOf(31, 0, 31, 0, 31, 0, 31, 0),
            decodeWaveform(bytes(0x1F, 0x7C, 0xF0, 0xC1, 0x07)),
        )
    }

    @Test
    fun `reads levels that span two bytes`() {
        assertArrayEquals(intArrayOf(0, 31, 0), decodeWaveform(bytes(0xE0, 0x03)))
        assertArrayEquals(intArrayOf(31, 31, 31), decodeWaveform(bytes(0xFF, 0xFF)))
    }

    @Test
    fun `ignores trailing bits too few for a level`() {
        assertArrayEquals(intArrayOf(17), decodeWaveform(bytes(0x11)))
        assertEquals(100, decodeWaveform(ByteArray(63)).size)
    }

    @Test
    fun `is empty without bytes`() {
        assertArrayEquals(IntArray(0), decodeWaveform(ByteArray(0)))
    }

    @Test
    fun `encodes what it decodes`() {
        val levels = IntArray(100) { (it * 7) % 32 }

        assertArrayEquals(levels, decodeWaveform(encodeWaveform(levels)))
        assertEquals(63, encodeWaveform(levels).size)
    }

    @Test
    fun `bars take the loudest level of the stretch they cover`() {
        val bars = waveformBars(intArrayOf(0, 31, 4, 8, 16, 2), barCount = 3)

        assertArrayEquals(floatArrayOf(1f, 8 / 31f, 16 / 31f), bars, 1e-6f)
    }

    @Test
    fun `with more bars than levels, levels repeat`() {
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 1f), waveformBars(intArrayOf(0, 31), barCount = 4), 1e-6f)
    }

    @Test
    fun `bars are silent without levels and empty without room`() {
        assertArrayEquals(FloatArray(3), waveformBars(IntArray(0), barCount = 3), 0f)
        assertEquals(0, waveformBars(intArrayOf(1, 2), barCount = 0).size)
    }

    @Test
    fun `seeking measures from the start side`() {
        assertEquals(0.25f, seekFraction(25f, 100, LayoutDirection.Ltr))
        assertEquals(0.75f, seekFraction(25f, 100, LayoutDirection.Rtl))
    }

    @Test
    fun `seeking stays within the bar`() {
        assertEquals(0f, seekFraction(-10f, 100, LayoutDirection.Ltr))
        assertEquals(1f, seekFraction(150f, 100, LayoutDirection.Ltr))
        assertEquals(0f, seekFraction(10f, 0, LayoutDirection.Ltr))
    }
}
