package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import dev.kyralabs.pingogram.core.domain.model.BubbleCorners
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chat style the theme hands conversations ([ChatStyle], `REQUIREMENTS.md` FR-S1): message text scaled with its
 * optical size re-derived (`docs/DESIGN_SYSTEM.md` §3.2), bubble corners from the shape scale with the joined ones
 * kept, and wallpapers from the theme's roles.
 */
class ChatStyleTest {
    private val scheme = themeColorScheme(ThemePalette.Pingogram, dark = false, contrast = Contrast.Standard)
    private val density = Density(1f)
    private val size = Size(200f, 200f)

    @Test
    fun `the default text size is bodyLarge itself`() {
        assertSame(pingogramTypography().bodyLarge, pingogramMessageText(1f))
    }

    @Test
    fun `a scaled text size scales size and line height and sets the optical size to the new size`() {
        val style = pingogramMessageText(1.25f)
        val body = pingogramTypography().bodyLarge

        assertEquals((body.fontSize.value * 1.25f).sp, style.fontSize)
        assertEquals((body.lineHeight.value * 1.25f).sp, style.lineHeight)
        val opticalSizes =
            (style.fontFamily as androidx.compose.ui.text.font.FontListFontFamily)
                .fonts
                .map { font ->
                    (font as GoogleSansFlexFont).variationSettings.settings.single { it.axisName == "opsz" }
                }.map { (it as FontVariation.Setting).toVariationValue(density) }
        assertTrue(opticalSizes.isNotEmpty())
        opticalSizes.forEach { assertEquals(style.fontSize.value, it, 0.001f) }
    }

    @Test
    fun `scaled text styles are made once`() {
        assertSame(pingogramMessageText(1.1f), pingogramMessageText(1.1f))
    }

    @Test
    fun `bubble corners take the chosen step, joined corners stay extraSmall`() {
        val style =
            chatStyle(
                ChatAppearance(bubbleCorners = BubbleCorners.Large),
                pingogramMessageText(1f),
                PingogramShapes,
                scheme,
            )
        val alone = style.bubbleShapes.of(isOutgoing = true, joinsPrevious = false, joinsNext = false)
        val middle = style.bubbleShapes.of(isOutgoing = true, joinsPrevious = true, joinsNext = true)

        assertEquals(PingogramShapes.large.topStart.px(), alone.topStart.px())
        assertEquals(PingogramShapes.large.topEnd.px(), alone.topEnd.px())
        assertEquals(PingogramShapes.extraSmall.topEnd.px(), middle.topEnd.px())
        assertEquals(PingogramShapes.large.topStart.px(), middle.topStart.px())
    }

    @Test
    fun `no wallpaper draws nothing, the others come from the theme's roles`() {
        assertNull(ChatWallpaper.None.brush(scheme))
        assertEquals(SolidColor(scheme.surfaceContainerLow), ChatWallpaper.Neutral.brush(scheme))
        val tints = ChatWallpaper.entries.drop(1).map { it.brush(scheme) }
        assertEquals(tints.size, tints.toSet().size)
        assertNotEquals(SolidColor(scheme.surface), ChatWallpaper.Primary.brush(scheme))
    }

    private fun CornerSize.px() = toPx(size, density)
}
