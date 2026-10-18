package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How far content travels in a Material shared axis transition (`docs/DESIGN_SYSTEM.md` §5): it slides this distance
 * while it fades, on a `MaterialTheme.motionScheme` spatial spring.
 */
val SharedAxisDistance: Dp = 30.dp

/**
 * The scale of a surface that follows a predictive back gesture (§5): from 100% down to 90% as the gesture
 * [progress]es from 0 to 1, along Material's back-progress curve, the one Material 3 components apply to back events.
 */
fun predictiveBackScale(progress: Float): Float =
    1f - (1f - PREDICTIVE_BACK_MIN_SCALE) * PredictiveBackEasing.transform(progress.coerceIn(0f, 1f))

/**
 * Scales this content with a predictive back gesture ([predictiveBackScale]). [progress] is read in the draw phase,
 * so following the gesture never recomposes (`docs/PERFORMANCE.md` §4).
 */
fun Modifier.predictiveBackScale(progress: () -> Float): Modifier =
    graphicsLayer {
        val scale = predictiveBackScale(progress())
        scaleX = scale
        scaleY = scale
    }

private const val PREDICTIVE_BACK_MIN_SCALE = 0.9f

// androidx.compose.material3.internal.PredictiveBack: the curve of Material's predictive back motion.
private val PredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
