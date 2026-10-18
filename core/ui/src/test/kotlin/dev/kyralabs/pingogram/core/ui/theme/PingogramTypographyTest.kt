package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.text.style.LineHeightStyle
import dev.kyralabs.pingogram.core.ui.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Checks the type scale against `docs/DESIGN_SYSTEM.md` §3 by reading back each style's font settings. */
class PingogramTypographyTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `defines all 30 styles in Google Sans Flex`(dark: Boolean) {
        val styles = pingogramTypography(dark).styles()

        assertEquals(30, styles.size)
        styles.forEach { (name, style) ->
            assertTrue(style.fonts().all { it is ResourceFont && it.resId == R.font.google_sans_flex }, name)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `optical size equals the font size of every style`(dark: Boolean) {
        pingogramTypography(dark).styles().forEach { (name, style) ->
            style.fonts().forEach { assertEquals(style.fontSize.value, it.axis("opsz"), name) }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `grade is 0 in light and -25 in dark, 25 higher for emphasized styles`(dark: Boolean) {
        pingogramTypography(dark).styles().forEach { (name, style) ->
            val expected = (if (dark) -25f else 0f) + (if (name.isEmphasized()) 25f else 0f)
            style.fonts().forEach { assertEquals(expected, it.axis("GRAD"), name) }
        }
    }

    @Test
    fun `display, headline and title styles are rounded, body and label styles are not`() {
        pingogramTypography(dark = false).styles().forEach { (name, style) ->
            val expected = if (name.category() in setOf("display", "headline", "title")) 100f else 0f
            style.fonts().forEach { assertEquals(expected, it.axis("ROND"), name) }
        }
    }

    @Test
    fun `base weights follow the category and emphasized styles are 200 heavier up to 800`() {
        val baseWeights = mapOf("display" to 400, "headline" to 500, "title" to 600, "body" to 400, "label" to 500)
        val styles = pingogramTypography(dark = false).styles()

        styles.forEach { (name, style) ->
            val base = baseWeights.getValue(name.category())
            val expected = if (name.isEmphasized()) minOf(base + 200, 800) else base
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
        pingogramTypography(dark = false, adjustment).styles().forEach { (name, style) ->
            style.fonts().forEach {
                assertEquals(minOf(it.axis("wght").toInt() + adjustment, 1000), it.weight.weight, name)
            }
        }
    }

    // Compose asks the family for the style's weight plus the adjustment, and adds the adjustment to that font's wght.
    @ParameterizedTest
    @ValueSource(ints = [0, BOLD_TEXT])
    fun `the font a style asks for has the style's own wght, with or without Bold text`(adjustment: Int) {
        pingogramTypography(dark = false, adjustment).styles().forEach { (name, style) ->
            val weight = style.fontWeight!!.weight
            val requested = minOf(weight + adjustment, 1000)
            val font = style.fonts().single { it.weight.weight == requested && it.style == FontStyle.Normal }
            assertEquals(weight.toFloat(), font.axis("wght"), name)
        }
    }

    @Test
    fun `each weight has an upright font and a true italic font with slant -10`() {
        pingogramTypography(dark = false).styles().forEach { (name, style) ->
            style.fonts().groupBy { it.weight }.forEach { (weight, fonts) ->
                val upright = fonts.single { it.style == FontStyle.Normal }
                val italic = fonts.single { it.style == FontStyle.Italic }
                assertEquals(0f, upright.axis("slnt"), "$name $weight")
                assertEquals(-10f, italic.axis("slnt"), "$name $weight")
            }
        }
    }

    @Test
    fun `display is wide, labelSmall is narrow with tabular figures, the rest is normal width`() {
        pingogramTypography(dark = false).styles().forEach { (name, style) ->
            val expected =
                when {
                    name.category() == "display" -> 110f
                    name.startsWith("labelSmall") -> 95f
                    else -> 100f
                }
            style.fonts().forEach { assertEquals(expected, it.axis("wdth"), name) }
            assertEquals(if (name.startsWith("labelSmall")) "tnum" else null, style.fontFeatureSettings, name)
        }
    }

    @Test
    fun `line height is centered and font padding is off`() {
        val centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

        pingogramTypography(dark = false).styles().forEach { (name, style) ->
            assertEquals(centered, style.lineHeightStyle, name)
            assertFalse(style.platformStyle?.paragraphStyle?.includeFontPadding ?: true, name)
        }
    }

    @Test
    fun `keeps the Material 3 token sizes, line heights and tracking`() {
        val tokens = Typography().styles()

        pingogramTypography(dark = false).styles().forEach { (name, style) ->
            val token = tokens.getValue(name)
            assertEquals(token.fontSize, style.fontSize, name)
            assertEquals(token.lineHeight, style.lineHeight, name)
            assertEquals(token.letterSpacing, style.letterSpacing, name)
        }
    }

    @Test
    fun `builds the typography once per theme and Bold text adjustment`() {
        assertSame(pingogramTypography(dark = false), pingogramTypography(dark = false))
        assertSame(pingogramTypography(dark = true), pingogramTypography(dark = true))
        assertSame(pingogramTypography(dark = false, BOLD_TEXT), pingogramTypography(dark = false, BOLD_TEXT))
    }

    @Test
    fun `Bold text keeps the styles' weights and axes`() {
        val plain = pingogramTypography(dark = false).styles()

        pingogramTypography(dark = false, BOLD_TEXT).styles().forEach { (name, style) ->
            val expected = plain.getValue(name)
            assertEquals(expected.fontWeight, style.fontWeight, name)
            assertEquals(expected.fonts().map { it.axes() }, style.fonts().map { it.axes() }, name)
        }
    }

    private fun TextStyle.fonts(): List<Font> = (fontFamily as FontListFontFamily).fonts

    private fun Font.axis(tag: String): Float =
        (this as ResourceFont)
            .variationSettings.settings
            .single { it.axisName == tag }
            .toVariationValue(null)

    private fun Font.axes(): Map<String, Float> =
        (this as ResourceFont).variationSettings.settings.associate { it.axisName to it.toVariationValue(null) }

    private fun String.isEmphasized() = endsWith("Emphasized")

    private fun String.category() = takeWhile { it.isLowerCase() }

    private companion object {
        /** Android's "Bold text" setting: `Configuration.fontWeightAdjustment` while it is on. */
        const val BOLD_TEXT = 300
    }
}
