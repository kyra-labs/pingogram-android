package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp

/**
 * [target], cross-fading to it from the scheme shown before whenever it changes (a theme, dynamic color, contrast or
 * AMOLED setting): every color role moves on [animationSpec], the motion scheme's default effects spring for color
 * (`docs/DESIGN_SYSTEM.md` §5). A change during a crossfade continues from the colors on screen. The first scheme
 * shows at once.
 *
 * Each frame of a crossfade is a new [ColorScheme], which recomposes what reads colors: only while the colors change,
 * after a user changed a setting.
 */
@Composable
internal fun animateColorScheme(
    target: ColorScheme,
    animationSpec: FiniteAnimationSpec<Float>,
): ColorScheme {
    val transition = remember { ColorSchemeTransition(target) }
    LaunchedEffect(target) { transition.animateTo(target, animationSpec) }
    return transition.current
}

@Stable
private class ColorSchemeTransition(
    initial: ColorScheme,
) {
    private var from by mutableStateOf(initial)
    private var to by mutableStateOf(initial)
    private val progress = Animatable(1f)

    /** The scheme on screen: [to] once the crossfade is over. */
    val current: ColorScheme
        get() = progress.value.let { fraction -> if (fraction >= 1f) to else lerp(from, to, fraction) }

    suspend fun animateTo(
        target: ColorScheme,
        animationSpec: FiniteAnimationSpec<Float>,
    ) {
        if (target == to) return
        from = current
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec)
    }
}

/**
 * Every color role of [start] moved [fraction] of the way to [stop]'s (in Oklab, as Compose's [lerp] does). A role
 * that either scheme leaves unspecified takes [stop]'s.
 */
@Suppress("LongMethod") // One line per color role of ColorScheme.
internal fun lerp(
    start: ColorScheme,
    stop: ColorScheme,
    fraction: Float,
): ColorScheme {
    fun mix(
        from: Color,
        to: Color,
    ) = if (from.isSpecified && to.isSpecified) lerp(from, to, fraction) else to
    return stop.copy(
        primary = mix(start.primary, stop.primary),
        onPrimary = mix(start.onPrimary, stop.onPrimary),
        primaryContainer = mix(start.primaryContainer, stop.primaryContainer),
        onPrimaryContainer = mix(start.onPrimaryContainer, stop.onPrimaryContainer),
        inversePrimary = mix(start.inversePrimary, stop.inversePrimary),
        secondary = mix(start.secondary, stop.secondary),
        onSecondary = mix(start.onSecondary, stop.onSecondary),
        secondaryContainer = mix(start.secondaryContainer, stop.secondaryContainer),
        onSecondaryContainer = mix(start.onSecondaryContainer, stop.onSecondaryContainer),
        tertiary = mix(start.tertiary, stop.tertiary),
        onTertiary = mix(start.onTertiary, stop.onTertiary),
        tertiaryContainer = mix(start.tertiaryContainer, stop.tertiaryContainer),
        onTertiaryContainer = mix(start.onTertiaryContainer, stop.onTertiaryContainer),
        background = mix(start.background, stop.background),
        onBackground = mix(start.onBackground, stop.onBackground),
        surface = mix(start.surface, stop.surface),
        onSurface = mix(start.onSurface, stop.onSurface),
        surfaceVariant = mix(start.surfaceVariant, stop.surfaceVariant),
        onSurfaceVariant = mix(start.onSurfaceVariant, stop.onSurfaceVariant),
        surfaceTint = mix(start.surfaceTint, stop.surfaceTint),
        inverseSurface = mix(start.inverseSurface, stop.inverseSurface),
        inverseOnSurface = mix(start.inverseOnSurface, stop.inverseOnSurface),
        error = mix(start.error, stop.error),
        onError = mix(start.onError, stop.onError),
        errorContainer = mix(start.errorContainer, stop.errorContainer),
        onErrorContainer = mix(start.onErrorContainer, stop.onErrorContainer),
        outline = mix(start.outline, stop.outline),
        outlineVariant = mix(start.outlineVariant, stop.outlineVariant),
        scrim = mix(start.scrim, stop.scrim),
        surfaceBright = mix(start.surfaceBright, stop.surfaceBright),
        surfaceDim = mix(start.surfaceDim, stop.surfaceDim),
        surfaceContainer = mix(start.surfaceContainer, stop.surfaceContainer),
        surfaceContainerHigh = mix(start.surfaceContainerHigh, stop.surfaceContainerHigh),
        surfaceContainerHighest = mix(start.surfaceContainerHighest, stop.surfaceContainerHighest),
        surfaceContainerLow = mix(start.surfaceContainerLow, stop.surfaceContainerLow),
        surfaceContainerLowest = mix(start.surfaceContainerLowest, stop.surfaceContainerLowest),
        primaryFixed = mix(start.primaryFixed, stop.primaryFixed),
        primaryFixedDim = mix(start.primaryFixedDim, stop.primaryFixedDim),
        onPrimaryFixed = mix(start.onPrimaryFixed, stop.onPrimaryFixed),
        onPrimaryFixedVariant = mix(start.onPrimaryFixedVariant, stop.onPrimaryFixedVariant),
        secondaryFixed = mix(start.secondaryFixed, stop.secondaryFixed),
        secondaryFixedDim = mix(start.secondaryFixedDim, stop.secondaryFixedDim),
        onSecondaryFixed = mix(start.onSecondaryFixed, stop.onSecondaryFixed),
        onSecondaryFixedVariant = mix(start.onSecondaryFixedVariant, stop.onSecondaryFixedVariant),
        tertiaryFixed = mix(start.tertiaryFixed, stop.tertiaryFixed),
        tertiaryFixedDim = mix(start.tertiaryFixedDim, stop.tertiaryFixedDim),
        onTertiaryFixed = mix(start.onTertiaryFixed, stop.onTertiaryFixed),
        onTertiaryFixedVariant = mix(start.onTertiaryFixedVariant, stop.onTertiaryFixedVariant),
    )
}
