package dev.kyralabs.pingogram.core.ui.theme.mcu

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The ported color conversions and degree arithmetic give what material-color-utilities 0.4.0 gives: every expected
 * value below was printed by that library (`tools/theme`'s copy) for the same input.
 */
class ColorUtilsTest {
    @Test
    fun `converts to CIELAB and back`() {
        for ((argb, lab) in LAB) {
            assertArrayEquals(lab, labFromArgb(argb.toInt()), TOLERANCE)
            assertEquals(argb.toInt(), argbFromLab(lab[0], lab[1], lab[2]))
        }
    }

    @Test
    fun `converts between lightness, luminance and greys`() {
        assertEquals(0xFF212121.toInt(), argbFromLstar(12.5))
        assertEquals(0xFF777777.toInt(), argbFromLstar(50.0))
        assertEquals(0xFFDBDBDB.toInt(), argbFromLstar(87.25))
        assertEquals(18.418651851244416, yFromLstar(50.0), TOLERANCE)
        assertEquals(87.25, lstarFromY(yFromLstar(87.25)), TOLERANCE)
        assertEquals(0xFF00A7A4.toInt(), argbFromXyz(20.0, 30.0, 40.0))
        assertEquals(0xFFFFFFFF.toInt(), argbFromXyz(95.047, 100.0, 108.883))
    }

    @Test
    fun `sanitizes degrees and measures hue distances`() {
        assertEquals(330, sanitizeDegreesInt(-30))
        assertEquals(5.5, sanitizeDegreesDouble(725.5))
        assertEquals(1.0, rotationDirection(350.0, 10.0))
        assertEquals(20.0, differenceDegrees(350.0, 10.0))
    }

    private companion object {
        const val TOLERANCE = 1e-9

        val LAB =
            mapOf(
                0xFF2156C4 to doubleArrayOf(39.520500621658904, 24.23854320795596, -62.19142857339984),
                0xFFB4744B to doubleArrayOf(54.764136138860465, 20.94694216814258, 32.83814439246069),
                0xFFE8D5B5 to doubleArrayOf(86.04668531929129, 1.5846875350908896, 18.219866638272684),
            )
    }
}
