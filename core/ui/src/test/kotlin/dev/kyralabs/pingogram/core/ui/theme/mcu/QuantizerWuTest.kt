package dev.kyralabs.pingogram.core.ui.theme.mcu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The ported Wu quantizer and color counter give what material-color-utilities 0.4.0 gives for [quantizerTestImage];
 * the expected colors were printed by that library (`tools/theme`'s copy).
 */
class QuantizerWuTest {
    @Test
    fun `counts each opaque color once, in the order first seen`() {
        val counts = QuantizerMap.quantize(quantizerTestImage())

        assertEquals(3855, counts.size)
        assertEquals(
            listOf(0xFF250701.toInt() to 1, 0xFF4A0E04.toInt() to 1, 0xFF6F1509.toInt() to 1),
            counts.entries.take(3).map { it.key to it.value },
        )
    }

    @Test
    fun `cuts the image into the same four colors`() {
        assertEquals(
            listOf(0xFF403F7E.toInt(), 0xFFBF3F81.toInt(), 0xFF3FBF80.toInt(), 0xFFC0BF7C.toInt()),
            QuantizerWu().quantize(quantizerTestImage(), 4),
        )
    }

    @Test
    fun `cuts the image into the same sixteen colors`() {
        assertEquals(
            listOf(
                0xFF1F3F3E.toInt(),
                0xFFBD1E3E.toInt(),
                0xFF3C9E40.toInt(),
                0xFF9EBE42.toInt(),
                0xFFBC1EBE.toInt(),
                0xFF3D9EBE.toInt(),
                0xFF1F41BE.toInt(),
                0xFFA0C1BC.toInt(),
                0xFFC262BF.toInt(),
                0xFF42E1BD.toInt(),
                0xFFC16042.toInt(),
                0xFF42E141.toInt(),
                0xFFE1BF42.toInt(),
                0xFF603F40.toInt(),
                0xFF603EBD.toInt(),
                0xFFE0BEBE.toInt(),
            ),
            QuantizerWu().quantize(quantizerTestImage(), 16),
        )
    }

    @Test
    fun `a single color gives that color`() {
        assertEquals(listOf(0xFF2156C4.toInt()), QuantizerWu().quantize(IntArray(100) { 0xFF2156C4.toInt() }, 8))
    }
}
