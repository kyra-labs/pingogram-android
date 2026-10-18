package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Shapes

/**
 * The shape of a message bubble (`docs/DESIGN_SYSTEM.md` §4), from the shape scale: `large` corners, except at the
 * bubble's tail side (the end for outgoing messages, the start for incoming ones, mirrored in right-to-left layouts).
 * There the bottom corner of the last message of a group is `small`, the tail; corners that face another message of
 * the group, above ([joinsPrevious]) or below ([joinsNext]), are `medium`, so a group reads as one stack.
 *
 * It allocates a shape, so remember it, e.g. keyed by the shapes and the arguments.
 *
 * @param joinsPrevious the message above (the previous one) belongs to the same group.
 * @param joinsNext the message below (the next one) belongs to the same group.
 */
fun Shapes.messageBubble(
    isOutgoing: Boolean,
    joinsPrevious: Boolean,
    joinsNext: Boolean,
): CornerBasedShape {
    val tailTop = if (joinsPrevious) medium.topStart else large.topStart
    val tailBottom = if (joinsNext) medium.bottomStart else small.bottomStart
    return if (isOutgoing) {
        large.copy(topEnd = tailTop, bottomEnd = tailBottom)
    } else {
        large.copy(topStart = tailTop, bottomStart = tailBottom)
    }
}
