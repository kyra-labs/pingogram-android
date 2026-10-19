package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Checks the type scale against `docs/DESIGN_SYSTEM.md` §3 by reading back each style's font settings. */
class PingogramTypographyTest {
    @Test
    fun `defines all 30 styles in Google Sans Flex`() {
        val styles = pingogramTypography().styles()

        assertEquals(30, styles.size)
        styles.forEach { (name, style) ->
            assertTrue(style.fonts().all { it is GoogleSansFlexFont }, name)
        }
    }

    @Test
    fun `optical size equals the font size of every style`() {
        pingogramTypography().styles().forEach { (name, style) ->
            style.fonts().forEach { assertEquals(style.fontSize.value, it.axis("opsz"), name) }
        }
    }

    @Test
    fun `grade is 0 in every style`() {
        pingogramTypography().styles().forEach { (name, style) ->
            style.fonts().forEach { assertEquals(0f, it.axis("GRAD"), name) }
        }
    }

    @Test
    fun `emphasized styles are rounded, base styles are not`() {
        pingogramTypography().styles().forEach { (name, style) ->
            val expected = if (name.isEmphasized()) 100f else 0f
            style.fonts().forEach { assertEquals(expected, it.axis("ROND"), name) }
        }
    }

    @Test
    fun `base styles have the Material 3 token weights and emphasized styles are 100 heavier`() {
        val tokens = Typography().styles()
        val styles = pingogramTypography().styles()

        styles.forEach { (name, style) ->
            val base = tokens.getValue(name.removeSuffix("Emphasized")).fontWeight!!.weight
            val expected = if (name.isEmphasized()) base + 100 else base
            assertEquals(expected, style.fontWeight?.weight, name)
            assertTrue(
                style.fonts().any { it.weight.weight == expected && it.axis("wght") == expected.toFloat() },
                name,
            )
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, BOLD_TEXT])
    fun `every font is registered at its wght plus the Bold text adjustment`(adjustment: Int) {
        pingogramTypography(adjustment).styles().forEach { (name, style) ->
            style.fonts().forEach {
                assertEquals(minOf(it.axis("wght").toInt() + adjustment, 1000), it.weight.weight, name)
            }
        }
    }

    // Compose asks the family for the style's weight plus the adjustment, and adds the adjustment to that font's wght.
    @ParameterizedTest
    @ValueSource(ints = [0, BOLD_TEXT])
    fun `the font a style asks for has the style's own wght, with or without Bold text`(adjustment: Int) {
        pingogramTypography(adjustment).styles().forEach { (name, style) ->
            val weight = style.fontWeight!!.weight
            val requested = minOf(weight + adjustment, 1000)
            val font = style.fonts().single { it.weight.weight == requested && it.style == FontStyle.Normal }
            assertEquals(weight.toFloat(), font.axis("wght"), name)
        }
    }

    @Test
    fun `each weight has an upright font and a true italic font with slant -10`() {
        pingogramTypography().styles().forEach { (name, style) ->
            style.fonts().groupBy { it.weight }.forEach { (weight, fonts) ->
                val upright = fonts.single { it.style == FontStyle.Normal }
                val italic = fonts.single { it.style == FontStyle.Italic }
                assertEquals(0f, upright.axis("slnt"), "$name $weight")
                assertEquals(-10f, italic.axis("slnt"), "$name $weight")
            }
        }
    }

    @Test
    fun `every style is normal width, and labelSmall has tabular figures`() {
        pingogramTypography().styles().forEach { (name, style) ->
            style.fonts().forEach { assertEquals(100f, it.axis("wdth"), name) }
            assertEquals(if (name.startsWith("labelSmall")) "tnum" else null, style.fontFeatureSettings, name)
        }
    }

    @Test
    fun `no style synthesizes a weight or slant`() {
        pingogramTypography().styles().forEach { (name, style) ->
            assertEquals(FontSynthesis.None, style.fontSynthesis, name)
        }
    }

    @Test
    fun `line height is centered and font padding is off`() {
        val centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

        pingogramTypography().styles().forEach { (name, style) ->
            assertEquals(centered, style.lineHeightStyle, name)
            assertFalse(style.platformStyle?.paragraphStyle?.includeFontPadding ?: true, name)
        }
    }

    @Test
    fun `keeps the Material 3 token sizes and line heights`() {
        val tokens = Typography().styles()

        pingogramTypography().styles().forEach { (name, style) ->
            val token = tokens.getValue(name)
            assertEquals(token.fontSize, style.fontSize, name)
            assertEquals(token.lineHeight, style.lineHeight, name)
        }
    }

    // Google's tracking for Google Sans Flex (Android's SystemUI type scale), not the Material tokens' Roboto tracking.
    @Test
    fun `tracks only the smallest styles, by 0_1 sp`() {
        val tracked = setOf("bodySmall", "labelMedium", "labelSmall")

        pingogramTypography().styles().forEach { (name, style) ->
            val expected = if (name.removeSuffix("Emphasized") in tracked) 0.1.sp else 0.sp
            assertEquals(expected, style.letterSpacing, name)
        }
    }

    @Test
    fun `builds the typography once per theme and Bold text adjustment`() {
        assertSame(pingogramTypography(), pingogramTypography())
        assertSame(pingogramTypography(BOLD_TEXT), pingogramTypography(BOLD_TEXT))
    }

    @Test
    fun `Bold text keeps the styles' weights and axes`() {
        val plain = pingogramTypography().styles()

        pingogramTypography(BOLD_TEXT).styles().forEach { (name, style) ->
            val expected = plain.getValue(name)
            assertEquals(expected.fontWeight, style.fontWeight, name)
            assertEquals(expected.fonts().map { it.axes() }, style.fonts().map { it.axes() }, name)
        }
    }

    private fun TextStyle.fonts(): List<Font> = (fontFamily as FontListFontFamily).fonts

    private fun Font.axis(tag: String): Float =
        (this as GoogleSansFlexFont)
            .variationSettings.settings
            .single { it.axisName == tag }
            .toVariationValue(null)

    private fun Font.axes(): Map<String, Float> =
        (this as GoogleSansFlexFont).variationSettings.settings.associate { it.axisName to it.toVariationValue(null) }

    private fun String.isEmphasized() = endsWith("Emphasized")

    private companion object {
        /** Android's "Bold text" setting: `Configuration.fontWeightAdjustment` while it is on. */
        const val BOLD_TEXT = 300
    }
}
