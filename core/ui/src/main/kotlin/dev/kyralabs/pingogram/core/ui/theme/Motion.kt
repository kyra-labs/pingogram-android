package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.material3.MotionScheme
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
 * Material's fade-through (§5), between content of the same level (tabs, folders): the incoming content fades in on
 * the [motionScheme]'s default effects spring while it grows from [FADE_THROUGH_INITIAL_SCALE] on its default spatial
 * spring, as the outgoing content leaves with [fadeThroughExit]. Springs cannot be delayed, so the faster exit spring
 * stands in for the spec's sequential timing.
 */
fun fadeThroughEnter(motionScheme: MotionScheme): EnterTransition =
    fadeIn(motionScheme.defaultEffectsSpec()) +
        scaleIn(motionScheme.defaultSpatialSpec(), initialScale = FADE_THROUGH_INITIAL_SCALE)

/** The outgoing half of a fade-through ([fadeThroughEnter]): a fade out on the [motionScheme]'s fast effects spring. */
fun fadeThroughExit(motionScheme: MotionScheme): ExitTransition = fadeOut(motionScheme.fastEffectsSpec())

/** The scale incoming content grows from in a fade-through ([fadeThroughEnter]): 92%. */
const val FADE_THROUGH_INITIAL_SCALE = 0.92f

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
