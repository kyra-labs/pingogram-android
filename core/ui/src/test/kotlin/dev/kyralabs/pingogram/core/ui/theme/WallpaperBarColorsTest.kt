package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.max
import kotlin.math.min

/**
 * A conversation's top bar over its wallpaper (`docs/DESIGN_SYSTEM.md` §13.5, owner decision 2026-11-22): it follows
 * the theme, its scheme's `surface` washed over the blurred wallpaper, and its text (title, subtitle, the primary
 * "typing…") keeps 4.5:1 whatever the brightness of the top, light and dark, for wallpaper schemes and the app's own,
 * at every contrast level.
 */
class WallpaperBarColorsTest {
    @ParameterizedTest
    @ValueSource(floats = [0f, 0.05f, 0.18f, 0.29f, 0.3f, 0.5f, 0.8f, 1f])
    fun `the bar's text keeps 4,5 to 1 on a top of any brightness, in either theme`(luminance: Float) {
        for (dark in listOf(false, true)) {
            val bar = wallpaperBarColors(dark)
            for (contrast in Contrast.entries) {
                val schemes =
                    SEEDS.map { contentColorScheme(Color(it), dark, contrast) } +
                        themeColorScheme(ThemePalette.Pingogram, dark, contrast)
                for (scheme in schemes) {
                    check(
                        scheme,
                        bar,
                        greyOf(luminance),
                        "luminance $luminance dark=$dark $contrast",
                    )
                }
            }
        }
    }

    @Test
    fun `dark theme's wash stays mid-dark over a white top, as the reference does`() {
        val scheme = contentColorScheme(Color(SEEDS.first()), dark = true, Contrast.Standard)
        val washed = scheme.surface.copy(alpha = wallpaperBarColors(dark = true).washAlpha).compositeOver(Color.White)
        assertTrue(washed.luminance() < MID_DARK, "washed white ${washed.luminance()}")
    }

    private fun check(
        scheme: ColorScheme,
        bar: WallpaperBarColors,
        top: Color,
        look: String,
    ) {
        val washed = scheme.surface.copy(alpha = bar.washAlpha).compositeOver(top)
        for ((name, text) in mapOf(
            "title" to scheme.onSurface,
            "subtitle" to scheme.onSurfaceVariant,
            "typing" to scheme.primary,
        )) {
            assertTrue(ratio(text, washed) >= TEXT, "$look $name ${scheme.primary}: ${ratio(text, washed)}")
        }
    }

    /** The grey of relative luminance [luminance]. */
    private fun greyOf(luminance: Float): Color {
        var low = 0f
        var high = 1f
        repeat(BISECTIONS) {
            val mid = (low + high) / 2
            if (Color(mid, mid, mid).luminance() < luminance) low = mid else high = mid
        }
        return Color(low, low, low)
    }

    private fun ratio(
        first: Color,
        second: Color,
    ): Float {
        val lighter = max(first.luminance(), second.luminance())
        val darker = min(first.luminance(), second.luminance())
        return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
    }

    private companion object {
        const val TEXT = 4.5f
        const val LUMINANCE_OFFSET = 0.05f
        const val BISECTIONS = 24

        /** The luminance of L* 45, the reference bar's tone over its brightest wallpaper. */
        const val MID_DARK = 0.15f
        val SEEDS =
            listOf(0xFFB4744B.toInt(), 0xFF2156C4.toInt(), 0xFF4CAF50.toInt(), 0xFFCDDC39.toInt(), 0xFFE91E63.toInt())
    }
}
