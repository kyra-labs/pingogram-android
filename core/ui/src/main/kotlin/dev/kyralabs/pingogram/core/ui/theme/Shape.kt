package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/** The Material 3 Expressive shape scale (`docs/DESIGN_SYSTEM.md` §4). "Full" is `CircleShape`. */
val PingogramShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        largeIncreased = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
        extraLargeIncreased = RoundedCornerShape(32.dp),
        extraExtraLarge = RoundedCornerShape(48.dp),
    )

/**
 * A [Shape] that morphs from [start] to [end] while [morphed] is true and back when it is false
 * (§4: FAB press, toggles, selected avatar, record button). The morph is a spatial spring from
 * `MaterialTheme.motionScheme` by default. Progress is read when the outline is created, so the
 * animation invalidates drawing only, never composition. With animations turned off in the system
 * settings, Compose finishes the spring immediately, which skips the morph.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberMorphShape(
    start: RoundedPolygon,
    end: RoundedPolygon,
    morphed: Boolean,
    animationSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec(),
): Shape {
    val progress = remember { Animatable(if (morphed) 1f else 0f) }
    val spec by rememberUpdatedState(animationSpec)
    LaunchedEffect(morphed) { progress.animateTo(if (morphed) 1f else 0f, spec) }
    return remember(start, end) { MorphShape(Morph(start, end)) { progress.value } }
}

private class MorphShape(
    private val morph: Morph,
    private val progress: () -> Float,
) : Shape {
    private var lastProgress = Float.NaN
    private var lastSize = Size.Unspecified
    private var lastOutline: Outline? = null

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val currentProgress = progress()
        lastOutline?.let { if (currentProgress == lastProgress && size == lastSize) return it }
        // A new Path per frame: outlines compare paths by identity, so reusing one would hide changes.
        val path = morph.toPath(currentProgress, Path())
        path.transform(Matrix().apply { scale(x = size.width, y = size.height) })
        path.translate(size.center - path.getBounds().center)
        return Outline.Generic(path).also {
            lastProgress = currentProgress
            lastSize = size
            lastOutline = it
        }
    }
}
