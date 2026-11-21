package dev.kyralabs.pingogram.core.ui.theme.mcu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The ported HCT and CAM16 give what material-color-utilities 0.4.0 gives: every expected value below was printed by
 * that library (`tools/theme`'s copy) for the same input.
 */
class HctTest {
    @Test
    fun `reads hue, chroma and tone as material-color-utilities does`() {
        for ((argb, expected) in HCT) {
            val hct = Hct.fromInt(argb.toInt())
            assertEquals(expected[0], hct.hue, TOLERANCE, "hue of ${argb.toString(16)}")
            assertEquals(expected[1], hct.chroma, TOLERANCE, "chroma of ${argb.toString(16)}")
            assertEquals(expected[2], hct.tone, TOLERANCE, "tone of ${argb.toString(16)}")
        }
    }

    @Test
    fun `CAM16 gives the same lightness J`() {
        assertEquals(46.445185455166062, Cam16.fromInt(0xFFFF0000.toInt()).j, TOLERANCE)
        assertEquals(30.283399083257812, Cam16.fromInt(0xFF2156C4.toInt()).j, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9

        val HCT =
            mapOf(
                0xFFFF0000 to doubleArrayOf(27.408225137158738, 113.35788739379785, 53.23288178584245),
                0xFF00FF00 to doubleArrayOf(142.13989350764564, 108.41006052028888, 87.73703347354422),
                0xFF0000FF to doubleArrayOf(282.78817956187277, 87.23069368032539, 32.302586667249486),
                0xFFFFFFFF to doubleArrayOf(209.49195947383808, 2.8690352036774005, 100.0),
                0xFF000000 to doubleArrayOf(0.0, 0.0, 0.0),
                0xFF2156C4 to doubleArrayOf(271.17160043078894, 60.81290027229617, 39.520500621658904),
                0xFFB4744B to doubleArrayOf(50.6018759354916, 33.36111801318559, 54.764136138860465),
                0xFF7F7F7F to doubleArrayOf(209.4938403937148, 1.8871872187205339, 53.19277745493915),
                0xFFE8D5B5 to doubleArrayOf(84.64117485909597, 14.123892212611944, 86.04668531929129),
            )
    }
}
