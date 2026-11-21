package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.max
import kotlin.math.min

/**
 * A conversation's top bar over its wallpaper (`docs/DESIGN_SYSTEM.md` §13.5): in the scheme of its top's polarity,
 * its text (title, subtitle, the primary "typing…") keeps 4.5:1 on the washed wallpaper whatever the brightness of the
 * top, for wallpaper schemes and the app's own, at every contrast level; the status bar's icons follow the polarity.
 */
class WallpaperBarColorsTest {
    @ParameterizedTest
    @ValueSource(floats = [0f, 0.05f, 0.18f, 0.29f, 0.3f, 0.5f, 0.8f, 1f])
    fun `the bar's text keeps 4,5 to 1 on a top of any brightness`(luminance: Float) {
        val bar = wallpaperBarColors(luminance)
        for (contrast in Contrast.entries) {
            val schemes =
                SEEDS.map { contentColorScheme(Color(it), bar.darkTop, contrast) } +
                    themeColorScheme(ThemePalette.Pingogram, bar.darkTop, contrast)
            for (scheme in schemes) check(scheme, bar, greyOf(luminance), "luminance $luminance $contrast")
        }
    }

    @Test
    fun `a light top takes dark icons, a dark one light icons`() {
        assertEquals(true, wallpaperBarColors(0.8f).darkStatusBarIcons)
        assertEquals(false, wallpaperBarColors(0.05f).darkStatusBarIcons)
    }

    private fun check(
        scheme: ColorScheme,
        bar: WallpaperBarColors,
        top: Color,
        look: String,
    ) {
        val washed = scheme.surfaceContainer.copy(alpha = bar.washAlpha).compositeOver(top)
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
        val SEEDS =
            listOf(0xFFB4744B.toInt(), 0xFF2156C4.toInt(), 0xFF4CAF50.toInt(), 0xFFCDDC39.toInt(), 0xFFE91E63.toInt())
    }
}
