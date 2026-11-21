package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import dev.kyralabs.pingogram.core.domain.model.Contrast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [contentColorScheme] fills every role of Compose's color scheme with SchemeContent's color for that role, as
 * material-color-utilities 0.4.0 makes it; the expected colors were printed by that library, in [ROLES]' order.
 */
class ContentColorSchemeTest {
    @Test
    fun `fills every role from SchemeContent, light at standard contrast`() {
        assertScheme(
            contentColorScheme(Color(0xFFB4744B), dark = false, Contrast.Standard),
            "864E28 FFFFFF A3663E FFFBFF FFB68A 765846 FFFFFF FED4BD 795A48 5E5F1D FFFFFF 777834 FFFBFF FFF8F5 " +
                "201A17 FFF8F5 201A17 F4DED3 52443C 89502A 362F2B FBEEE8 BA1A1A FFFFFF FFDAD6 93000A 85746A D7C2B8 " +
                "000000 FFF8F5 F8EBE5 F2E6E0 EDE0DA FEF1EB FFFFFF E4D8D2 FFDBC8 FFB68A 321300 6C3915 FFDBC8 E6BEA8 " +
                "2B1608 5C4130 E7E796 CACB7D 1C1D00 484908",
        )
    }

    @Test
    fun `fills every role from SchemeContent, dark at high contrast`() {
        assertScheme(
            contentColorScheme(Color(0xFF2156C4), dark = true, Contrast.High),
            "EDEFFF 000000 ADC1FF 000927 0040A6 EDEFFF 000000 B2C1F2 000927 FFEAFE 000000 F0A9FF 1A0023 11131A " +
                "E2E2EB 11131A FFFFFF 434653 FFFFFF B3C5FF E2E2EB 000000 FFECE9 000000 FFAEA4 220001 EDEFFF BFC2D1 " +
                "000000 4E5058 2E3037 393B43 45464E 1D1F26 000000 11131A DAE1FF B3C5FF 000000 000E33 DAE1FF B6C5F6 " +
                "000000 000E33 FCD7FF F2AFFF 000000 23002F",
        )
    }

    private fun assertScheme(
        scheme: ColorScheme,
        expected: String,
    ) {
        val colors = expected.split(" ").map { Color(0xFF000000 or it.toLong(16)) }
        ROLES.forEachIndexed { index, (name, role) -> assertEquals(colors[index], role(scheme), name) }
    }

    private companion object {
        val ROLES: List<Pair<String, (ColorScheme) -> Color>> =
            listOf(
                "primary" to { it.primary },
                "onPrimary" to { it.onPrimary },
                "primaryContainer" to { it.primaryContainer },
                "onPrimaryContainer" to { it.onPrimaryContainer },
                "inversePrimary" to { it.inversePrimary },
                "secondary" to { it.secondary },
                "onSecondary" to { it.onSecondary },
                "secondaryContainer" to { it.secondaryContainer },
                "onSecondaryContainer" to { it.onSecondaryContainer },
                "tertiary" to { it.tertiary },
                "onTertiary" to { it.onTertiary },
                "tertiaryContainer" to { it.tertiaryContainer },
                "onTertiaryContainer" to { it.onTertiaryContainer },
                "background" to { it.background },
                "onBackground" to { it.onBackground },
                "surface" to { it.surface },
                "onSurface" to { it.onSurface },
                "surfaceVariant" to { it.surfaceVariant },
                "onSurfaceVariant" to { it.onSurfaceVariant },
                "surfaceTint" to { it.surfaceTint },
                "inverseSurface" to { it.inverseSurface },
                "inverseOnSurface" to { it.inverseOnSurface },
                "error" to { it.error },
                "onError" to { it.onError },
                "errorContainer" to { it.errorContainer },
                "onErrorContainer" to { it.onErrorContainer },
                "outline" to { it.outline },
                "outlineVariant" to { it.outlineVariant },
                "scrim" to { it.scrim },
                "surfaceBright" to { it.surfaceBright },
                "surfaceContainer" to { it.surfaceContainer },
                "surfaceContainerHigh" to { it.surfaceContainerHigh },
                "surfaceContainerHighest" to { it.surfaceContainerHighest },
                "surfaceContainerLow" to { it.surfaceContainerLow },
                "surfaceContainerLowest" to { it.surfaceContainerLowest },
                "surfaceDim" to { it.surfaceDim },
                "primaryFixed" to { it.primaryFixed },
                "primaryFixedDim" to { it.primaryFixedDim },
                "onPrimaryFixed" to { it.onPrimaryFixed },
                "onPrimaryFixedVariant" to { it.onPrimaryFixedVariant },
                "secondaryFixed" to { it.secondaryFixed },
                "secondaryFixedDim" to { it.secondaryFixedDim },
                "onSecondaryFixed" to { it.onSecondaryFixed },
                "onSecondaryFixedVariant" to { it.onSecondaryFixedVariant },
                "tertiaryFixed" to { it.tertiaryFixed },
                "tertiaryFixedDim" to { it.tertiaryFixedDim },
                "onTertiaryFixed" to { it.onTertiaryFixed },
                "onTertiaryFixedVariant" to { it.onTertiaryFixedVariant },
            )
    }
}
