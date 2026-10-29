package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable

/**
 * The shape of a message bubble (`docs/DESIGN_SYSTEM.md` §4), from Material 3 Expressive's shape scale:
 * `extraLarge` corners, which make a one-line bubble a full pill (corners are capped at half the
 * height), except where the bubble meets another message of its group on the sender's side (the end for outgoing
 * messages, the start for incoming ones, mirrored in right-to-left layouts). There the corners are `extraSmall`: at the
 * bottom of the first message of a group, at both ends of a middle one, at the top of the last one. The far side stays
 * `extraLarge`, and there is no tail.
 *
 * It allocates a shape: use [MessageBubbleShapes], which builds the eight once.
 *
 * @param joinsPrevious the message above (the previous one) belongs to the same group.
 * @param joinsNext the message below (the next one) belongs to the same group.
 */
fun Shapes.messageBubble(
    isOutgoing: Boolean,
    joinsPrevious: Boolean,
    joinsNext: Boolean,
): CornerBasedShape {
    val top = if (joinsPrevious) extraSmall.topStart else extraLarge.topStart
    val bottom = if (joinsNext) extraSmall.bottomStart else extraLarge.bottomStart
    return if (isOutgoing) {
        extraLarge.copy(topEnd = top, bottomEnd = bottom)
    } else {
        extraLarge.copy(topStart = top, bottomStart = bottom)
    }
}

/**
 * The eight bubble shapes of [shapes] ([messageBubble]: either side, and alone, first, middle or last of a group),
 * built once, e.g. once per theme for a whole conversation, so bubbles allocate none as they compose or scroll.
 */
@Immutable
class MessageBubbleShapes(
    shapes: Shapes,
) {
    // Indexed by outgoing (4) + joins previous (2) + joins next (1).
    private val all =
        List(SHAPE_COUNT) { index ->
            shapes.messageBubble(
                isOutgoing = index and OUTGOING != 0,
                joinsPrevious = index and JOINS_PREVIOUS != 0,
                joinsNext = index and JOINS_NEXT != 0,
            )
        }

    /** The shape of a bubble on the [isOutgoing] side, joining the message above and below it as said. */
    fun of(
        isOutgoing: Boolean,
        joinsPrevious: Boolean,
        joinsNext: Boolean,
    ): CornerBasedShape {
        val side = if (isOutgoing) OUTGOING else 0
        return all[side + (if (joinsPrevious) JOINS_PREVIOUS else 0) + (if (joinsNext) JOINS_NEXT else 0)]
    }

    override fun equals(other: Any?): Boolean = other is MessageBubbleShapes && other.all == all

    override fun hashCode(): Int = all.hashCode()

    private companion object {
        const val OUTGOING = 4
        const val JOINS_PREVIOUS = 2
        const val JOINS_NEXT = 1
        const val SHAPE_COUNT = 8
    }
}
