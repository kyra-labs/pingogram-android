package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** Forum topic colors in the sender hues (`docs/DESIGN_SYSTEM.md` §2.2, owner decision 2026-11-16). */
class TopicHuesTest {
    @Test
    fun `each of Telegram's topic colors takes the sender hue of its name`() {
        // tools/theme's order: red, orange, violet, green, teal, blue, pink, gold.
        assertEquals(
            mapOf(
                TopicColor.Blue to 5,
                TopicColor.Yellow to 7,
                TopicColor.Green to 3,
                TopicColor.Violet to 2,
                TopicColor.Rose to 6,
                TopicColor.Red to 0,
            ),
            TopicColor.entries.associateWith { it.senderHueIndex },
        )
    }

    @ParameterizedTest
    @EnumSource(ThemePalette::class)
    fun `the six topic colors look different in light, dark, AMOLED and every contrast level`(palette: ThemePalette) {
        for (contrast in Contrast.entries) {
            val dark = themeColorScheme(palette, dark = true, contrast)
            val looks =
                mapOf(
                    "light $contrast" to (themeColorScheme(palette, dark = false, contrast) to false),
                    "dark $contrast" to (dark to true),
                    "AMOLED $contrast" to (dark.withAmoledSurfaces() to true),
                )
            for ((look, schemeAndDark) in looks) {
                assertSixDistinct(look, schemeAndDark.first, schemeAndDark.second)
            }
        }
    }

    private fun assertSixDistinct(
        look: String,
        scheme: ColorScheme,
        dark: Boolean,
    ) {
        val hues = pingogramColors(scheme, dark).senderHues
        val topics = TopicColor.entries.map { hues[it.senderHueIndex] }
        assertEquals(TopicColor.entries.size, topics.map { it.vivid.container }.distinct().size, "$look fills")
        assertEquals(TopicColor.entries.size, topics.map { it.ink }.distinct().size, "$look inks")
    }
}
