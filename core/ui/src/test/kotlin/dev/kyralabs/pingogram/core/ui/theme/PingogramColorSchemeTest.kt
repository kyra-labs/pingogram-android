package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.max
import kotlin.math.min

class PingogramColorSchemeTest {
    // Stand-ins for the platform's Material You schemes.
    private val dynamicLight = lightColorScheme(primary = Color(0xFF00658E))
    private val dynamicDark = darkColorScheme(primary = Color(0xFF7DD0FF))

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `uses the dynamic scheme when one is available`(dark: Boolean) {
        val dynamic = if (dark) dynamicDark else dynamicLight

        val scheme = select(dark = dark, dynamicScheme = dynamic)

        assertSame(dynamic, scheme)
    }

    @ParameterizedTest
    @EnumSource(Contrast::class)
    fun `uses the brand scheme for the contrast level without dynamic color`(contrast: Contrast) {
        assertSame(brandColorScheme(dark = false, contrast), select(dark = false, contrast = contrast))
        assertSame(brandColorScheme(dark = true, contrast), select(dark = true, contrast = contrast))
    }

    @ParameterizedTest
    @EnumSource(Contrast::class)
    fun `keeps the dynamic scheme at any contrast when the platform applies contrast to it`(contrast: Contrast) {
        val scheme = select(contrast = contrast, dynamicScheme = dynamicLight, dynamicSchemeFollowsContrast = true)

        assertSame(dynamicLight, scheme)
    }

