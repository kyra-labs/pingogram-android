package dev.kyralabs.pingogram.core.ui.theme.mcu

import org.junit.jupiter.api.Test

/**
 * SchemeContent's fixed roles come out as material-color-utilities 0.4.0 makes them, for four seeds in light and dark
 * theme at standard, medium and high contrast (`scheme_content_fixed.txt`).
 */
class MaterialFixedColorsTest {
    @Test
    fun `fixed roles match material-color-utilities for every seed, theme and contrast level`() {
        with(MaterialFixedColors) {
            assertSchemeTable(
                "scheme_content_fixed.txt",
                mapOf(
                    "primaryFixed" to primaryFixed,
                    "primaryFixedDim" to primaryFixedDim,
                    "onPrimaryFixed" to onPrimaryFixed,
                    "onPrimaryFixedVariant" to onPrimaryFixedVariant,
                    "secondaryFixed" to secondaryFixed,
                    "secondaryFixedDim" to secondaryFixedDim,
                    "onSecondaryFixed" to onSecondaryFixed,
                    "onSecondaryFixedVariant" to onSecondaryFixedVariant,
                    "tertiaryFixed" to tertiaryFixed,
                    "tertiaryFixedDim" to tertiaryFixedDim,
                    "onTertiaryFixed" to onTertiaryFixed,
                    "onTertiaryFixedVariant" to onTertiaryFixedVariant,
                ),
            )
        }
    }
}
