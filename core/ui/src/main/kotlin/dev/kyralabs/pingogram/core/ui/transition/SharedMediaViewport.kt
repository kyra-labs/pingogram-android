package dev.kyralabs.pingogram.core.ui.transition

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints

/**
 * The part of the window where a conversation shows its messages: the list's bounds without what its [padding] keeps
 * clear for the bars drawn over it (the composer, the typing row). While a photo, video or GIF moves between its bubble
 * and the media viewer ([sharedMediaBounds], [sharedMediaViewerBounds]), both sides are clipped to it as the media
 * nears the bubble, so the media never draws over the top bar, the pinned bar or the composer on its way back, and
 * comes out from under them on its way in. Put it on the message list; the bounds are taken in the shared transition's
 * coordinates as the list is placed, never while it scrolls its items.
 */
fun Modifier.sharedMediaViewport(padding: PaddingValues): Modifier = this then SharedMediaViewportElement(padding)

/** The viewports the conversations on screen published, the last placed winning: the media viewer opens over one. */
internal class SharedMediaViewports {
    private val nodes = ArrayList<SharedMediaViewportNode>(1)

    /** The viewport the media's way between bubble and viewer is clipped to; null while no conversation shows. */
    val current: Rect? get() = nodes.lastOrNull()?.bounds

    fun placed(node: SharedMediaViewportNode) {
        if (nodes.lastOrNull() === node) return
        nodes.remove(node)
        nodes += node
    }

    fun removed(node: SharedMediaViewportNode) {
        nodes.remove(node)
    }
}

/**
 * The clip a shared media's sides take toward the conversation's viewport ([viewport], in the transition's
 * coordinates): none while the media is at the viewer ([atBubble] 0), the viewport once it is at its bubble
 * ([atBubble] 1), a rectangle between the two meanwhile, which also holds [bounds] at first, so nothing is cut as the
 * transform starts. Null where there is no viewport or the media is at the viewer.
 */
internal fun viewportClip(
    viewport: Rect?,
    bounds: Rect,
    atBubble: Float,
): Rect? {
    val fraction = atBubble.coerceIn(0f, 1f)
    if (viewport == null || fraction == 0f) return null
    val all =
        Rect(
            minOf(viewport.left, bounds.left),
            minOf(viewport.top, bounds.top),
            maxOf(viewport.right, bounds.right),
            maxOf(viewport.bottom, bounds.bottom),
        )
    return lerp(all, viewport, fraction)
}

private data class SharedMediaViewportElement(
    val padding: PaddingValues,
) : ModifierNodeElement<SharedMediaViewportNode>() {
    override fun create() = SharedMediaViewportNode(padding)

    override fun update(node: SharedMediaViewportNode) {
        node.padding = padding
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "sharedMediaViewport"
        properties["padding"] = padding
    }
}

internal class SharedMediaViewportNode(
    var padding: PaddingValues,
) : Modifier.Node(),
    LayoutModifierNode,
    CompositionLocalConsumerModifierNode {
    /** The list's bounds without its [padding], in the shared transition's coordinates; null until placed in one. */
    var bounds: Rect? = null
        private set

    private var viewports: SharedMediaViewports? = null

    /** Sets [bounds] as placing the node would, for the clip's tests. */
    @VisibleForTesting
    internal fun setBoundsForTest(bounds: Rect) {
        this.bounds = bounds
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
            if (isLookingAhead) return@layout
            val scope = currentValueOf(LocalSharedTransitionScope) ?: return@layout
            val shapes = currentValueOf(LocalSharedMediaShapes) ?: return@layout
            val coordinates = coordinates ?: return@layout
            val box = with(scope) { lookaheadScopeCoordinates }.localBoundingBoxOf(coordinates, clipBounds = false)
            bounds =
                Rect(
                    box.left,
                    box.top + padding.calculateTopPadding().toPx(),
                    box.right,
                    box.bottom - padding.calculateBottomPadding().toPx(),
                )
            if (viewports !== shapes.viewports) viewports?.removed(this@SharedMediaViewportNode)
            viewports = shapes.viewports.also { it.placed(this@SharedMediaViewportNode) }
        }
    }

    override fun onDetach() {
        viewports?.removed(this)
        viewports = null
        bounds = null
    }
}
