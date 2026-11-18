package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * A category color (`docs/DESIGN_SYSTEM.md` §2.2): a light [container] and its [content] in a deep shade of the same
 * hue in light theme, the other way round in dark theme, e.g. the circle behind a settings row's icon and the icon.
 */
@Immutable
data class CategoryColors(
    val container: Color,
    val content: Color,
)

/**
 * The category hues, in the order a page's groups take them ([CategoryPalette.get] by index), so neighbours differ.
 * [cam16Hue] is the hue before harmonizing, [chroma] what it asks for: as colorful as a tonal palette's key color,
 * teal less, as sRGB holds far more of it than of the others at the lightest tones and it would outshine them.
 */
enum class CategoryHue(
    internal val cam16Hue: Double,
    internal val chroma: Double = CATEGORY_CHROMA,
) {
    Pink(cam16Hue = 350.0),
    Green(cam16Hue = 145.0),
    Orange(cam16Hue = 55.0),
    Blue(cam16Hue = 265.0),
    Purple(cam16Hue = 305.0),
    Teal(cam16Hue = 190.0, chroma = TEAL_CHROMA),
}

/**
 * The category colors of the theme in use (`docs/DESIGN_SYSTEM.md` §2.2, owner decision 2026-11-18): each
 * [CategoryHue] harmonized toward `primary`, as `online` is, and generated at the tones of `primaryContainer` and
 * `onPrimaryContainer`, so every color theme, wallpaper colors, contrast level and AMOLED black get Material's
 * container tones and the content keeps the contrast `onPrimaryContainer` keeps on `primaryContainer`.
 */
@Immutable
class CategoryPalette internal constructor(
    private val pairs: List<CategoryColors>,
) {
    /** How many category colors there are. */
    val size: Int get() = pairs.size

    /** The colors of [hue]. */
    operator fun get(hue: CategoryHue): CategoryColors = pairs[hue.ordinal]

    /** The colors at [index] in [CategoryHue]'s order, from the start again after the last. */
    operator fun get(index: Int): CategoryColors = pairs[index.mod(pairs.size)]
}

/** The [CategoryPalette] of [colorScheme]. */
internal fun categoryPalette(colorScheme: ColorScheme): CategoryPalette =
    CategoryPalette(
        CategoryHue.entries.map { hue ->
            CategoryColors(
                container = categoryTone(hue, colorScheme.primaryContainer, colorScheme.primary),
                content = categoryTone(hue, colorScheme.onPrimaryContainer, colorScheme.primary),
            )
        },
    )

private fun categoryTone(
    hue: CategoryHue,
    toneOf: Color,
    primary: Color,
): Color = harmonizedHueAtTone(hue.cam16Hue, hue.chroma, toneOf, primary)

// The chroma a hue asks for; sRGB caps it at the lightest tones.
private const val CATEGORY_CHROMA = 40.0
private const val TEAL_CHROMA = 24.0
