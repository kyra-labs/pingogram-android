package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.transition.snapWhenReducedMotion

/**
 * A picked row's background (`docs/DESIGN_SYSTEM.md` §4): the list item's resting [restShape] without a fill, and
 * while [selected] its [selectedShape] filled with [color], the corners morphing on the theme's fast spatial spring
 * and the fill fading in with them. Both are read in the draw phase, so the morph recomposes nothing
 * (`docs/PERFORMANCE.md` §4); at rest it draws the same clip and fill as `clip(shape).background(color)`. With reduced
 * motion it snaps.
 */
@Composable
fun Modifier.selectionBackground(
    selected: Boolean,
    restShape: CornerBasedShape = MaterialTheme.shapes.extraSmall,
    selectedShape: CornerBasedShape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
): Modifier {
    val progress = remember { Animatable(if (selected) 1f else 0f) }
    val spec by rememberUpdatedState(snapWhenReducedMotion(MaterialTheme.motionScheme.fastSpatialSpec<Float>()))
    LaunchedEffect(selected) { progress.animateTo(if (selected) 1f else 0f, spec) }
    val shape = remember(restShape, selectedShape) { MorphingCornerShape(restShape, selectedShape) { progress.value } }
    val fill by rememberUpdatedState(color)
    return clip(shape).drawBehind {
        val alpha = progress.value.coerceIn(0f, 1f)
        if (alpha > 0f) drawRect(fill, alpha = alpha)
    }
}

/**
 * [rest]'s outline at progress 0 and [selected]'s at 1, exactly as those shapes draw them; in between, rounded corners
 * of the radii between theirs. The progress is read when the outline is created, in the draw phase.
 */
private class MorphingCornerShape(
    private val rest: CornerBasedShape,
    private val selected: CornerBasedShape,
    private val progress: () -> Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val fraction = progress()
        return when {
            fraction <= 0f -> {
                rest.createOutline(size, layoutDirection, density)
            }

            fraction == 1f -> {
                selected.createOutline(size, layoutDirection, density)
            }

            else -> {
                val from = rest.topStart.toPx(size, density)
                val to = selected.topStart.toPx(size, density)
                val radius = (from + (to - from) * fraction).coerceAtLeast(0f)
                Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
            }
        }
    }
}
