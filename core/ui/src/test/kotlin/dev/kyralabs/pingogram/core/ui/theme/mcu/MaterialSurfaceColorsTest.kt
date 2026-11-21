package dev.kyralabs.pingogram.core.ui.theme.mcu

import org.junit.jupiter.api.Test

/**
 * SchemeContent's neutral and surface roles come out as material-color-utilities 0.4.0 makes them, for four seeds in
 * light and dark theme at standard, medium and high contrast (`scheme_content_surfaces.txt`).
 */
class MaterialSurfaceColorsTest {
    @Test
    fun `surface roles match material-color-utilities for every seed, theme and contrast level`() {
        with(MaterialSurfaceColors) {
            assertSchemeTable(
                "scheme_content_surfaces.txt",
                mapOf(
                    "background" to background,
                    "onBackground" to onBackground,
                    "surface" to surface,
                    "surfaceDim" to surfaceDim,
                    "surfaceBright" to surfaceBright,
                    "surfaceContainerLowest" to surfaceContainerLowest,
                    "surfaceContainerLow" to surfaceContainerLow,
                    "surfaceContainer" to surfaceContainer,
                    "surfaceContainerHigh" to surfaceContainerHigh,
                    "surfaceContainerHighest" to surfaceContainerHighest,
                    "onSurface" to onSurface,
                    "surfaceVariant" to surfaceVariant,
                    "onSurfaceVariant" to onSurfaceVariant,
                    "inverseSurface" to inverseSurface,
                    "inverseOnSurface" to inverseOnSurface,
                    "outline" to outline,
                    "outlineVariant" to outlineVariant,
                    "shadow" to shadow,
                    "scrim" to scrim,
                    "surfaceTint" to surfaceTint,
                ),
            )
        }
    }
}
