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

    private companion object {
        val HUES = doubleArrayOf(0.0, 27.4, 90.0, 145.5, 200.0, 271.2, 330.0)
        val CHROMAS = doubleArrayOf(0.0, 16.0, 48.0, 120.0)
        val TONES = doubleArrayOf(0.0, 5.0, 30.0, 50.0, 70.0, 95.0, 100.0)

        // HUES × CHROMAS × TONES, in that nesting order.
        val EXPECTED =
            intArrayOf(
                0xFF000000.toInt(), 0xFF111111.toInt(), 0xFF474747.toInt(), 0xFF777777.toInt(), 0xFFABABAB.toInt(),
                0xFFF1F1F1.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF1F0A11.toInt(), 0xFF5A3F47.toInt(),
                0xFF8E6F77.toInt(), 0xFFC6A2AB.toInt(), 0xFFFFECF0.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
                0xFF2B0012.toInt(), 0xFF7B2949.toInt(), 0xFFB6587A.toInt(), 0xFFF48BAE.toInt(), 0xFFFFECF0.toInt(),
                0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF2B0012.toInt(), 0xFF8E004A.toInt(), 0xFFE7007D.toInt(),
                0xFFFF84AF.toInt(), 0xFFFFECF0.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF111111.toInt(),
                0xFF474747.toInt(), 0xFF777777.toInt(), 0xFFABABAB.toInt(), 0xFFF1F1F1.toInt(), 0xFFFFFFFF.toInt(),
                0xFF000000.toInt(), 0xFF200B08.toInt(), 0xFF5D3F3B.toInt(), 0xFF926F69.toInt(), 0xFFCAA29C.toInt(),
                0xFFFFEDEA.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF2D0000.toInt(), 0xFF7D2B21.toInt(),
                0xFFBB5A4B.toInt(), 0xFFFB8C7B.toInt(), 0xFFFFEDEA.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
                0xFF2D0000.toInt(), 0xFF930100.toInt(), 0xFFEF0000.toInt(), 0xFFFF8A78.toInt(), 0xFFFFEDEA.toInt(),
                0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF111111.toInt(), 0xFF474747.toInt(), 0xFF777777.toInt(),
                0xFFABABAB.toInt(), 0xFFF1F1F1.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF171000.toInt(),
                0xFF51462A.toInt(), 0xFF837656.toInt(), 0xFFB9AA87.toInt(), 0xFFFFEFCF.toInt(), 0xFFFFFFFF.toInt(),
                0xFF000000.toInt(), 0xFF171000.toInt(), 0xFF594400.toInt(), 0xFF937300.toInt(), 0xFFCFA62E.toInt(),
                0xFFFFEFCF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF171000.toInt(), 0xFF594400.toInt(),
                0xFF937300.toInt(), 0xFFD3A500.toInt(), 0xFFFFEFCF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
                0xFF111111.toInt(), 0xFF474747.toInt(), 0xFF777777.toInt(), 0xFFABABAB.toInt(), 0xFFF1F1F1.toInt(),
                0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF061406.toInt(), 0xFF3B4B38.toInt(), 0xFF6B7C66.toInt(),
                0xFF9FB099.toInt(), 0xFFE4F6DC.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF001502.toInt(),
                0xFF085314.toInt(), 0xFF428541.toInt(), 0xFF75BC70.toInt(), 0xFFC8FFBF.toInt(), 0xFFFFFFFF.toInt(),
                0xFF000000.toInt(), 0xFF001502.toInt(), 0xFF005311.toInt(), 0xFF008A23.toInt(), 0xFF00C637.toInt(),
                0xFFC8FFBF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF111111.toInt(), 0xFF474747.toInt(),
                0xFF777777.toInt(), 0xFFABABAB.toInt(), 0xFFF1F1F1.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
                0xFF001415.toInt(), 0xFF324B4C.toInt(), 0xFF627C7D.toInt(), 0xFF96B0B1.toInt(), 0xFFDAF6F7.toInt(),
                0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF001415.toInt(), 0xFF004F52.toInt(), 0xFF008488.toInt(),
                0xFF1DBDC2.toInt(), 0xFFB9FDFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF001415.toInt(),
                0xFF004F52.toInt(), 0xFF008488.toInt(), 0xFF00BEC3.toInt(), 0xFFB9FDFF.toInt(), 0xFFFFFFFF.toInt(),
                0xFF000000.toInt(), 0xFF111111.toInt(), 0xFF474747.toInt(), 0xFF777777.toInt(), 0xFFABABAB.toInt(),
                0xFFF1F1F1.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF0A1021.toInt(), 0xFF414659.toInt(),
                0xFF71768B.toInt(), 0xFFA5AAC1.toInt(), 0xFFEEF0FF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
                0xFF000E33.toInt(), 0xFF20428F.toInt(), 0xFF5674C4.toInt(), 0xFF8BA8FC.toInt(), 0xFFEEF0FF.toInt(),
                0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF000E33.toInt(), 0xFF003FA4.toInt(), 0xFF206DFF.toInt(),
                0xFF8AA8FF.toInt(), 0xFFEEF0FF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF111111.toInt(),
                0xFF474747.toInt(), 0xFF777777.toInt(), 0xFFABABAB.toInt(), 0xFFF1F1F1.toInt(), 0xFFFFFFFF.toInt(),
                0xFF000000.toInt(), 0xFF1A0C1A.toInt(), 0xFF534152.toInt(), 0xFF867183.toInt(), 0xFFBCA4B8.toInt(),
                0xFFFFEBFA.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF25002A.toInt(), 0xFF6C2D70.toInt(),
                0xFFA25EA5.toInt(), 0xFFDC91DC.toInt(), 0xFFFFEBFA.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
                0xFF25002A.toInt(), 0xFF7D008B.toInt(), 0xFFCD00E2.toInt(), 0xFFF779FF.toInt(), 0xFFFFEBFA.toInt(),
                0xFFFFFFFF.toInt(),
            )
    }
}
