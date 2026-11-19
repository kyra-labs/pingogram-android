package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cbrt

/**
 * The category colors' tones (`docs/DESIGN_SYSTEM.md` §2.2, owner decision 2026-11-19): containers at tone 90 in light
 * theme and 80 in dark, the icon on them at tone 30 (20 with high contrast) in the hue's full chroma, not a grey.
 */
class CategoryColorsTest {
    private val light = themeColorScheme(ThemePalette.Pingogram, dark = false, Contrast.Standard)
    private val dark = themeColorScheme(ThemePalette.Pingogram, dark = true, Contrast.Standard)

    @Test
    fun `light theme takes containers at tone 90 and icons at tone 30`() {
        val categories = categoryPalette(light, dark = false)
        for (hue in CategoryHue.entries) {
            assertTone(LIGHT_CONTAINER, categories[hue].container, "$hue container")
            assertTone(CONTENT, categories[hue].content, "$hue icon")
        }
    }

    @Test
    fun `dark theme and AMOLED black take bright containers at tone 80 with the same dark icons`() {
        for (scheme in listOf(dark, dark.withAmoledSurfaces())) {
            val categories = categoryPalette(scheme, dark = true)
            for (hue in CategoryHue.entries) {
                assertTone(DARK_CONTAINER, categories[hue].container, "$hue container")
                assertTone(CONTENT, categories[hue].content, "$hue icon")
            }
        }
    }

    @Test
    fun `high contrast keeps the containers and takes the icons to tone 20`() {
        val standard = categoryPalette(light, dark = false)
        val high = categoryPalette(light, dark = false, Contrast.High)
        for (hue in CategoryHue.entries) {
            assertEquals(standard[hue].container, high[hue].container, "$hue container")
            assertTone(HIGH_CONTRAST_CONTENT, high[hue].content, "$hue icon")
        }
    }

    @Test
    fun `the icons are colorful, not grey`() {
        val categories = categoryPalette(light, dark = false)
        for (hue in CategoryHue.entries) {
            val icon = categories[hue].content
            val spread = maxOf(icon.red, icon.green, icon.blue) - minOf(icon.red, icon.green, icon.blue)
            assertTrue(spread >= MIN_CHANNEL_SPREAD, "$hue icon $icon")
        }
    }

    private fun assertTone(
        expected: Double,
        color: Color,
        what: String,
    ) {
        val y = color.luminance().toDouble()
        val tone = if (y > LAB_E) 116 * cbrt(y) - 16 else LAB_KAPPA * y
        assertEquals(expected, tone, TONE_TOLERANCE, what)
    }

    private companion object {
        const val LIGHT_CONTAINER = 90.0
        const val DARK_CONTAINER = 80.0
        const val CONTENT = 30.0
        const val HIGH_CONTRAST_CONTENT = 20.0
        const val TONE_TOLERANCE = 1.0
        const val LAB_E = 216.0 / 24389.0
        const val LAB_KAPPA = 24389.0 / 27.0

        // The reference's tone 30 icons (#8d0053, #004d68, #753403) spread their channels by 0.45 and more.
        const val MIN_CHANNEL_SPREAD = 0.3f
    }
}
