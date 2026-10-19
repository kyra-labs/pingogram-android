package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.kyralabs.pingogram.core.domain.model.Contrast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.abs

class PingogramColorsTest {
    // Expected values from Blend.harmonize in @material/material-color-utilities.
    @ParameterizedTest
    @CsvSource(
        "FF006E1C, FF8D4A5B, FF426900",
        "FF78DC77, FFFFB1C2, FFA0D756",
        "FF006E1C, FF0061A4, FF006C43",
        "FF78DC77, FF9ECAFF, FF45DF97",
    )
    fun `harmonizes like material-color-utilities`(
        design: String,
        source: String,
        expected: String,
    ) {
        val harmonized = harmonize(design.toColor(), source.toColor())

        assertClose(expected.toColor(), harmonized)
    }

    @Test
    fun `leaves a color unchanged when its hue already matches`() {
        assertClose(OnlineLight, harmonize(OnlineLight, OnlineLight))
    }

    @Test
    fun `online is the Material green harmonized toward the scheme's primary`() {
        val light = pingogramColors(brandColorScheme(dark = false, Contrast.Standard), dark = false)
        val dark = pingogramColors(brandColorScheme(dark = true, Contrast.Standard), dark = true)

        assertClose("FF006C43".toColor(), light.online)
        assertClose("FF45DF97".toColor(), dark.online)
    }

    @Test
    fun `online follows a dynamic primary`() {
        val pink = pingogramColors(lightColorScheme(primary = "FF8D4A5B".toColor()), dark = false)
        val blue = pingogramColors(lightColorScheme(primary = "FF0061A4".toColor()), dark = false)

        assertNotEquals(pink.online, blue.online)
    }

    @Test
    fun `picks avatar placeholder colors by id, negative chat ids included`() {
        val scheme = brandColorScheme(dark = false, Contrast.Standard)
        val supergroupId = -1_001_234_567_890L

        assertEquals(
            AvatarColors(scheme.primaryContainer, scheme.onPrimaryContainer),
            scheme.avatarPlaceholderColors(0),
        )
        assertEquals(
            AvatarColors(scheme.surfaceContainerHighest, scheme.onSurfaceVariant),
            scheme.avatarPlaceholderColors(-1),
        )
        assertEquals(scheme.avatarPlaceholderColors(supergroupId), scheme.avatarPlaceholderColors(supergroupId + 4))
    }

    @Test
    fun `avatar placeholders never use the fixed roles, which keep their light tone in dark theme`() {
        val scheme = brandColorScheme(dark = true, Contrast.Standard)
        val fixed = setOf(scheme.primaryFixed, scheme.secondaryFixed, scheme.tertiaryFixed)

        (0L until 4L).forEach { id -> assertFalse(scheme.avatarPlaceholderColors(id).container in fixed) }
    }

    @Test
    fun `avatar placeholders on a selected row are primary`() {
        val scheme = brandColorScheme(dark = false, Contrast.Standard)

        assertEquals(AvatarColors(scheme.primary, scheme.onPrimary), scheme.avatarPlaceholderColorsOnSelection())
    }

    @Test
    fun `picks sender name colors by id among primary, secondary and tertiary`() {
        val scheme = brandColorScheme(dark = false, Contrast.Standard)

        assertEquals(
            listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primary),
            (0L..3L).map(scheme::senderNameColor),
        )
        assertEquals(scheme.tertiary, scheme.senderNameColor(-1))
    }

    @Test
    fun `measures contrast like WCAG`() {
        assertEquals(21f, Color.White.contrastWith(Color.Black), 0.01f)
        assertEquals(1f, OnlineLight.contrastWith(OnlineLight), 0.01f)
        assertEquals(4.98f, "FF8D4A5B".toColor().contrastWith("FFFFD9E0".toColor()), 0.01f)
    }

    @Test
    fun `bubbles take their roles and accent content with primary in the standard schemes`() {
        listOf(false, true).forEach { dark ->
            val scheme = brandColorScheme(dark, Contrast.Standard)
            val colors = pingogramColors(scheme, dark)

            assertEquals(scheme.surfaceContainerHigh, colors.incomingBubble.container)
            assertEquals(scheme.onSurface, colors.incomingBubble.content)
            assertEquals(scheme.onSurfaceVariant, colors.incomingBubble.secondaryContent)
            assertEquals(scheme.surfaceContainerHighest, colors.incomingBubble.block)
            assertEquals(scheme.primaryContainer, colors.outgoingBubble.container)
            assertEquals(scheme.onPrimaryContainer, colors.outgoingBubble.content)
            assertEquals(scheme.onPrimaryContainer, colors.outgoingBubble.secondaryContent)
            assertEquals(scheme.surfaceContainerLow, colors.outgoingBubble.block)
            listOf(colors.incomingBubble, colors.outgoingBubble).forEach { bubble ->
                assertEquals(scheme.primary, bubble.accent)
                assertEquals(scheme.onPrimary, bubble.onAccent)
                assertEquals(scheme.error, bubble.wrong)
            }
        }
    }

    @Test
    fun `an outgoing bubble accents with its content color where primary loses contrast`() {
        val scheme = brandColorScheme(dark = false, Contrast.High)
        val outgoing = pingogramColors(scheme, dark = false).outgoingBubble

        assertEquals(scheme.onPrimaryContainer, outgoing.accent)
        assertEquals(scheme.primaryContainer, outgoing.onAccent)
        assertEquals(scheme.onPrimaryContainer, outgoing.wrong)
    }

    @ParameterizedTest
    @CsvSource("false, Standard", "false, Medium", "false, High", "true, Standard", "true, Medium", "true, High")
    fun `every bubble accent keeps 3 to 1 against its bubble`(
        dark: Boolean,
        contrast: Contrast,
    ) {
        val colors = pingogramColors(brandColorScheme(dark, contrast), dark)

        listOf(colors.incomingBubble, colors.outgoingBubble).forEach { bubble ->
            listOf(bubble.accent, bubble.correct, bubble.wrong).forEach { accent ->
                assertTrue(accent.contrastWith(bubble.container) >= 3f, "$accent on ${bubble.container}")
            }
            assertTrue(bubble.onAccent.contrastWith(bubble.accent) >= 3f)
        }
    }

    private fun String.toColor() = Color(toLong(radix = 16))

    private fun assertClose(
        expected: Color,
        actual: Color,
    ) {
        val (e, a) = expected.toArgb() to actual.toArgb()
        val channelDifference = listOf(16, 8, 0).maxOf { shift -> abs((e shr shift and 0xFF) - (a shr shift and 0xFF)) }
        assertTrue(channelDifference <= 1, "expected ${Integer.toHexString(e)}, was ${Integer.toHexString(a)}")
    }
}
