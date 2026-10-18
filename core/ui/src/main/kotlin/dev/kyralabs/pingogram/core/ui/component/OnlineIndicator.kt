package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch

/**
 * Draws an [Avatar]'s online dot over the content at the bottom end, set off by a ring of [ringColor], and scales it
 * in and out with [animationSpec]. A modifier node rather than a composable with an effect: the scale animates in the
 * node's own coroutine scope and is read only while drawing, so it never recomposes or lays out
 * (`docs/PERFORMANCE.md` §4). Sized in proportion to the content.
 */
internal fun Modifier.onlineIndicator(
    online: Boolean,
    dotColor: Color,
    ringColor: Color,
    animationSpec: FiniteAnimationSpec<Float>,
): Modifier = this then OnlineIndicatorElement(online, dotColor, ringColor, animationSpec)

private data class OnlineIndicatorElement(
    val online: Boolean,
    val dotColor: Color,
    val ringColor: Color,
    val animationSpec: FiniteAnimationSpec<Float>,
) : ModifierNodeElement<OnlineIndicatorNode>() {
    override fun create() = OnlineIndicatorNode(online, dotColor, ringColor, animationSpec)

    override fun update(node: OnlineIndicatorNode) = node.update(online, dotColor, ringColor, animationSpec)
}

private class OnlineIndicatorNode(
    private var online: Boolean,
    private var dotColor: Color,
    private var ringColor: Color,
    private var animationSpec: FiniteAnimationSpec<Float>,
) : Modifier.Node(),
    DrawModifierNode {
    private var scale = Animatable(online.scale)

    // Set when a lazy list reuses the node for another item, which shows its state at once rather than animating.
    private var showNextChangeAtOnce = false

    fun update(
        online: Boolean,
        dotColor: Color,
        ringColor: Color,
        animationSpec: FiniteAnimationSpec<Float>,
    ) {
        this.dotColor = dotColor
        this.ringColor = ringColor
        this.animationSpec = animationSpec
        if (online == this.online) return
        this.online = online
        if (isAttached && !showNextChangeAtOnce) {
            coroutineScope.launch { scale.animateTo(online.scale, animationSpec) }
        } else {
            // A plain field: the update invalidates drawing, which reads the new animatable.
            scale = Animatable(online.scale)
        }
    }

    override fun onReset() {
        showNextChangeAtOnce = true
    }

    // Detaching cancels a running animation; start from the end state when attached again.
    override fun onDetach() {
        scale = Animatable(online.scale)
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        showNextChangeAtOnce = false
        // The spatial spring overshoots, below 0 too when shrinking.
        val scale = scale.value.coerceAtLeast(0f)
        if (scale == 0f) return
        val radius = size.minDimension * INDICATOR_SIZE_RATIO / 2
        val center =
            Offset(
                x = if (layoutDirection == LayoutDirection.Ltr) size.width - radius else radius,
                y = size.height - radius,
            )
        drawCircle(ringColor, radius = radius * scale, center = center)
        drawCircle(dotColor, radius = (radius - size.minDimension * RING_WIDTH_RATIO) * scale, center = center)
    }
}

private val Boolean.scale get() = if (this) 1f else 0f

// The dot with its ring, at the bottom end: its center lies on the avatar's circle, at 45°.
private const val INDICATOR_SIZE_RATIO = 0.3f
private const val RING_WIDTH_RATIO = 0.05f
