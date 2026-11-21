package dev.kyralabs.pingogram.core.ui.theme.mcu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The ported Celebi quantizer (Wu, then weighted k-means) gives what material-color-utilities 0.4.0 gives for
 * [quantizerTestImage] when its random starting assignment is the port's (Java's `Random(0x42688)`, as upstream's
 * Java uses): the expected clusters were printed by that library with `Math.random` drawing the same sequence.
 */
class QuantizerCelebiTest {
    @Test
    fun `finds the same four colors and populations`() {
        assertEquals(
            mapOf(
                0xFF824BBD.toInt() to 1136,
                0xFFC44B54.toInt() to 737,
                0xFF7BCF58.toInt() to 1032,
                0xFF849685.toInt() to 1191,
            ),
            QuantizerCelebi.quantize(quantizerTestImage(), 4),
        )
    }

    @Test
    fun `finds the same sixteen colors and populations, in the same order`() {
        val expected =
            listOf(
                0xFF3C5039.toInt() to 192, 0xFFCF2F44.toInt() to 250, 0xFF449946.toInt() to 262,
                0xFFA8C84B.toInt() to 246, 0xFFC52FCF.toInt() to 233, 0xFF5D8DC5.toInt() to 290,
                0xFF524AAC.toInt() to 283, 0xFF76B7B2.toInt() to 307, 0xFFC3529F.toInt() to 321,
                0xFF58E1B4.toInt() to 244, 0xFFC46631.toInt() to 230, 0xFF55E247.toInt() to 346,
                0xFFD9B842.toInt() to 216, 0xFF64374C.toInt() to 213, 0xFF5A2CD5.toInt() to 256,
                0xFFD3AEA8.toInt() to 207,
            )

        assertEquals(expected, QuantizerCelebi.quantize(quantizerTestImage(), 16).toList())
    }

    @Test
    fun `asked for 128 colors, keeps the same first ones`() {
        val result = QuantizerCelebi.quantize(quantizerTestImage(), 128).toList()

        assertEquals(128, result.size)
        assertEquals(
            listOf(0xFF152521.toInt() to 26, 0xFFA61422.toInt() to 27, 0xFF288B23.toInt() to 48),
            result.take(3),
        )
        assertEquals(0xFF6F2F57.toInt() to 19, result.last())
    }
}
