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
import kotlin.math.min

/**
 * The full Material 3 Expressive type scale, 15 styles plus their 15 `*Emphasized` variants, set in
 * Google Sans Flex (`docs/DESIGN_SYSTEM.md` §3). Sizes, line heights and tracking are the Material 3
 * tokens; the variable axes are set per style. Built once per [dark] value and cached.
 */
fun pingogramTypography(dark: Boolean): Typography = if (dark) DarkTypography else LightTypography

private val LightTypography by lazy { buildTypography(dark = false) }
private val DarkTypography by lazy { buildTypography(dark = true) }

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
private val LabelSmall = Axes(weight = 500, round = 0f, width = 95f, fontFeatureSettings = "tnum")

private const val EMPHASIS_WEIGHT = 200
private const val MAX_WEIGHT = 800
private const val EMPHASIS_GRADE = 25

// GRAD −25 keeps light-on-dark text from blooming (§3.1). The bundled file's GRAD axis is 0..100,
// so the platform clamps negative grades to 0; DESIGN_SYSTEM.md §3.1 records the open question.
private const val DARK_GRADE = -25
private const val ITALIC_SLANT = -10f

private fun buildTypography(dark: Boolean): Typography {
    val tokens = Typography()
    val grade = if (dark) DARK_GRADE else 0

    fun TextStyle.base(axes: Axes) = withGoogleSansFlex(axes, grade, emphasized = false)

    fun TextStyle.emphasized(axes: Axes) = withGoogleSansFlex(axes, grade, emphasized = true)

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
    val fonts = weights.flatMap { listOf(instance.font(it, italic = false), instance.font(it, italic = true)) }
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
    fun font(
        weight: Int,
        italic: Boolean,
    ): Font =
        Font(
            resId = R.font.google_sans_flex,
            weight = FontWeight(weight),
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