    @ParameterizedTest
    @EnumSource(value = Contrast::class, names = ["Medium", "High"])
    fun `prefers the brand scheme when the dynamic scheme cannot follow the contrast level`(contrast: Contrast) {
        val scheme = select(contrast = contrast, dynamicScheme = dynamicLight, dynamicSchemeFollowsContrast = false)

        assertSame(brandColorScheme(dark = false, contrast), scheme)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `brand schemes raise contrast with each level`(dark: Boolean) {
        val ratios =
            Contrast.entries.map { contrast ->
                val scheme = brandColorScheme(dark, contrast)
                contrastRatio(scheme.onSurfaceVariant, scheme.surface)
            }

        assertTrue(ratios[0] < ratios[1] && ratios[1] < ratios[2], "contrast ratios $ratios")
    }

    @Test
    fun `AMOLED makes the base surfaces true black`() {
        val scheme = select(dark = true, amoled = true)

        listOf(scheme.background, scheme.surface, scheme.surfaceDim, scheme.surfaceContainerLowest).forEach {
            assertEquals(Color.Black, it)
        }
    }

    @Test
    fun `AMOLED moves every container one step down the tonal ladder`() {
        val dark = brandColorScheme(dark = true, Contrast.Standard)

        val amoled = select(dark = true, amoled = true)

        assertEquals(dark.surfaceContainerLowest, amoled.surfaceContainerLow)
        assertEquals(dark.surfaceContainerLow, amoled.surfaceContainer)
        assertEquals(dark.surfaceContainer, amoled.surfaceContainerHigh)
        assertEquals(dark.surfaceContainerHigh, amoled.surfaceContainerHighest)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `AMOLED leaves every other role unchanged, content roles included`(dynamic: Boolean) {
        val base = if (dynamic) dynamicDark else brandColorScheme(dark = true, Contrast.Standard)

        val amoled = select(dark = true, amoled = true, dynamicScheme = base.takeIf { dynamic })

        (ALL_ROLES - AMOLED_ROLES).forEach { role ->
            assertEquals(role.get(base), role.get(amoled), role.name)
        }
    }

    @Test
    fun `AMOLED has no effect in the light theme`() {
        assertSame(brandColorScheme(dark = false, Contrast.Standard), select(dark = false, amoled = true))
    }

    @ParameterizedTest
    @EnumSource(ThemePalette::class)
    fun `without dynamic color uses the color theme's scheme`(palette: ThemePalette) {
        assertSame(themeColorScheme(palette, dark = false, Contrast.Standard), select(palette = palette))
        assertSame(
            themeColorScheme(palette, dark = true, Contrast.High),
            select(dark = true, Contrast.High, palette = palette),
        )
    }

    @Test
    fun `dynamic colors without contrast levels give way to the color theme, not the brand`() {
        val scheme =
            select(
                contrast = Contrast.Medium,
                dynamicScheme = dynamicLight,
                dynamicSchemeFollowsContrast = false,
                palette = ThemePalette.Teal,
            )

        assertSame(themeColorScheme(ThemePalette.Teal, dark = false, Contrast.Medium), scheme)
    }

    @ParameterizedTest
    @EnumSource(ThemePalette::class)
    fun `AMOLED black works with every color theme`(palette: ThemePalette) {
        val scheme = select(dark = true, amoled = true, palette = palette)

        assertEquals(Color.Black, scheme.surface)
        assertEquals(themeColorScheme(palette, dark = true, Contrast.Standard).onSurface, scheme.onSurface)
    }

    @Test
    fun `maps the system contrast level like Material Components`() {
        assertEquals(Contrast.Standard, contrastFromSystemLevel(-1f))
        assertEquals(Contrast.Standard, contrastFromSystemLevel(0f))
        assertEquals(Contrast.Medium, contrastFromSystemLevel(0.5f))
        assertEquals(Contrast.High, contrastFromSystemLevel(1f))
    }

    private fun select(
        dark: Boolean = false,
        contrast: Contrast = Contrast.Standard,
        amoled: Boolean = false,
        dynamicScheme: ColorScheme? = null,
        dynamicSchemeFollowsContrast: Boolean = false,
        palette: ThemePalette = ThemePalette.Pingogram,
    ): ColorScheme = pingogramColorScheme(dark, palette, contrast, amoled, dynamicScheme, dynamicSchemeFollowsContrast)

    private fun contrastRatio(
        first: Color,
        second: Color,
    ): Float {
        val lighter = max(first.luminance(), second.luminance())
        val darker = min(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private companion object {
        val AMOLED_ROLES =
            listOf(
                ColorScheme::background,
                ColorScheme::surface,
                ColorScheme::surfaceDim,
                ColorScheme::surfaceContainerLowest,
                ColorScheme::surfaceContainerLow,
                ColorScheme::surfaceContainer,
                ColorScheme::surfaceContainerHigh,
                ColorScheme::surfaceContainerHighest,
            )

        val ALL_ROLES =
            AMOLED_ROLES +
                listOf(
                    ColorScheme::primary,
                    ColorScheme::onPrimary,
                    ColorScheme::primaryContainer,
                    ColorScheme::onPrimaryContainer,
                    ColorScheme::inversePrimary,
                    ColorScheme::secondary,
                    ColorScheme::onSecondary,
                    ColorScheme::secondaryContainer,
                    ColorScheme::onSecondaryContainer,
                    ColorScheme::tertiary,
                    ColorScheme::onTertiary,
                    ColorScheme::tertiaryContainer,
                    ColorScheme::onTertiaryContainer,
                    ColorScheme::onBackground,
                    ColorScheme::onSurface,
                    ColorScheme::surfaceVariant,
                    ColorScheme::onSurfaceVariant,
                    ColorScheme::surfaceTint,
                    ColorScheme::inverseSurface,
                    ColorScheme::inverseOnSurface,
                    ColorScheme::error,
                    ColorScheme::onError,
                    ColorScheme::errorContainer,
                    ColorScheme::onErrorContainer,
                    ColorScheme::outline,
                    ColorScheme::outlineVariant,
                    ColorScheme::scrim,
                    ColorScheme::surfaceBright,
                    ColorScheme::primaryFixed,
                    ColorScheme::primaryFixedDim,
                    ColorScheme::onPrimaryFixed,
                    ColorScheme::onPrimaryFixedVariant,
                    ColorScheme::secondaryFixed,
                    ColorScheme::secondaryFixedDim,
                    ColorScheme::onSecondaryFixed,
                    ColorScheme::onSecondaryFixedVariant,
                    ColorScheme::tertiaryFixed,
                    ColorScheme::tertiaryFixedDim,
                    ColorScheme::onTertiaryFixed,
                    ColorScheme::onTertiaryFixedVariant,
                )
    }
}
