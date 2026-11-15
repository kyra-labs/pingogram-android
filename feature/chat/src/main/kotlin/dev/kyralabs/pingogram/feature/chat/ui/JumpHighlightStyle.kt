package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline

/** How a jumped-to bubble's highlight looks: a `secondaryContainer` band in the `medium` shape. */
@Immutable
internal class JumpHighlightStyle(
    val color: Color,
    val shape: Shape,
)

/** The theme's [JumpHighlightStyle]. */
@Composable
internal fun rememberJumpHighlightStyle(): JumpHighlightStyle {
    val color = MaterialTheme.colorScheme.secondaryContainer
    val shape = MaterialTheme.shapes.medium
    return remember(color, shape) { JumpHighlightStyle(color, shape) }
}

/**
 * The highlight of a jumped-to bubble ([MessageJumpState.highlightOf]): [style]'s color in its shape behind the row at
 * the highlight's strength, like a selection's state layer. Read in the draw phase; the outline is built once per size.
 */
internal fun Modifier.jumpHighlight(
    state: MessageJumpState,
    bubble: ChatItem.Bubble,
    style: JumpHighlightStyle,
): Modifier =
    drawWithCache {
        val outline = style.shape.createOutline(size, layoutDirection, this)
        onDrawBehind {
            val strength = state.highlightOf(bubble)
            if (strength > 0f) drawOutline(outline, style.color, alpha = strength)
        }
    }
