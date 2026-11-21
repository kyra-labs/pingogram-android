package dev.kyralabs.pingogram.core.ui.theme.mcu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The ported HCT solver finds the same sRGB color as material-color-utilities 0.4.0 for a grid of hues, chromas and
 * tones, in gamut (Newton's method on J) and beyond it (the bisection to the gamut's edge). The expected colors were
 * printed by that library (`tools/theme`'s copy).
 */
class HctSolverTest {
    @Test
    fun `solves every hue, chroma and tone of the grid as material-color-utilities does`() {
        var index = 0
        for (hue in HUES) {
            for (chroma in CHROMAS) {
                for (tone in TONES) {
                    val expected = EXPECTED[index++]
                    val actual = Hct.from(hue, chroma, tone).toInt()
                    assertEquals(expected, actual, "HCT($hue, $chroma, $tone)")
                }
            }
        }
    }

    @Test
    fun `a solved color reads back at its tone and, in gamut, its hue and chroma`() {
        val hct = Hct.from(271.2, 48.0, 50.0)
        assertEquals(50.0, hct.tone, 0.5)
        assertEquals(271.2, hct.hue, 1.0)
        assertEquals(48.0, hct.chroma, 1.0)
    }

    @Test
    fun `computes upstream's critical planes exactly`() {
        assertEquals(UPSTREAM_CRITICAL_PLANES.toList(), CRITICAL_PLANES.toList())
    }

    private companion object {
        val HUES = doubleArrayOf(0.0, 27.4, 90.0, 145.5, 200.0, 271.2, 330.0)
        val CHROMAS = doubleArrayOf(0.0, 16.0, 48.0, 120.0)
        val TONES = doubleArrayOf(0.0, 5.0, 30.0, 50.0, 70.0, 95.0, 100.0)

        // HUES × CHROMAS × TONES, in that nesting order.
        val EXPECTED: IntArray = mcuTable("hct_solver.txt").map { it.toLong(16).toInt() }.toIntArray()

        val UPSTREAM_CRITICAL_PLANES: DoubleArray =
            mcuTable(
                "critical_planes.txt",
            ).map { it.toDouble() }.toDoubleArray()

        fun mcuTable(name: String): List<String> =
            requireNotNull(HctSolverTest::class.java.getResource("/mcu/$name")) { "missing $name" }
                .readText()
                .lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
    }
}
