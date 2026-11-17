package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.TraversableNode.Companion.TraverseDescendantsAction
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.traverseDescendants
import androidx.compose.ui.text.LinkAnnotation

/**
 * Marks a bubble's text, laid out as [layout] holds, as one whose link a long press on its row can find
 * ([linkUnder]): asked only when a long press needs it, never while the list scrolls (`docs/PERFORMANCE.md` §4).
 */
internal fun Modifier.linkTarget(layout: TextLayoutHolder): Modifier = this then LinkTargetElement(layout)

/**
 * The address of the link drawn at [windowPosition] in a text marked with [linkTarget] inside this node; null where
 * there is none.
 */
internal fun DelegatableNode.linkUnder(windowPosition: Offset): String? {
    var link: String? = null
    traverseDescendants(LinkTargetNode.Key) { node ->
        link = (node as? LinkTargetNode)?.linkAt(windowPosition)
        if (link == null) TraverseDescendantsAction.ContinueTraversal else TraverseDescendantsAction.CancelTraversal
    }
    return link
}

private data class LinkTargetElement(
    val layout: TextLayoutHolder,
) : ModifierNodeElement<LinkTargetNode>() {
    override fun create() = LinkTargetNode(layout)

    override fun update(node: LinkTargetNode) {
        node.layout = layout
    }
}

private class LinkTargetNode(
    var layout: TextLayoutHolder,
) : Modifier.Node(),
    TraversableNode {
    override val traverseKey: Any get() = Key

    /** The address of the link drawn at [windowPosition]: on a character of it, not in the space beside a line. */
    fun linkAt(windowPosition: Offset): String? {
        val result = layout.result?.takeIf { isAttached } ?: return null
        val position = requireLayoutCoordinates().windowToLocal(windowPosition)
        val text = result.layoutInput.text
        // The offset nearest the point is before the character under it, or after it in its right half.
        val offset = result.getOffsetForPosition(position)
        val near = listOf(offset, offset - 1).filter { it in text.indices }
        val char = near.firstOrNull { position in result.getBoundingBox(it) }
        val links = char?.let { text.getLinkAnnotations(it, it + 1) }.orEmpty()
        return links.firstNotNullOfOrNull { (it.item as? LinkAnnotation.Url)?.url }
    }

    companion object Key
}
