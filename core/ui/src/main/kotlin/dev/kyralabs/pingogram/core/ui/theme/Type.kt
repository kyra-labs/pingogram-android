package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.kyralabs.pingogram.core.domain.model.Contrast
import java.util.concurrent.ConcurrentHashMap

/**
 * The full Material 3 Expressive type scale, 15 styles plus their 15 `*Emphasized` variants, set in Google Sans Flex
 * the way Google sets it on Android (`docs/DESIGN_SYSTEM.md` §3): the Material 3 token sizes, line heights and
 * weights, the tracking Google uses with this font, and per style the variable axes. The same in light and dark
 * themes (grade 0, as Google's own styles); with high [contrast] the text is a grade heavier ([HIGH_CONTRAST_GRADE]).
 * Built once per [fontWeightAdjustment] and grade and cached, so standard and medium contrast share one typography.
 *
 * @param fontWeightAdjustment the system's "Bold text" adjustment (`Configuration.fontWeightAdjustment`
 *   on Android 12+, 300 while it is on), or 0. Compose resolves a style's font at its weight plus the
 *   adjustment and then adds the adjustment to that font's `wght` too, so each font is registered at its
 *   adjusted weight: Compose then picks the font with the style's own `wght`, and the text gets bolder by
 *   the adjustment once. Fonts registered at their plain weights would make it pick the heavier
 *   emphasized font and add the adjustment on top: `bodyLarge` at 800 instead of 700, and `titleMedium`
 *   at 900, as heavy as `titleMediumEmphasized`.
 * @param contrast the contrast level in effect (the system's on Android 14+). [Contrast.High] sets every style at
 *   `GRAD` [HIGH_CONTRAST_GRADE] (`docs/DESIGN_SYSTEM.md` §3.1, §10): the strokes thicken without the glyphs growing
 *   wider, so nothing reflows. Medium and standard contrast keep grade 0, as Material changes only colors there.
 */
fun pingogramTypography(
    fontWeightAdjustment: Int = 0,
    contrast: Contrast = Contrast.Standard,
): Typography {
    val key = TypographyKey(fontWeightAdjustment, grade = if (contrast == Contrast.High) HIGH_CONTRAST_GRADE else GRADE)
    return Typographies.computeIfAbsent(key) { buildTypography(it.fontWeightAdjustment, it.grade) }
}

/**
 * Message text at [scale] times `bodyLarge` (the Chat appearance setting, `docs/DESIGN_SYSTEM.md` §3.2): its size and
 * line height scaled and its optical size re-derived from the new size, otherwise [pingogramTypography]'s
 * `bodyLarge`; [scale] 1 is that style itself. Cached per scale, adjustment and grade, like the typography.
 */
fun pingogramMessageText(
    scale: Float,
    fontWeightAdjustment: Int = 0,
    contrast: Contrast = Contrast.Standard,
): TextStyle {
    val typography = pingogramTypography(fontWeightAdjustment, contrast)
    if (scale == 1f) return typography.bodyLarge
    val grade = if (contrast == Contrast.High) HIGH_CONTRAST_GRADE else GRADE
    return MessageTexts.computeIfAbsent(MessageTextKey(scale, fontWeightAdjustment, grade)) { key ->
        val token = Typography().bodyLarge
        token
            .copy(fontSize = token.fontSize * key.scale, lineHeight = token.lineHeight * key.scale)
            .withGoogleSansFlex(NoTracking, null, key.fontWeightAdjustment, key.grade, emphasized = false)
    }
}

private data class MessageTextKey(
    val scale: Float,
    val fontWeightAdjustment: Int,
    val grade: Int,
)

private val MessageTexts = ConcurrentHashMap<MessageTextKey, TextStyle>()

/** What a [Typography] is built from: the "Bold text" adjustment and the grade. */
private data class TypographyKey(
    val fontWeightAdjustment: Int,
    val grade: Int,
)

private val Typographies = ConcurrentHashMap<TypographyKey, Typography>()

/**
 * Verification codes (`docs/DESIGN_SYSTEM.md` §3.3): [Typography.headlineSmall] with tabular figures, centered, and
 * tracked a little wider ([CodeTracking]) so each digit reads on its own, as one-time code fields set them.
 */
val Typography.verificationCode: TextStyle
    get() = headlineSmall.merge(VerificationCodeStyle)

// A tenth of the size between digits: enough to tell them apart, little enough to read the code as one.
private val CodeTracking = 0.1.em

private val VerificationCodeStyle =
    TextStyle(textAlign = TextAlign.Center, letterSpacing = CodeTracking, fontFeatureSettings = TABULAR_FIGURES)

// Google's tracking for Google Sans Flex (Android's SystemUI type scale): none, except a little in the smallest styles.
// The Material 3 tokens' tracking (up to 0.5 sp) is Roboto's; Google Sans Flex spaces small sizes through its optical
// size axis already, and both together set text far wider than Material intends.
private val NoTracking = 0.sp
private val SmallTracking = 0.1.sp

// Timestamps, badges and counters (§3.3) use labelSmall: tabular figures, so digits do not shift as they change.
private const val TABULAR_FIGURES = "tnum"

// Emphasized styles are one step heavier and rounded, as Google's own Google Sans Flex styles on Android.
private const val EMPHASIS_WEIGHT = 100
private const val EMPHASIS_ROUND = 100f

