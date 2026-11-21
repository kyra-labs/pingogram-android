package dev.kyralabs.pingogram.core.ui.theme.mcu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The ported Score, tonal palettes and dislike analyzer give what material-color-utilities 0.4.0 gives; the expected
 * colors were printed by that library (`tools/theme`'s copy), the test image's clusters as in [QuantizerCelebiTest].
 */
class ScoreTest {
    @Test
    fun `ranks the test image's colors as material-color-utilities does`() {
        val clusters = QuantizerCelebi.quantize(quantizerTestImage(), 128)

        assertEquals(
            listOf(0xFF1FF526.toInt(), 0xFFD622F1.toInt(), 0xFFA8D3E8.toInt(), 0xFF7A675A.toInt()),
            Score.score(clusters),
        )
        assertEquals(listOf(0xFF1FF526.toInt()), Score.score(clusters, desired = 1))
    }

    @Test
    fun `prefers covering hues and a chroma near 48, each hue once`() {
        val colors =
            linkedMapOf(
                0xFFB4744B.toInt() to 500,
                0xFF2156C4.toInt() to 300,
                0xFF4CAF50.toInt() to 200,
                0xFFE91E63.toInt() to 20,
                0xFFFFEB3B.toInt() to 400,
            )

        assertEquals(
            listOf(0xFFFFEB3B.toInt(), 0xFFB4744B.toInt(), 0xFF2156C4.toInt(), 0xFFE91E63.toInt()),
            Score.score(colors),
        )
    }

    @Test
    fun `greys alone give Google Blue, unless the filter is off`() {
        val greys = linkedMapOf(0xFF808080.toInt() to 100, 0xFF7F7F80.toInt() to 50)

        assertEquals(listOf(0xFF4285F4.toInt()), Score.score(greys))
        assertEquals(
            listOf(0xFF808080.toInt(), 0xFF2156C4.toInt()),
            Score.score(linkedMapOf(0xFF808080.toInt() to 100, 0xFF2156C4.toInt() to 5), filter = false),
        )
    }

    @Test
    fun `tonal palettes give the same tones`() {
        assertTones(
            0xFF2156C4.toInt(),
            0xFF000000, 0xFF001849, 0xFF2357C5, 0xFFB3C5FF, 0xFFDAE1FF, 0xFFFEFBFF, 0xFFFFFFFF,
        )
        assertTones(
            0xFFB4744B.toInt(),
            0xFF000000, 0xFF321300, 0xFF89502A, 0xFFFFB68A, 0xFFFFDBC8, 0xFFFFFBFF, 0xFFFFFFFF,
        )
        assertTones(
            0xFFCDDC39.toInt(),
            0xFF000000, 0xFF1A1D00, 0xFF5B6300, 0xFFC1D02C, 0xFFDDED49, 0xFFFCFFDA, 0xFFFFFFFF,
        )
    }

    @Test
    fun `a palette from a hue and chroma keys on that chroma nearest tone 50, yellow's tone 99 averaged`() {
        val yellow = TonalPalette.fromHueAndChroma(110.0, 40.0)
        assertEquals(0xFF7C7A24.toInt(), yellow.keyColor.toInt())
        assertEquals(listOf(0xFFFFFCC8, 0xFFFFFEE4, 0xFFFFFFFF).map { it.toInt() }, listOf(98.0, 99.0, 100.0).map(yellow::tone))

        val blueGrey = TonalPalette.fromHueAndChroma(271.2, 16.0).keyColor
        assertEquals(0xFF71768B.toInt(), blueGrey.toInt())
        assertEquals(49.895926204447335, blueGrey.tone, 1e-9)
        assertEquals(0xFFFE0000.toInt(), TonalPalette.fromHueAndChroma(27.4, 120.0).keyColor.toInt())
    }

    @Test
    fun `dark yellow-greens are lightened to tone 70`() {
        val darkYellowGreen = Hct.from(100.0, 40.0, 50.0)

        assertEquals(true, DislikeAnalyzer.isDisliked(darkYellowGreen))
        assertEquals(0xFFBEAB4C.toInt(), DislikeAnalyzer.fixIfDisliked(darkYellowGreen).toInt())
        assertEquals(false, DislikeAnalyzer.isDisliked(Hct.from(100.0, 40.0, 70.0)))
    }

    private fun assertTones(
        seed: Int,
        vararg expected: Long,
    ) {
        val palette = TonalPalette.fromInt(seed)
        assertEquals(
            expected.map { it.toInt() },
            TONES.map(palette::tone),
            "palette of ${seed.toUInt().toString(16)}",
        )
    }

    private companion object {
        val TONES = listOf(0.0, 10.0, 40.0, 80.0, 90.0, 99.0, 100.0)
    }
}
