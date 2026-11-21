package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.kyralabs.pingogram.core.domain.model.Contrast
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.max
import kotlin.math.min

/**
 * A conversation in its wallpaper's colors (`docs/adr/0041-chat-colors-from-the-wallpaper.md`) keeps the contrast the
 * color themes keep (`ThemeContrastTest`), for seeds like wallpapers give, at every contrast level, light, dark and
 * AMOLED black: 4.5:1 for text on bubbles, date chips, the composer and the buttons, 3:1 for accents on bubbles.
 */
class WallpaperColorsContrastTest {
    @ParameterizedTest
    @ValueSource(
        ints = [
            0xFFB4744B.toInt(), 0xFF2156C4.toInt(), 0xFF4CAF50.toInt(), 0xFFCDDC39.toInt(),
            0xFFE91E63.toInt(), 0xFF9A9A9A.toInt(), 0xFF101010.toInt(), 0xFFF5F0E6.toInt(),
        ],
    )
    fun `text and accents keep their contrast in a wallpaper's colors`(seed: Int) {
        for (contrast in Contrast.entries) {
            for (dark in listOf(false, true)) {
                val scheme = contentColorScheme(Color(seed), dark, contrast)
                check("${seed.toUInt().toString(16)} dark=$dark $contrast", scheme, dark)
                if (dark) check("${seed.toUInt().toString(16)} AMOLED $contrast", scheme.withAmoledSurfaces(), dark)
            }
        }
    }

    private fun check(
        look: String,
        scheme: ColorScheme,
        dark: Boolean,
    ) {
        val colors = pingogramColors(scheme, dark)
        val text =
            buildMap {
                val bubbles =
                    mapOf(
                        "incoming" to colors.incomingBubble,
                        "outgoing" to colors.outgoingBubble,
                        "selected" to colors.selectedBubble,
                    )
                for ((name, bubble) in bubbles) {
                    put("$name text", bubble.content to bubble.container)
                    put("$name secondary text", bubble.secondaryContent to bubble.container)
                }
                put("date chip", scheme.onSurfaceVariant to scheme.surfaceContainerHighest)
                put("service message", scheme.onSurfaceVariant to scheme.surfaceContainerHighest)
                put("composer text", scheme.onSurface to scheme.surfaceContainerHigh)
                put("composer hint", scheme.onSurfaceVariant to scheme.surfaceContainerHigh)
                put("send button", scheme.onPrimary to scheme.primary)
                put("jump to bottom", scheme.onSecondaryContainer to scheme.secondaryContainer)
            }
        for ((name, pair) in text) assertTrue(ratio(pair) >= TEXT, "$look $name: ${ratio(pair)}")
        for (bubble in listOf(colors.incomingBubble, colors.outgoingBubble, colors.selectedBubble)) {
            val pair = bubble.accent to bubble.container
            assertTrue(ratio(pair) >= NON_TEXT, "$look accent ${bubble.accent} on ${bubble.container}: ${ratio(pair)}")
        }
    }

    private fun ratio(pair: Pair<Color, Color>): Float {
        val lighter = max(pair.first.luminance(), pair.second.luminance())
        val darker = min(pair.first.luminance(), pair.second.luminance())
        return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
    }

    private companion object {
        const val TEXT = 4.5f
        const val NON_TEXT = 3f
        const val LUMINANCE_OFFSET = 0.05f
    }
}
