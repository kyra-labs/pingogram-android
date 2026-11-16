package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.kyralabs.pingogram.core.ui.theme.growInVertically
import dev.kyralabs.pingogram.core.ui.theme.shrinkOutVertically

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

/**
 * `core:ui`'s `GrowingVisibility` on [callMotionScheme]: content that takes room as it comes and gives it back as it
 * goes, growing on the default spatial spring with a fade in and shrinking away with a fast fade out, at once while
 * power saving holds the call screens still.
 */
@Composable
internal fun CallGrowingVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    expandFrom: Alignment.Vertical = Alignment.Top,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val motion = callMotionScheme()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = growInVertically(motion, expandFrom),
        exit = shrinkOutVertically(motion, expandFrom),
        content = content,
    )
}
