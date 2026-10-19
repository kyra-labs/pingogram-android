package dev.kyralabs.pingogram.core.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The typefaces of the type scale outlive Compose's typeface caches ([GoogleSansFlexTypefaces]), which keep 16 entries
 * each for the whole process: a font loaded again after an eviction must not become another typeface, which the
 * platform would shape and rasterize text for from scratch. That the typefaces render the variation settings, Bold
 * text included, as Compose's own loading of the font does is [GoogleSansFlexRenderingTest]'s.
 */
@RunWith(RobolectricTestRunner::class)
class GoogleSansFlexTypefacesTest {
    private val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val styles = pingogramTypography().styles().values

    @Test
    fun `every style gets its typeface back after Compose's caches moved on to other styles`() {
        // 60 requests, upright and italic for each style: well past what Compose keeps.
        val first = resolveAll()
        val second = resolveAll()

        first.zip(second).forEach { (before, after) -> assertSame(before, after) }
    }

    private fun resolveAll(): List<Typeface> =
        styles.flatMap { style ->
            listOf(FontStyle.Normal, FontStyle.Italic).map { fontStyle ->
                resolver.resolve(style.fontFamily, checkNotNull(style.fontWeight), fontStyle).value as Typeface
            }
        }
}
