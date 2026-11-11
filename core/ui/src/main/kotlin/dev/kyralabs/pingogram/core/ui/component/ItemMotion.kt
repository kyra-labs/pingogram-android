package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/**
 * The three specs of a lazy item (`docs/DESIGN_SYSTEM.md` §5 Lists): an item that comes fades in on the theme's
 * default effects spring, one that moves slides on its default spatial spring, and one that goes fades out on its fast
 * effects spring. Without them `Modifier.animateItem()` runs Compose's own springs, which no theme changes.
 *
 * Read once per list with [rememberItemMotion], outside its items, and pass it to [animateItem].
 */
@Immutable
class ItemMotion internal constructor(
    val fadeIn: FiniteAnimationSpec<Float>,
    val placement: FiniteAnimationSpec<IntOffset>,
    val fadeOut: FiniteAnimationSpec<Float>,
) {
    internal constructor(motionScheme: MotionScheme) : this(
        fadeIn = motionScheme.defaultEffectsSpec(),
        placement = motionScheme.defaultSpatialSpec(),
        fadeOut = motionScheme.fastEffectsSpec(),
    )
}

/** The theme's [ItemMotion], made once per motion scheme. */
@Composable
fun rememberItemMotion(): ItemMotion {
    val motionScheme = MaterialTheme.motionScheme
    return remember(motionScheme) { ItemMotion(motionScheme) }
}

/**
 * Animates this item of a lazy list as it comes, moves and goes, on [motion]'s specs; [placement] false leaves its
 * moves to the list (an item a finger drags, which follows the finger instead).
 */
fun Modifier.animateItem(
    scope: LazyItemScope,
    motion: ItemMotion,
    placement: Boolean = true,
): Modifier =
    with(scope) {
        this@animateItem.animateItem(
            fadeInSpec = motion.fadeIn,
            placementSpec = if (placement) motion.placement else null,
            fadeOutSpec = motion.fadeOut,
        )
    }

/** [animateItem] for an item of a lazy grid. */
fun Modifier.animateItem(
    scope: LazyGridItemScope,
    motion: ItemMotion,
    placement: Boolean = true,
): Modifier =
    with(scope) {
        this@animateItem.animateItem(
            fadeInSpec = motion.fadeIn,
            placementSpec = if (placement) motion.placement else null,
            fadeOutSpec = motion.fadeOut,
        )
    }
