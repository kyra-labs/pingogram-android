package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.ui.theme.mcu.ContentScheme
import dev.kyralabs.pingogram.core.ui.theme.mcu.DynamicColor
import dev.kyralabs.pingogram.core.ui.theme.mcu.Hct
import dev.kyralabs.pingogram.core.ui.theme.mcu.MaterialAccentColors as Accent
import dev.kyralabs.pingogram.core.ui.theme.mcu.MaterialFixedColors as Fixed
import dev.kyralabs.pingogram.core.ui.theme.mcu.MaterialSurfaceColors as Surface

/**
 * The color scheme Material makes from [seed] to keep the colors close to it (SchemeContent's palettes, the 2021 color
 * spec, as the color themes are generated: `docs/adr/0041-chat-colors-from-the-wallpaper.md`), for [dark] theme at
 * [contrast], with its containers at the standard tones (30 dark, 90 light) rather than the seed's own, so a bright
 * wallpaper gives deep containers in dark theme (owner decision 2026-11-22). A conversation with a wallpaper takes its
 * colors from the wallpaper's seed this way. Every role is computed, so it takes a few milliseconds: compute it off
 * the main thread and keep it.
 */
fun contentColorScheme(
    seed: Color,
    dark: Boolean,
    contrast: Contrast,
): ColorScheme {
    val scheme = ContentScheme(Hct.fromInt(seed.toArgb()), dark, contrast.level, fidelity = false)
    val role: (DynamicColor) -> Color = { Color(it.getArgb(scheme)) }
    return lightColorScheme(
        primary = role(Accent.primary),
        onPrimary = role(Accent.onPrimary),
        primaryContainer = role(Accent.primaryContainer),
        onPrimaryContainer = role(Accent.onPrimaryContainer),
        inversePrimary = role(Accent.inversePrimary),
        secondary = role(Accent.secondary),
        onSecondary = role(Accent.onSecondary),
        secondaryContainer = role(Accent.secondaryContainer),
        onSecondaryContainer = role(Accent.onSecondaryContainer),
        tertiary = role(Accent.tertiary),
        onTertiary = role(Accent.onTertiary),
        tertiaryContainer = role(Accent.tertiaryContainer),
        onTertiaryContainer = role(Accent.onTertiaryContainer),
        background = role(Surface.background),
        onBackground = role(Surface.onBackground),
        surface = role(Surface.surface),
        onSurface = role(Surface.onSurface),
        surfaceVariant = role(Surface.surfaceVariant),
        onSurfaceVariant = role(Surface.onSurfaceVariant),
        surfaceTint = role(Surface.surfaceTint),
        inverseSurface = role(Surface.inverseSurface),
        inverseOnSurface = role(Surface.inverseOnSurface),
        error = role(Accent.error),
        onError = role(Accent.onError),
        errorContainer = role(Accent.errorContainer),
        onErrorContainer = role(Accent.onErrorContainer),
        outline = role(Surface.outline),
        outlineVariant = role(Surface.outlineVariant),
        scrim = role(Surface.scrim),
        surfaceBright = role(Surface.surfaceBright),
        surfaceContainer = role(Surface.surfaceContainer),
        surfaceContainerHigh = role(Surface.surfaceContainerHigh),
        surfaceContainerHighest = role(Surface.surfaceContainerHighest),
        surfaceContainerLow = role(Surface.surfaceContainerLow),
        surfaceContainerLowest = role(Surface.surfaceContainerLowest),
        surfaceDim = role(Surface.surfaceDim),
        primaryFixed = role(Fixed.primaryFixed),
        primaryFixedDim = role(Fixed.primaryFixedDim),
        onPrimaryFixed = role(Fixed.onPrimaryFixed),
        onPrimaryFixedVariant = role(Fixed.onPrimaryFixedVariant),
        secondaryFixed = role(Fixed.secondaryFixed),
        secondaryFixedDim = role(Fixed.secondaryFixedDim),
        onSecondaryFixed = role(Fixed.onSecondaryFixed),
        onSecondaryFixedVariant = role(Fixed.onSecondaryFixedVariant),
        tertiaryFixed = role(Fixed.tertiaryFixed),
        tertiaryFixedDim = role(Fixed.tertiaryFixedDim),
        onTertiaryFixed = role(Fixed.onTertiaryFixed),
        onTertiaryFixedVariant = role(Fixed.onTertiaryFixedVariant),
    )
}

/** Material Color Utilities' contrast level: 0 standard, 0.5 medium, 1 high. */
private val Contrast.level: Double
    get() =
        when (this) {
            Contrast.Standard -> 0.0
            Contrast.Medium -> 0.5
            Contrast.High -> 1.0
        }
