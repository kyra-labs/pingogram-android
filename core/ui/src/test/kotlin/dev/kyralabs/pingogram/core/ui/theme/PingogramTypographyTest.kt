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

    @Test
    fun `every font sets wght to the weight it is registered with`() {
        pingogramTypography(dark = false).styles().forEach { (name, style) ->
            style.fonts().forEach { assertEquals(it.weight.weight.toFloat(), it.axis("wght"), name) }
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
    fun `builds the typography once per theme`() {
        assertSame(pingogramTypography(dark = false), pingogramTypography(dark = false))
        assertSame(pingogramTypography(dark = true), pingogramTypography(dark = true))
    }

    private fun TextStyle.fonts(): List<Font> = (fontFamily as FontListFontFamily).fonts

    private fun Font.axis(tag: String): Float =
        (this as ResourceFont)
            .variationSettings.settings
            .single { it.axisName == tag }
            .toVariationValue(null)

    private fun String.isEmphasized() = endsWith("Emphasized")

    private fun String.category() = takeWhile { it.isLowerCase() }

    private fun Typography.styles(): Map<String, TextStyle> =
        mapOf(
            "displayLarge" to displayLarge,
            "displayMedium" to displayMedium,
            "displaySmall" to displaySmall,
            "headlineLarge" to headlineLarge,
            "headlineMedium" to headlineMedium,
            "headlineSmall" to headlineSmall,
            "titleLarge" to titleLarge,
            "titleMedium" to titleMedium,
            "titleSmall" to titleSmall,
            "bodyLarge" to bodyLarge,
            "bodyMedium" to bodyMedium,
            "bodySmall" to bodySmall,
            "labelLarge" to labelLarge,
            "labelMedium" to labelMedium,
            "labelSmall" to labelSmall,
            "displayLargeEmphasized" to displayLargeEmphasized,
            "displayMediumEmphasized" to displayMediumEmphasized,
            "displaySmallEmphasized" to displaySmallEmphasized,
            "headlineLargeEmphasized" to headlineLargeEmphasized,
            "headlineMediumEmphasized" to headlineMediumEmphasized,
            "headlineSmallEmphasized" to headlineSmallEmphasized,
            "titleLargeEmphasized" to titleLargeEmphasized,
            "titleMediumEmphasized" to titleMediumEmphasized,
            "titleSmallEmphasized" to titleSmallEmphasized,
            "bodyLargeEmphasized" to bodyLargeEmphasized,
            "bodyMediumEmphasized" to bodyMediumEmphasized,
            "bodySmallEmphasized" to bodySmallEmphasized,
            "labelLargeEmphasized" to labelLargeEmphasized,
            "labelMediumEmphasized" to labelMediumEmphasized,
            "labelSmallEmphasized" to labelSmallEmphasized,
        )
}
