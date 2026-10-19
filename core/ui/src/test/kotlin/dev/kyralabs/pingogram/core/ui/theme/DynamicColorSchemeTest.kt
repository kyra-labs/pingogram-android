package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import dev.kyralabs.pingogram.core.domain.model.Contrast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicColorSchemeTest {
    // Tonal palettes as a green wallpaper would give them: each a hue at every published tone.
    private val green = Color(0xFF3B8A3E)
    private val palettes =
        SystemPalettes(
            accent1 = palette(green),
            accent2 = palette(Color(0xFF52634F)),
            accent3 = palette(Color(0xFF39656B)),
            neutral2 = palette(Color(0xFF5D5F5A)),
        )

    @Test
    fun `uses the color roles when they match the palettes, keeping the system contrast`() {
        val fromPalettes = paletteColorScheme(palettes, dark = false)
        // High contrast darkens the role's tone, not its hue.
        val fromRoles = lightColorScheme(primary = green.withLightness(30f))

        val chosen = chooseDynamicColors(fromRoles, fromPalettes)

        assertSame(fromRoles, chosen.scheme)
        assertTrue(chosen.followsContrast)
    }

    @Test
    fun `uses the palettes when the color roles stayed at the stock blue, as on One UI`() {
        val fromPalettes = paletteColorScheme(palettes, dark = false)
        val stockBlueRoles = brandColorScheme(dark = false, Contrast.Standard)

        val chosen = chooseDynamicColors(stockBlueRoles, fromPalettes)

        assertSame(fromPalettes, chosen.scheme)
        assertFalse(chosen.followsContrast)
    }

    @Test
    fun `uses the palettes before Android 14, which has no color roles`() {
        val fromPalettes = paletteColorScheme(palettes, dark = true)

        val chosen = chooseDynamicColors(fromRoles = null, fromPalettes)

        assertSame(fromPalettes, chosen.scheme)
        assertFalse(chosen.followsContrast)
    }

    @Test
    fun `maps the palettes' tones to roles as Compose does on Android 12`() {
        val light = paletteColorScheme(palettes, dark = false)
        val dark = paletteColorScheme(palettes, dark = true)

        assertEquals(palettes.primary(40), light.primary)
        assertEquals(palettes.primary(90), light.primaryContainer)
        assertEquals(palettes.tertiary(40), light.tertiary)
        assertEquals(palettes.neutralVariant(10), light.onSurface)
        assertEquals(palettes.primary(80), dark.primary)
        assertEquals(palettes.secondary(30), dark.secondaryContainer)
        assertEquals(palettes.neutralVariant(90), dark.onSurface)
    }

    @Test
    fun `builds the surface tones the platform does not publish at their lightness, keeping the palette's hue`() {
        val surface = paletteColorScheme(palettes, dark = false).surface
        val darkLowest = paletteColorScheme(palettes, dark = true).surfaceContainerLowest

        assertEquals(98f, surface.lightness(), 0.5f)
        assertEquals(4f, darkLowest.lightness(), 0.5f)
        // Taken from a tinted tone, not from black, so the tint survives.
        assertTrue(sameHue(palettes.neutralVariant(10), darkLowest.withLightness(50f)))
    }

    @Test
    fun `compares hues, treating grays alike`() {
        assertTrue(sameHue(Color(0xFF1B6EF3), Color(0xFF001A41)))
        assertFalse(sameHue(Color(0xFF1B6EF3), green))
        assertTrue(sameHue(Color(0xFF777777), Color(0xFF222222)))
        assertFalse(sameHue(Color(0xFF777777), Color(0xFF1B6EF3)))
    }

    private fun palette(hue: Color): Map<Int, Color> =
        SystemPalettes.TONES.associateWith { tone ->
            when (tone) {
                0 -> Color.Black
                100 -> Color.White
                else -> hue.withLightness(tone.toFloat())
            }
        }

    private fun Color.lightness(): Float = convert(ColorSpaces.CieLab).component1()
}
