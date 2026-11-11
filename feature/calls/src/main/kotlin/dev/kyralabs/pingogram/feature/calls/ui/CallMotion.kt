package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The motion of the call screens' own animations: the theme's motion scheme, or, while power saving holds the call
 * screens still ([LocalStillCallAnimations], "Stop call screen animations"), [StillMotionScheme], whose every spec
 * snaps to its end, so what would move or fade there takes its end state in the next frame.
 */
@Composable
@ReadOnlyComposable
internal fun callMotionScheme(): MotionScheme =
    if (LocalStillCallAnimations.current) StillMotionScheme else MaterialTheme.motionScheme

/** A motion scheme without motion: every spec a snap. */
internal object StillMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}
