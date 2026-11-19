package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.kyralabs.pingogram.core.domain.model.Contrast

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
 * [cam16Hue] is the hue before harmonizing, [chroma] what its container asks for: as colorful as a tonal palette's key
 * color, teal less, as sRGB holds far more of it than of the others at the lightest tones and it would outshine them.
 * The content takes as much chroma as sRGB holds at its tone.
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
 * The category colors of the theme in use (`docs/DESIGN_SYSTEM.md` §2.2, owner decisions 2026-11-18 and 2026-11-19):
 * each [CategoryHue] harmonized toward `primary`, as `online` is: a container at tone 90 in light theme and 80 in dark
 * theme and AMOLED black, bright in both, and its content at tone 30 in both (20 with high contrast) in the hue's full
 * chroma, so the icon reads as the deep shade of the circle's color rather than a grey.
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

/** The [CategoryPalette] of [colorScheme], [dark] or not, at [contrast]. */
internal fun categoryPalette(
    colorScheme: ColorScheme,
    dark: Boolean,
    contrast: Contrast = Contrast.Standard,
): CategoryPalette {
    val containerTone = if (dark) DARK_CONTAINER_TONE else LIGHT_CONTAINER_TONE
    val contentTone = if (contrast == Contrast.High) HIGH_CONTRAST_CONTENT_TONE else CONTENT_TONE
    return CategoryPalette(
        CategoryHue.entries.map { hue ->
            CategoryColors(
                container = harmonizedHueAtTone(hue.cam16Hue, hue.chroma, containerTone, colorScheme.primary),
                content = harmonizedHueAtTone(hue.cam16Hue, FULL_CHROMA, contentTone, colorScheme.primary),
            )
        },
    )
}

// The chroma a hue's container asks for; sRGB caps it at the lightest tones.
private const val CATEGORY_CHROMA = 40.0
private const val TEAL_CHROMA = 24.0

// More than sRGB holds at any tone, so the content takes all it can.
private const val FULL_CHROMA = 150.0

// The tones (HCT) of the containers and their content (owner decision 2026-11-19).
private const val LIGHT_CONTAINER_TONE = 90.0
private const val DARK_CONTAINER_TONE = 80.0
private const val CONTENT_TONE = 30.0
private const val HIGH_CONTRAST_CONTENT_TONE = 20.0
