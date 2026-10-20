package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.isSpecified

/**
 * [clickable] on an area at least as tall as Material's minimum touch target ([LocalMinimumInteractiveComponentSize],
 * 48 dp), centred on this content, while the content keeps its own size in the layout: what sits around it does not
 * move (`docs/DESIGN_SYSTEM.md` §4, §9), as for the small badges and labels on a bubble's edge. The area reaches past
 * the content's top and bottom. Narrower than 48 dp, it is widened by the platform's minimum touch target, which gives
 * a touch between two neighbours to the nearer one. [clickable] should draw no indication, which would fill the whole
 * area: the content draws its own state layer, clipped to its shape.
 */
internal fun Modifier.touchTarget(clickable: Modifier): Modifier = this then ContentSize then clickable then TallTarget

/** How far the content sits below the top of its touch area ([touchTarget]), in pixels. */
private val TouchTargetInset = HorizontalAlignmentLine(merger = ::minOf)

/** The touch area laid out at the size of its content, reaching [TouchTargetInset] above and below it. */
private data object ContentSize : ModifierNodeElement<ContentSizeNode>() {
    override fun create() = ContentSizeNode()

    override fun update(node: ContentSizeNode) = Unit
}

private class ContentSizeNode :
    Modifier.Node(),
    LayoutModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
        val inset = placeable[TouchTargetInset].takeIf { it != AlignmentLine.Unspecified } ?: 0
        return layout(placeable.width, constraints.constrainHeight(placeable.height - 2 * inset)) {
            placeable.place(0, -inset)
        }
    }
}

/** The content in the middle of an area at least the minimum touch target tall, which says how far down it is. */
private data object TallTarget : ModifierNodeElement<TallTargetNode>() {
    override fun create() = TallTargetNode()

    override fun update(node: TallTargetNode) = Unit
}

private class TallTargetNode :
    Modifier.Node(),
    LayoutModifierNode,
    CompositionLocalConsumerModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        val minimum = currentValueOf(LocalMinimumInteractiveComponentSize)
        val target = if (minimum.isSpecified) minimum.roundToPx() else 0
        // Rounded up, so the area is the content's height plus the same inset above and below it.
        val inset = ((target - placeable.height).coerceAtLeast(0) + 1) / 2
        return layout(placeable.width, placeable.height + 2 * inset, mapOf(TouchTargetInset to inset)) {
            placeable.place(0, inset)
        }
    }
}
