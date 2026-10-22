package dev.kyralabs.pingogram.feature.scanner

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import kotlin.math.min

/**
 * The viewfinder's window: [shape] (a `MaterialShapes` polygon) centred, [WINDOW_FRACTION] of the shorter side and at
 * most [maxSize]; [dim] over everything outside it, its edge outlined in [outline], [outlineWidth] wide.
 */
@Immutable
internal data class ScannerWindow(
    val shape: Shape,
    val dim: Color,
    val outline: Color,
    val outlineWidth: Dp,
    val maxSize: Dp,
)

/**
 * Draws [window] over the content: the dim around its shape and the outline, both built once per size and drawn in the
 * draw phase (`docs/PERFORMANCE.md` §4), so the camera's frames show through the window undimmed.
 */
internal fun Modifier.scannerWindow(window: ScannerWindow): Modifier =
    drawWithCache {
        val side = min(min(size.width, size.height) * WINDOW_FRACTION, window.maxSize.toPx())
        val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2)
        val path =
            Path().apply {
                addOutline(window.shape.createOutline(Size(side, side), layoutDirection, this@drawWithCache))
                translate(topLeft)
            }
        val stroke = Stroke(width = window.outlineWidth.toPx())
        onDrawWithContent {
            drawContent()
            clipPath(path, ClipOp.Difference) { drawRect(window.dim) }
            drawPath(path, window.outline, style = stroke)
        }
    }

/** How much of the screen's shorter side the window takes. */
internal const val WINDOW_FRACTION = 0.72f