// Grade 0 in both themes (§3.1), as Google's own styles: the bundled file's GRAD axis is 0..100, so a negative dark
// grade would be clamped to 0 anyway.
private const val GRADE = 0

// High contrast only (§10): Material's optional +25, within the bundled file's 0..100.
internal const val HIGH_CONTRAST_GRADE = 25
private const val ITALIC_SLANT = -10f
private const val NORMAL_WIDTH = 100f

// The range of FontWeight.
private const val MIN_FONT_WEIGHT = 1
private const val MAX_FONT_WEIGHT = 1000

private fun buildTypography(
    fontWeightAdjustment: Int,
    grade: Int,
): Typography {
    val tokens = Typography()

    fun TextStyle.base(
        tracking: TextUnit = NoTracking,
        features: String? = null,
    ) = withGoogleSansFlex(tracking, features, fontWeightAdjustment, grade, emphasized = false)

    // From the base style's token: the emphasized tokens share its size and line height.
    fun TextStyle.emphasized(
        tracking: TextUnit = NoTracking,
        features: String? = null,
    ) = withGoogleSansFlex(tracking, features, fontWeightAdjustment, grade, emphasized = true)

    return Typography(
        displayLarge = tokens.displayLarge.base(),
        displayMedium = tokens.displayMedium.base(),
        displaySmall = tokens.displaySmall.base(),
        headlineLarge = tokens.headlineLarge.base(),
        headlineMedium = tokens.headlineMedium.base(),
        headlineSmall = tokens.headlineSmall.base(),
        titleLarge = tokens.titleLarge.base(),
        titleMedium = tokens.titleMedium.base(),
        titleSmall = tokens.titleSmall.base(),
        bodyLarge = tokens.bodyLarge.base(),
        bodyMedium = tokens.bodyMedium.base(),
        bodySmall = tokens.bodySmall.base(SmallTracking),
        labelLarge = tokens.labelLarge.base(),
        labelMedium = tokens.labelMedium.base(SmallTracking),
        labelSmall = tokens.labelSmall.base(SmallTracking, TABULAR_FIGURES),
        displayLargeEmphasized = tokens.displayLarge.emphasized(),
        displayMediumEmphasized = tokens.displayMedium.emphasized(),
        displaySmallEmphasized = tokens.displaySmall.emphasized(),
        headlineLargeEmphasized = tokens.headlineLarge.emphasized(),
        headlineMediumEmphasized = tokens.headlineMedium.emphasized(),
        headlineSmallEmphasized = tokens.headlineSmall.emphasized(),
        titleLargeEmphasized = tokens.titleLarge.emphasized(),
        titleMediumEmphasized = tokens.titleMedium.emphasized(),
        titleSmallEmphasized = tokens.titleSmall.emphasized(),
        bodyLargeEmphasized = tokens.bodyLarge.emphasized(),
        bodyMediumEmphasized = tokens.bodyMedium.emphasized(),
        bodySmallEmphasized = tokens.bodySmall.emphasized(SmallTracking),
        labelLargeEmphasized = tokens.labelLarge.emphasized(),
        labelMediumEmphasized = tokens.labelMedium.emphasized(SmallTracking),
        labelSmallEmphasized = tokens.labelSmall.emphasized(SmallTracking, TABULAR_FIGURES),
    )
}

/** This token style in Google Sans Flex: its weight, or one step heavier and rounded if [emphasized]. */
private fun TextStyle.withGoogleSansFlex(
    tracking: TextUnit,
    features: String?,
    fontWeightAdjustment: Int,
    grade: Int,
    emphasized: Boolean,
): TextStyle {
    val baseWeight = checkNotNull(fontWeight) { "Material 3 tokens set every weight" }.weight
    val emphasizedWeight = baseWeight + EMPHASIS_WEIGHT
    val weight = if (emphasized) emphasizedWeight else baseWeight
    val instance =
        FontInstance(
            opticalSize = fontSize.value,
            round = if (emphasized) EMPHASIS_ROUND else 0f,
            grade = grade,
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
        // Every weight and slant the text asks for has a real font: a bold span takes the heaviest one the style has.
        fontSynthesis = FontSynthesis.None,
        letterSpacing = tracking,
        fontFeatureSettings = features,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}

/**
 * Variation settings shared by every font of one style's family. Each distinct setting becomes its
 * own Typeface when first used, kept for the process ([GoogleSansFlexTypefaces]), so a family holds
 * only the weights its style needs, upright and with a true variable slant for italics. Glyphs the
 * font lacks (CJK, Arabic, Devanagari, emoji…) fall back to the system fonts automatically.
 */
private class FontInstance(
    val opticalSize: Float,
    val round: Float,
    val grade: Int,
) {
    /** The font at `wght` [weight], registered in its family at [registeredWeight] (see [pingogramTypography]). */
    fun font(
        weight: Int,
        registeredWeight: FontWeight,
        italic: Boolean,
    ): Font =
        GoogleSansFlexFont(
            weight = registeredWeight,
            style = if (italic) FontStyle.Italic else FontStyle.Normal,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(weight),
                    // opsz must equal the size in sp (§3.1): Android does not apply optical sizing on its own.
                    FontVariation.Setting("opsz", opticalSize),
                    FontVariation.Setting("ROND", round),
                    FontVariation.grade(grade),
                    FontVariation.width(NORMAL_WIDTH),
                    FontVariation.slant(if (italic) ITALIC_SLANT else 0f),
                ),
        )
}
