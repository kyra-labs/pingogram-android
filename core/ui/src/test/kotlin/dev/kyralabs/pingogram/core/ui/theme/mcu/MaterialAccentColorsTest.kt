package dev.kyralabs.pingogram.core.ui.theme.mcu

import org.junit.jupiter.api.Test

/**
 * SchemeContent's primary, secondary, tertiary and error roles come out as material-color-utilities 0.4.0 makes them,
 * for four seeds in light and dark theme at standard, medium and high contrast (`scheme_content_accents.txt`).
 */
class MaterialAccentColorsTest {
    @Test
    fun `accent roles match material-color-utilities for every seed, theme and contrast level`() {
        with(MaterialAccentColors) {
            assertSchemeTable(
                "scheme_content_accents.txt",
                mapOf(
                    "primary" to primary,
                    "onPrimary" to onPrimary,
                    "primaryContainer" to primaryContainer,
                    "onPrimaryContainer" to onPrimaryContainer,
                    "inversePrimary" to inversePrimary,
                    "secondary" to secondary,
                    "onSecondary" to onSecondary,
                    "secondaryContainer" to secondaryContainer,
                    "onSecondaryContainer" to onSecondaryContainer,
                    "tertiary" to tertiary,
                    "onTertiary" to onTertiary,
                    "tertiaryContainer" to tertiaryContainer,
                    "onTertiaryContainer" to onTertiaryContainer,
                    "error" to error,
                    "onError" to onError,
                    "errorContainer" to errorContainer,
                    "onErrorContainer" to onErrorContainer,
                ),
            )
        }
    }
}
