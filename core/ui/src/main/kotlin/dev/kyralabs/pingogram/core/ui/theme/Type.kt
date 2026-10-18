package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import dev.kyralabs.pingogram.core.ui.R
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * The full Material 3 Expressive type scale, 15 styles plus their 15 `*Emphasized` variants, set in
 * Google Sans Flex (`docs/DESIGN_SYSTEM.md` §3). Sizes, line heights and tracking are the Material 3
 * tokens; the variable axes are set per style. Built once per [dark] and [fontWeightAdjustment] value
 * and cached.
 *
 * @param fontWeightAdjustment the system's "Bold text" adjustment (`Configuration.fontWeightAdjustment`
 *   on Android 12+, 300 while it is on), or 0. Compose resolves a style's font at its weight plus the
 *   adjustment and then adds the adjustment to that font's `wght` too, so each font is registered at its
 *   adjusted weight: Compose then picks the font with the style's own `wght`, and the text gets bolder by
 *   the adjustment once. Fonts registered at their plain weights would make it pick the heavier
 *   emphasized font and add the adjustment on top: `bodyLarge` at 900 instead of 700, and `titleMedium`
 *   at 1000, as heavy as `titleMediumEmphasized`.
 */
fun pingogramTypography(
    dark: Boolean,
    fontWeightAdjustment: Int = 0,
): Typography =
    Typographies.computeIfAbsent(TypographyKey(dark, fontWeightAdjustment)) {
        buildTypography(dark, fontWeightAdjustment)
    }

private data class TypographyKey(
    val dark: Boolean,
    val fontWeightAdjustment: Int,
)

private val Typographies = ConcurrentHashMap<TypographyKey, Typography>()

/** Per-category axes from §3.2. Emphasized styles add [EMPHASIS_WEIGHT] and [EMPHASIS_GRADE]. */
private class Axes(
    val weight: Int,
    val round: Float,
    val width: Float = 100f,
    val fontFeatureSettings: String? = null,
)

private val Display = Axes(weight = 400, round = 100f, width = 110f)
private val Headline = Axes(weight = 500, round = 100f)
private val Title = Axes(weight = 600, round = 100f)
private val Body = Axes(weight = 400, round = 0f)
private val Label = Axes(weight = 500, round = 0f)

// Timestamps, badges and counters (§3.3): narrower, with tabular figures.
private val LabelSmall = Axes(weight = 500, round = 0f, width = 95f, fontFeatureSettings = TABULAR_FIGURES)

private const val TABULAR_FIGURES = "tnum"

private const val EMPHASIS_WEIGHT = 200
private const val MAX_WEIGHT = 800
private const val EMPHASIS_GRADE = 25

// GRAD −25 keeps light-on-dark text from blooming (§3.1). The bundled file's GRAD axis is 0..100,
// so the platform clamps negative grades to 0; DESIGN_SYSTEM.md §3.1 records the open question.
private const val DARK_GRADE = -25
private const val ITALIC_SLANT = -10f

// The range of FontWeight.
private const val MIN_FONT_WEIGHT = 1
private const val MAX_FONT_WEIGHT = 1000

