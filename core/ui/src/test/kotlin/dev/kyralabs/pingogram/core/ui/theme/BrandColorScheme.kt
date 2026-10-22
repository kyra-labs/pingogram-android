package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemePalette

/** The brand color theme's scheme (`docs/DESIGN_SYSTEM.md` §2.1), which most theme tests check against. */
internal fun brandColorScheme(
    dark: Boolean,
    contrast: Contrast,
): ColorScheme = themeColorScheme(ThemePalette.Pingogram, dark, contrast)
