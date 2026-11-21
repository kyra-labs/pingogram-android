package dev.kyralabs.pingogram.core.ui.theme.mcu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * SchemeContent's palettes, contrast curves and foreground tones come out as material-color-utilities 0.4.0 makes
 * them; the expected values were printed by that library (`tools/theme`'s copy).
 */
class ContentSchemeTest {
    @Test
    fun `picks the same palettes as SchemeContent, tones 40 and 90 of each`() {
        assertPalettes(
            0xFF2156C4,
            0xFF2357C5, 0xFFDAE1FF, 0xFF4E5D88, 0xFFDAE1FF, 0xFF8B3BA2, 0xFFFCD7FF,
            0xFF5D5E66, 0xFFE2E2EB, 0xFF5B5E6B, 0xFFDFE2F2, 0xFFBA1A1A, 0xFFFFDAD6,
        )
        assertPalettes(
            0xFFB4744B,
            0xFF89502A, 0xFFFFDBC8, 0xFF765846, 0xFFFFDBC8, 0xFF606120, 0xFFE7E796,
            0xFF655D58, 0xFFEDE0DA, 0xFF6B5B52, 0xFFF4DED3, 0xFFBA1A1A, 0xFFFFDAD6,
        )
        assertPalettes(
            0xFFCDDC39,
            0xFF5B6300, 0xFFDDED49, 0xFF5D6227, 0xFFE2E89E, 0xFF016E0A, 0xFF98F987,
            0xFF5F5F52, 0xFFE5E3D2, 0xFF5F604B, 0xFFE4E4CA, 0xFFBA1A1A, 0xFFFFDAD6,
        )
    }

    @Test
    fun `finds the same foreground tones`() {
        assertEquals(86.47110254503178, foregroundTone(40.0, 4.5), 1e-9)
        assertEquals(42.46464336877511, foregroundTone(90.0, 4.5), 1e-9)
        assertEquals(0.0, foregroundTone(55.0, 7.0))
        assertEquals(0.0, foregroundTone(65.0, 21.0))
    }

    @Test
    fun `a contrast curve interpolates between its levels`() {
        val curve = ContrastCurve(1.0, 3.0, 4.5, 7.0)

        assertEquals(
            listOf(1.0, 2.0, 3.0, 3.75, 4.5, 5.75, 7.0),
            listOf(-1.0, -0.5, 0.0, 0.25, 0.5, 0.75, 1.0).map(curve::get),
        )
    }

    private fun assertPalettes(
        seed: Long,
        vararg expected: Long,
    ) {
        val scheme = ContentScheme(Hct.fromInt(seed.toInt()), isDark = false, contrastLevel = 0.0)
        val palettes =
            with(scheme) {
                listOf(primaryPalette, secondaryPalette, tertiaryPalette, neutralPalette, neutralVariantPalette, errorPalette)
            }
        assertEquals(
            expected.map { it.toInt() },
            palettes.flatMap { listOf(it.tone(40.0), it.tone(90.0)) },
            "palettes of ${seed.toString(16)}",
        )
    }
}
