package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch

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

/**
 * The [ItemMotion] of [motionScheme], the theme's by default, made once per scheme. A screen that holds its motion
 * still (the call screens with power saving) passes its own.
 */
@Composable
fun rememberItemMotion(motionScheme: MotionScheme = MaterialTheme.motionScheme): ItemMotion =
    remember(motionScheme) { ItemMotion(motionScheme) }

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

/**
 * Slides this item to a new place in its parent on [motion]'s placement spec, for items outside a lazy layout (a
 * `FlowRow` of tiles a menu reorders): where its parent places it elsewhere than before, it moves there from where it
 * was instead of jumping. Its first place it takes at once. The slide runs in the placement phase, so it recomposes
 * nothing. Give the item a stable `key` in its parent, so its place follows it when the order changes. Coming and going
 * stay the parent's: an item that leaves vanishes, and the others slide into its place.
 */
fun Modifier.animatePlacement(motion: ItemMotion): Modifier = this then AnimatePlacementElement(motion.placement)

private class AnimatePlacementElement(
    private val spec: FiniteAnimationSpec<IntOffset>,
) : ModifierNodeElement<AnimatePlacementNode>() {
    override fun create() = AnimatePlacementNode(spec)

    override fun update(node: AnimatePlacementNode) {
        node.spec = spec
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "animatePlacement"
    }

    override fun equals(other: Any?): Boolean = other is AnimatePlacementElement && other.spec == spec

    override fun hashCode(): Int = spec.hashCode()
}

private class AnimatePlacementNode(
    var spec: FiniteAnimationSpec<IntOffset>,
) : Modifier.Node(),
    LayoutModifierNode {
    /** Where the item shows, following where its parent places it; null until its first placement. */
    private var shown: Animatable<IntOffset, AnimationVector2D>? = null

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            val target = coordinates?.positionInParent()?.round()
            if (target == null || isLookingAhead) {
                placeable.place(0, 0)
                return@layout
            }
            val animatable = shown ?: Animatable(target, IntOffset.VectorConverter).also { shown = it }
            if (animatable.targetValue != target) {
                coroutineScope.launch { animatable.animateTo(target, spec) }
            }
            placeable.place(animatable.value - target)
        }
    }

    override fun onDetach() {
        shown = null
    }
}
