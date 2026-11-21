package dev.kyralabs.pingogram.core.ui.theme.mcu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The ported temperature cache and contrast give what material-color-utilities 0.4.0 gives; the expected values were
 * printed by that library (`tools/theme`'s copy).
 */
class TemperatureContrastTest {
    @Test
    fun `finds the same analogous colors, three of six as SchemeContent asks`() {
        assertAnalogous(0xFF2156C4, 3, 6, 0xFF006D24, 0xFF2156C4, 0xFF8A3AA1)
        assertAnalogous(0xFFB4744B, 3, 6, 0xFFB27087, 0xFFB4744B, 0xFF868741)
        assertAnalogous(0xFF4CAF50, 3, 6, 0xFF9BA200, 0xFF4CAF50, 0xFF00A4EC)
        assertAnalogous(0xFFFF0000, 3, 6, 0xFFF60082, 0xFFFF0000, 0xFFAF7200)
    }

    @Test
    fun `finds the same analogous colors, five of twelve by default`() {
        assertAnalogous(0xFF2156C4, 5, 12, 0xFF006D24, 0xFF006961, 0xFF2156C4, 0xFF6B45BB, 0xFF8A3AA1)
        assertAnalogous(0xFFFF0000, 5, 12, 0xFFF60082, 0xFFFC004C, 0xFFFF0000, 0xFFD95500, 0xFFAF7200)
    }

    @Test
    fun `measures contrast and finds tones that reach a ratio`() {
        assertEquals(21.0, Contrast.ratioOfTones(0.0, 100.0), TOLERANCE)
        assertEquals(5.002983511839433, Contrast.ratioOfTones(40.0, 90.0), TOLERANCE)
        assertEquals(21.0, Contrast.ratioOfTones(120.0, -3.0), TOLERANCE)
        assertEquals(86.47110254503178, Contrast.lighter(40.0, 4.5), TOLERANCE)
        assertEquals(-1.0, Contrast.lighter(90.0, 4.5))
        assertEquals(-1.0, Contrast.lighter(-1.0, 3.0))
        assertEquals(42.46464336877511, Contrast.darker(90.0, 4.5), TOLERANCE)
        assertEquals(-1.0, Contrast.darker(20.0, 7.0))
        assertEquals(-1.0, Contrast.darker(101.0, 3.0))
        assertEquals(100.0, Contrast.lighterUnsafe(90.0, 4.5))
        assertEquals(0.0, Contrast.darkerUnsafe(20.0, 7.0))
        assertEquals(85.01545768082842, Contrast.lighterUnsafe(50.0, 3.0), TOLERANCE)
    }

    private fun assertAnalogous(
        seed: Long,
        count: Int,
        divisions: Int,
        vararg expected: Long,
    ) {
        assertEquals(
            expected.map { it.toInt() },
            TemperatureCache(Hct.fromInt(seed.toInt())).analogous(count, divisions).map { it.toInt() },
            "analogous colors of ${seed.toString(16)}",
        )
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
