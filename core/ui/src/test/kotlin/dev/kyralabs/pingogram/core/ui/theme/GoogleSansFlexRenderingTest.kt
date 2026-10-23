package dev.kyralabs.pingogram.core.ui.theme

import android.app.UiModeManager
import android.content.ComponentName
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Proves that the variable axes of the type scale (`docs/DESIGN_SYSTEM.md` §3) reach the rendered glyphs, which
 * [PingogramTypographyTest] cannot: it reads the settings back, and the platform could still drop them. Each test
 * renders the same text with Robolectric's native graphics (the Skia, minikin and FreeType of the Android release it
 * runs) and compares the pixels of two renderings with each other, so nothing depends on goldens: a style must render
 * differently from a copy of it with one axis at the font's default, and exactly like a copy that changes nothing.
 * Black on white, so the sum of the darkness of all pixels ([Rendering.ink]) measures how heavy the text is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GoogleSansFlexRenderingTest {
    // Declares the activity to Robolectric, as ui-test-manifest would.
    @get:Rule(order = 0)
    val activityRegistration =
        object : ExternalResource() {
            override fun before() {
                val application = RuntimeEnvironment.getApplication()
                shadowOf(application.packageManager)
                    .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
            }
        }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var sample by mutableStateOf<(Typography) -> TextStyle>({ it.bodyLarge })
    private var started = false

    @After
    fun resetBoldText() = setBoldTextAdjustment(0)

    @Test
    fun `every axis a style sets changes its glyphs`() {
        val typography = pingogramTypography()
        typography.styles().forEach { (name, style) ->
            val rendering = render { style }
            val font = style.fonts().first { it.weight == style.fontWeight && it.style == FontStyle.Normal }
            FontDefaults
                .filter { (tag, default) -> font.axis(tag) != default }
                .forEach { (tag, default) ->
                    val atDefault = render { style.withAxis(tag, default) }
                    val message = "$name with $tag ${font.axis(tag)} renders as with $tag $default"
                    assertFalse(message, rendering.looksLike(atDefault))
                }
        }
    }

    @Test
    fun `a copy with the same axis values renders identically`() {
        val style = pingogramTypography().titleMediumEmphasized
        val font = style.fonts().first()
        val rendering = render { style }

        FontDefaults.keys.forEach { tag ->
            assertTrue(tag, rendering.looksLike(render { style.withAxis(tag, font.axis(tag)) }))
        }
    }

    @Test
    fun `emphasized styles render heavier than their base styles`() {
        val styles = pingogramTypography().styles()

        styles.filterKeys { !it.endsWith("Emphasized") }.forEach { (name, base) ->
            val emphasized = styles.getValue("${name}Emphasized")
            assertTrue(name, render { emphasized }.ink > render { base }.ink)
        }
    }

    @Test
    fun `italic is the font's own slant, never a synthesized one`() {
        val italic = pingogramTypography().bodyLarge.copy(fontStyle = FontStyle.Italic)
        val rendering = render { italic }

        assertFalse(rendering.looksLike(render { italic.withAxis("slnt", 0f) }))
        assertTrue(rendering.looksLike(render { italic.copy(fontSynthesis = FontSynthesis.None) }))
    }

    @Test
    fun `bold text in a regular style takes the emphasized font, never a synthesized bold`() {
        val bodyLarge = pingogramTypography().bodyLarge
        val emphasizedWeight = pingogramTypography().bodyLargeEmphasized.fontWeight

        assertTrue(
            render { bodyLarge.copy(fontWeight = FontWeight.Bold) }
                .looksLike(render { bodyLarge.copy(fontWeight = emphasizedWeight) }),
        )
    }

    // Android 12+'s "Bold text" setting. Compose adds the adjustment to the weight it asks the family for and to the
    // `wght` of the font it gets; PingogramTheme passes the adjustment on so that it applies once.
    @Test
    fun `with Bold text, the theme's styles are bolder by the adjustment once`() {
        setBoldTextAdjustment(BOLD_TEXT_ADJUSTMENT)
        val plain = pingogramTypography()

        assertTrue(render { it.bodyLarge }.looksLike(render { plain.bodyLarge.fontAlone(wght = 400f) }))
        assertTrue(render { it.titleMedium }.looksLike(render { plain.titleMedium.fontAlone(wght = 500f) }))
        // Read and unread chat titles stay apart (800 and 900).
        assertFalse(render { it.titleMedium }.looksLike(render { it.titleMediumEmphasized }))
    }

    // High contrast (the system's setting, which the theme follows on Android 14+) sets text at GRAD 25 (§10).
    @Test
    fun `with high contrast, the theme's styles render a grade heavier`() {
        shadowOf(composeRule.activity.getSystemService(UiModeManager::class.java)).setContrast(1f)
        val standard = pingogramTypography()

        val highContrast = render { it.bodyLarge }

        assertTrue(
            highContrast.looksLike(render { standard.bodyLarge.withAxis("GRAD", HIGH_CONTRAST_GRADE.toFloat()) }),
        )
        assertTrue(highContrast.ink > render { standard.bodyLarge }.ink)
    }

    @Test
    fun `high contrast's grade keeps every style's width, so no text reflows`() {
        val standard = pingogramTypography().styles()
        val highContrast = pingogramTypography(contrast = Contrast.High).styles()
        var widths = emptyMap<String, Pair<Int, Int>>()
        composeRule.setContent {
            val measurer = rememberTextMeasurer()
            widths =
                standard.mapValues { (name, style) ->
                    measurer.measure(SAMPLE_TEXT, style).size.width to
                        measurer.measure(SAMPLE_TEXT, highContrast.getValue(name)).size.width
                }
        }
        composeRule.waitForIdle()

        assertEquals(standard.keys, widths.keys)
        widths.forEach { (name, width) -> assertEquals(name, width.first, width.second) }
    }

    private class Rendering(
        val pixels: Bitmap,
    ) {
        val ink: Long by lazy {
            val colors = IntArray(pixels.width * pixels.height)
            pixels.getPixels(colors, 0, pixels.width, 0, 0, pixels.width, pixels.height)
            colors.sumOf { BLACK_ON_WHITE_MAX - (it and BLUE_CHANNEL).toLong() }
        }

        fun looksLike(other: Rendering) = pixels.sameAs(other.pixels)
    }

    /** [SAMPLE_TEXT] in the style [style] picks from the light theme's typography. */
    private fun render(style: (Typography) -> TextStyle): Rendering {
        sample = style
        if (!started) {
            started = true
            composeRule.setContent {
                PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                    Box(Modifier.size(400.dp, 80.dp).background(Color.White).testTag(SAMPLE_TAG)) {
                        Text(
                            text = SAMPLE_TEXT,
                            color = Color.Black,
                            maxLines = 1,
                            softWrap = false,
                            style = sample(MaterialTheme.typography),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val pixels = composeRule.onNodeWithTag(SAMPLE_TAG).captureToImage().asAndroidBitmap()
        return Rendering(pixels)
    }

    /**
     * Sets the system's "Bold text" adjustment for the activity, which Compose reads to pick fonts, and for the
     * application, which it reads to load them. Before the first [render]: Compose reads it once per view.
     */
    private fun setBoldTextAdjustment(adjustment: Int) {
        val resources = listOf(RuntimeEnvironment.getApplication().resources, composeRule.activity.resources)
        resources.forEach { it.setFontWeightAdjustment(adjustment) }
    }

    @Suppress("DEPRECATION") // Robolectric applies configuration changes to existing resources only this way.
    private fun Resources.setFontWeightAdjustment(adjustment: Int) {
        val configuration = Configuration(configuration).apply { fontWeightAdjustment = adjustment }
        updateConfiguration(configuration, displayMetrics)
    }

    private fun TextStyle.fonts(): List<GoogleSansFlexFont> =
        (fontFamily as FontListFontFamily).fonts.map { it as GoogleSansFlexFont }

    /** This style with [tag] set to [value] in every font of its family. */
    private fun TextStyle.withAxis(
        tag: String,
        value: Float,
    ): TextStyle {
        val fonts =
            fonts().map { font ->
                val settings =
                    font.variationSettings.settings.map {
                        if (it.axisName == tag) FontVariation.Setting(tag, value) else it
                    }
                GoogleSansFlexFont(font.weight, font.style, FontVariation.Settings(*settings.toTypedArray()))
            }
        return copy(fontFamily = FontFamily(fonts))
    }

    /**
     * The upright font of this style alone, at `wght` [wght] and without synthesis, so the family has nothing to match,
     * loaded the way Compose loads a font resource rather than through [GoogleSansFlexTypefaces]. Registered at weight
     * 1, which no family of the type scale uses: Compose's typeface cache, which outlives each test, then never hands
     * back a typeface that was loaded without the Bold text adjustment.
     */
    private fun TextStyle.fontAlone(wght: Float): TextStyle {
        val font = withAxis("wght", wght).fonts().first { it.weight == fontWeight && it.style == FontStyle.Normal }
        return copy(
            fontFamily =
                FontFamily(
                    Font(
                        R.font.google_sans_flex,
                        FontWeight(1),
                        font.style,
                        variationSettings = font.variationSettings,
                    ),
                ),
            fontSynthesis = FontSynthesis.None,
        )
    }

    private fun GoogleSansFlexFont.axis(tag: String): Float =
        variationSettings.settings
            .single { it.axisName == tag }
            .toVariationValue(null)

    private companion object {
        // Round and straight strokes, descenders and figures.
        const val SAMPLE_TEXT = "Ping 12:45"
        const val SAMPLE_TAG = "sample"
        const val BOLD_TEXT_ADJUSTMENT = 300
        const val BLACK_ON_WHITE_MAX = 255L
        const val BLUE_CHANNEL = 0xFF

        /** The default of each axis of google_sans_flex.ttf (its fvar table): how it renders without a setting. */
        val FontDefaults =
            mapOf("opsz" to 18f, "wdth" to 100f, "wght" to 400f, "GRAD" to 0f, "ROND" to 0f, "slnt" to 0f)
    }
}
