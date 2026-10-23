package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.math.max
import kotlin.math.min

/**
 * The contrast every color theme keeps, at every contrast level, light, dark and AMOLED black (`REQUIREMENTS.md` §6.3,
 * `docs/DESIGN_SYSTEM.md` §9, `docs/audit/accessibility.md`): 4.5:1 for text on the containers the app puts it on, and
 * 3:1 for the outlines and icons that show a control's bounds or state. The wallpaper's colors are the platform's and
 * are not checked here.
 */
class ThemeContrastTest {
    @ParameterizedTest
    @EnumSource(ThemePalette::class)
    fun `text keeps 4,5 to 1 on its containers in every contrast level and look`(palette: ThemePalette) {
        forEachScheme(palette) { look, scheme ->
            for ((name, pair) in textPairs(scheme)) {
                val ratio = contrastRatio(pair.first, pair.second)
                assertTrue(ratio >= TEXT, "$palette $look $name: $ratio")
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ThemePalette::class)
    fun `outlines and icons keep 3 to 1 in every contrast level and look`(palette: ThemePalette) {
        forEachScheme(palette) { look, scheme ->
            for ((name, pair) in nonTextPairs(scheme)) {
                val ratio = contrastRatio(pair.first, pair.second)
                assertTrue(ratio >= NON_TEXT, "$palette $look $name: $ratio")
            }
        }
    }

    private fun forEachScheme(
        palette: ThemePalette,
        check: (String, ColorScheme) -> Unit,
    ) {
        for (contrast in Contrast.entries) {
            check("light $contrast", themeColorScheme(palette, dark = false, contrast))
            val dark = themeColorScheme(palette, dark = true, contrast)
            check("dark $contrast", dark)
            check("AMOLED $contrast", dark.withAmoledSurfaces())
        }
    }

    /** Text and the containers it sits on in the app (`docs/DESIGN_SYSTEM.md` §2.2). */
    private fun textPairs(scheme: ColorScheme) =
        mapOf(
            "onSurface on surface" to (scheme.onSurface to scheme.surface),
            "onSurfaceVariant on surface" to (scheme.onSurfaceVariant to scheme.surface),
            // Settings and profile pages: the page, its headers and the rows on it.
            "onSurfaceVariant on surfaceContainerHigh" to (scheme.onSurfaceVariant to scheme.surfaceContainerHigh),
            "primary on surfaceContainerHigh" to (scheme.primary to scheme.surfaceContainerHigh),
            "onSurfaceVariant on surfaceContainer" to (scheme.onSurfaceVariant to scheme.surfaceContainer),
            "onSurface on surfaceContainerHighest" to (scheme.onSurface to scheme.surfaceContainerHighest),
            "onSurfaceVariant on surfaceContainerHighest" to
                (scheme.onSurfaceVariant to scheme.surfaceContainerHighest),
            "primary on surface" to (scheme.primary to scheme.surface),
            "error on surface" to (scheme.error to scheme.surface),
            "onPrimary on primary" to (scheme.onPrimary to scheme.primary),
            "onPrimaryContainer on primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
            "onSecondaryContainer on secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
            "onTertiaryContainer on tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
            "onErrorContainer on errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
        )

    /** The outlines of controls (text fields, unselected radio buttons, switches' tracks) and their icons. */
    private fun nonTextPairs(scheme: ColorScheme) =
        mapOf(
            "outline on surface" to (scheme.outline to scheme.surface),
            "outline on surfaceContainerHigh" to (scheme.outline to scheme.surfaceContainerHigh),
            "primary on surfaceContainer" to (scheme.primary to scheme.surfaceContainer),
        )

    private fun contrastRatio(
        first: Color,
        second: Color,
    ): Float {
        val lighter = max(first.luminance(), second.luminance())
        val darker = min(first.luminance(), second.luminance())
        return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
    }

    private companion object {
        const val TEXT = 4.5f
        const val NON_TEXT = 3f
        const val LUMINANCE_OFFSET = 0.05f
    }
}
