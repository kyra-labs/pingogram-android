package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * Shows this content at [fraction] of its width (0 hides it, values past 1 leave room after it), clipped, the fraction
 * read in the layout phase, so an animated fraction recomposes nothing (`docs/PERFORMANCE.md` §4).
 *
 * Unlike `AnimatedVisibility`'s horizontal expand and shrink, whose intrinsic width stays the content's full width,
 * its intrinsic width is the width it takes: a container that sizes itself from its content's intrinsic width (the
 * floating toolbar) follows the reveal instead of keeping room for the whole content.
 */
fun Modifier.revealHorizontally(fraction: () -> Float): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0))
        val width = (placeable.width * fraction()).roundToInt().coerceAtLeast(0)
        layout(width, placeable.height) { placeable.placeRelative(0, 0) }
    }

/** [revealHorizontally] down: shows this content at [fraction] of its height, from its top, clipped. */
fun Modifier.revealVertically(fraction: () -> Float): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        val height = (placeable.height * fraction()).roundToInt().coerceAtLeast(0)
        layout(placeable.width, height) { placeable.placeRelative(0, 0) }
    }
