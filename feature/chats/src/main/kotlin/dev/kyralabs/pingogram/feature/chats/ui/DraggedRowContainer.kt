package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline

/**
 * A row being swiped ([dragged]) covers what the swipe reveals from its first frame with its resting container, in the
 * list item's selected shape (`large`): the list item's own container is transparent at rest and would fade in.
 */
@Composable
internal fun Modifier.draggedContainer(dragged: Boolean): Modifier {
    if (!dragged) return this
    val color = ListItemDefaults.colors().containerColor
    val shape = ListItemDefaults.shapes().selectedShape
    return drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), color) }
}
