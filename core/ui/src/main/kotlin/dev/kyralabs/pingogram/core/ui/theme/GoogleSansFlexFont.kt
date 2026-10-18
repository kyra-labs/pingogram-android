package dev.kyralabs.pingogram.core.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.kyralabs.pingogram.core.ui.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Google Sans Flex at one set of variation settings (`docs/DESIGN_SYSTEM.md` §3), registered in its style's family at
 * [weight] and [style]. Like `Font(R.font.google_sans_flex, …)`, except that its typeface is made once per process
 * ([GoogleSansFlexTypefaces]).
 */
internal class GoogleSansFlexFont(
    override val weight: FontWeight,
    override val style: FontStyle,
    variationSettings: FontVariation.Settings,
) : AndroidFont(FontLoadingStrategy.Blocking, GoogleSansFlexTypefaces, variationSettings) {
    override fun equals(other: Any?): Boolean =
        other is GoogleSansFlexFont &&
            weight == other.weight &&
            style == other.style &&
            variationSettings == other.variationSettings

    override fun hashCode(): Int =
        (weight.hashCode() * HASH_MULTIPLIER + style.hashCode()) * HASH_MULTIPLIER +
            variationSettings.hashCode()

    override fun toString(): String = "GoogleSansFlexFont(weight=$weight, style=$style, $variationSettings)"

    private companion object {
        const val HASH_MULTIPLIER = 31
    }
}

/**
 * Makes the typeface of each [GoogleSansFlexFont] once per process and keeps it.
 *
 * Compose keeps the typefaces it resolves in two caches of 16 entries each, shared by all text. The type scale has 30
 * styles, and the chat list and a conversation together use about as many fonts as those caches hold, so going back
 * and forth evicted some. Compose then loaded them again, and every load made a new typeface, which the platform's text
 * caches (laid out words, rasterized glyphs) are keyed by: text in that style was laid out and rasterized again, as if
 * never drawn, in the frames of the transition. With the same typeface back from here, an eviction costs a lookup.
 *
 * The variation settings apply to the bundled font as Compose applies them (`'wght' 400, 'opsz' 16, …`), with the
 * system's "Bold text" adjustment added to `wght` (Android 12+), which Compose leaves to the loader of an
 * [AndroidFont]. A fresh [Paint] for each typeface: Compose reuses one, and resets it in a way that creates an extra
 * typeface.
 */
internal object GoogleSansFlexTypefaces : AndroidFont.TypefaceLoader {
    private val typefaces = ConcurrentHashMap<String, Typeface>()

    override fun loadBlocking(
        context: Context,
        font: AndroidFont,
    ): Typeface {
        val settings = font.variationSettings.toAndroidString(context.fontWeightAdjustment())
        return typefaces.computeIfAbsent(settings) {
            // The font file, which the platform loads once and keeps.
            val file = context.resources.getFont(R.font.google_sans_flex)
            Paint()
                .apply {
                    typeface = file
                    fontVariationSettings = settings
                }.typeface
        }
    }

    override suspend fun awaitLoad(
        context: Context,
        font: AndroidFont,
    ): Typeface = loadBlocking(context, font)

    private fun FontVariation.Settings.toAndroidString(weightAdjustment: Int): String =
        settings.joinToString(",") { setting ->
            val value = setting.toVariationValue(null)
            val adjusted =
                if (setting.axisName == WEIGHT_AXIS) {
                    (value + weightAdjustment).coerceIn(MIN_WEIGHT, MAX_WEIGHT)
                } else {
                    value
                }
            "'${setting.axisName}' $adjusted"
        }

    private fun Context.fontWeightAdjustment(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0
        val adjustment = resources.configuration.fontWeightAdjustment
        return if (adjustment == Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED) 0 else adjustment
    }

    private const val WEIGHT_AXIS = "wght"

    // The range of the platform's font weights, as Compose clamps the adjusted wght.
    private const val MIN_WEIGHT = 1f
    private const val MAX_WEIGHT = 1000f
}