private fun buildTypography(
    dark: Boolean,
    fontWeightAdjustment: Int,
): Typography {
    val tokens = Typography()
    val grade = if (dark) DARK_GRADE else 0

    fun TextStyle.base(axes: Axes) = withGoogleSansFlex(axes, grade, fontWeightAdjustment, emphasized = false)

    fun TextStyle.emphasized(axes: Axes) = withGoogleSansFlex(axes, grade, fontWeightAdjustment, emphasized = true)

    return Typography(
        displayLarge = tokens.displayLarge.base(Display),
        displayMedium = tokens.displayMedium.base(Display),
        displaySmall = tokens.displaySmall.base(Display),
        headlineLarge = tokens.headlineLarge.base(Headline),
        headlineMedium = tokens.headlineMedium.base(Headline),
        headlineSmall = tokens.headlineSmall.base(Headline),
        titleLarge = tokens.titleLarge.base(Title),
        titleMedium = tokens.titleMedium.base(Title),
        titleSmall = tokens.titleSmall.base(Title),
        bodyLarge = tokens.bodyLarge.base(Body),
        bodyMedium = tokens.bodyMedium.base(Body),
        bodySmall = tokens.bodySmall.base(Body),
        labelLarge = tokens.labelLarge.base(Label),
        labelMedium = tokens.labelMedium.base(Label),
        labelSmall = tokens.labelSmall.base(LabelSmall),
        displayLargeEmphasized = tokens.displayLargeEmphasized.emphasized(Display),
        displayMediumEmphasized = tokens.displayMediumEmphasized.emphasized(Display),
        displaySmallEmphasized = tokens.displaySmallEmphasized.emphasized(Display),
        headlineLargeEmphasized = tokens.headlineLargeEmphasized.emphasized(Headline),
        headlineMediumEmphasized = tokens.headlineMediumEmphasized.emphasized(Headline),
        headlineSmallEmphasized = tokens.headlineSmallEmphasized.emphasized(Headline),
        titleLargeEmphasized = tokens.titleLargeEmphasized.emphasized(Title),
        titleMediumEmphasized = tokens.titleMediumEmphasized.emphasized(Title),
        titleSmallEmphasized = tokens.titleSmallEmphasized.emphasized(Title),
        bodyLargeEmphasized = tokens.bodyLargeEmphasized.emphasized(Body),
        bodyMediumEmphasized = tokens.bodyMediumEmphasized.emphasized(Body),
        bodySmallEmphasized = tokens.bodySmallEmphasized.emphasized(Body),
        labelLargeEmphasized = tokens.labelLargeEmphasized.emphasized(Label),
        labelMediumEmphasized = tokens.labelMediumEmphasized.emphasized(Label),
        labelSmallEmphasized = tokens.labelSmallEmphasized.emphasized(LabelSmall),
    )
}

private fun TextStyle.withGoogleSansFlex(
    axes: Axes,
    baseGrade: Int,
    fontWeightAdjustment: Int,
    emphasized: Boolean,
): TextStyle {
    val emphasizedWeight = min(axes.weight + EMPHASIS_WEIGHT, MAX_WEIGHT)
    val weight = if (emphasized) emphasizedWeight else axes.weight
    val instance =
        FontInstance(
            opticalSize = fontSize.value,
            round = axes.round,
            grade = if (emphasized) baseGrade + EMPHASIS_GRADE else baseGrade,
            width = axes.width,
        )
    // A bold span in a regular style resolves to the emphasized weight instead of a synthesized bold.
    val weights = if (emphasized) listOf(weight) else listOf(weight, emphasizedWeight)
    val fonts =
        weights
            .map { it to FontWeight((it + fontWeightAdjustment).coerceIn(MIN_FONT_WEIGHT, MAX_FONT_WEIGHT)) }
            // An extreme adjustment can push both to the end of the range; the style's own weight wins.
            .distinctBy { (_, registered) -> registered }
            .flatMap { (wght, registered) ->
                listOf(instance.font(wght, registered, italic = false), instance.font(wght, registered, italic = true))
            }
    return copy(
        fontFamily = FontFamily(fonts),
        fontWeight = FontWeight(weight),
        fontFeatureSettings = axes.fontFeatureSettings,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}

/**
 * Variation settings shared by every font of one style's family. Each distinct setting becomes its
 * own Typeface when first used, so a family holds only the weights its style needs, upright and with
 * a true variable slant for italics. Glyphs the font lacks (CJK, Arabic, Devanagari, emoji…) fall back
 * to the system fonts automatically.
 */
private class FontInstance(
    val opticalSize: Float,
    val round: Float,
    val grade: Int,
    val width: Float,
) {
    /** The font at `wght` [weight], registered in its family at [registeredWeight] (see [pingogramTypography]). */
    fun font(
        weight: Int,
        registeredWeight: FontWeight,
        italic: Boolean,
    ): Font =
        Font(
            resId = R.font.google_sans_flex,
            weight = registeredWeight,
            style = if (italic) FontStyle.Italic else FontStyle.Normal,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(weight),
                    // opsz must equal the size in sp (§3.1): Android does not apply optical sizing on its own.
                    FontVariation.Setting("opsz", opticalSize),
                    FontVariation.Setting("ROND", round),
                    FontVariation.grade(grade),
                    FontVariation.width(width),
                    FontVariation.slant(if (italic) ITALIC_SLANT else 0f),
                ),
        )
}
