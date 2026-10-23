package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlin.math.roundToInt

/**
 * The horizontal scroll of a row of tabs made of a connected button group (`docs/DESIGN_SYSTEM.md` §6: the chat
 * list's folder tabs, the profile's shared media tabs): its [state], and where each button sits in the group
 * ([buttonBounds]), written in the layout phase and read by the scroll, never by composition.
 */
class TabScroll(
    val state: ScrollState,
    val buttonBounds: Array<Rect?>,
)

/**
 * The tabs' scroll, which brings the selected tab ([selectedIndex]) fully into view with the row's [margin] beside it
 * whenever it changes: at once the first time, then on the motion scheme's default spatial spring
 * (`docs/DESIGN_SYSTEM.md` §5). Nothing moves if it is in view already.
 */
@Composable
fun rememberTabScroll(
    tabCount: Int,
    selectedIndex: Int,
    margin: Dp,
): TabScroll {
    val state = rememberScrollState()
    val scroll = remember(state, tabCount) { TabScroll(state, arrayOfNulls(tabCount)) }
    val marginPx = with(LocalDensity.current) { margin.toPx() }
    val layoutDirection = LocalLayoutDirection.current
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val shown = remember { ShownTab() }
    LaunchedEffect(selectedIndex, scroll, layoutDirection) {
        // The buttons are placed in the frame that composes them.
        if (scroll.buttonBounds.getOrNull(selectedIndex) == null) withFrameNanos {}
        val bounds = scroll.buttonBounds.getOrNull(selectedIndex) ?: return@LaunchedEffect
        val target = state.valueShowing(bounds, marginPx, layoutDirection) ?: return@LaunchedEffect
        if (shown.index == null) state.scrollTo(target) else state.animateScrollTo(target, spec)
        shown.index = selectedIndex
    }
    return scroll
}

/** The tab [rememberTabScroll] last brought into view; null before the first. */
private class ShownTab {
    var index: Int? = null
}

/**
 * The scroll value that shows [bounds] (in the group, which starts [margin] in from the scroll's start) with [margin]
 * beside it, or null if it shows already. In right-to-left layouts the scroll runs from the right edge.
 */
private fun ScrollState.valueShowing(
    bounds: Rect,
    margin: Float,
    layoutDirection: LayoutDirection,
): Int? {
    val ltr = layoutDirection == LayoutDirection.Ltr
    val start = if (ltr) bounds.left + margin else maxValue + viewportSize - bounds.right - margin
    val end = start + bounds.width
    return when {
        start - margin < value -> (start - margin).roundToInt().coerceIn(0, maxValue)
        end + margin > value + viewportSize -> (end + margin - viewportSize).roundToInt().coerceIn(0, maxValue)
        else -> null
    }
}

/**
 * Fades the tabs out at each edge they can scroll past, over [MaterialTheme.spacing]'s extra-large step, drawn in the
 * draw phase from [scrollState]. The row draws in an offscreen layer, which the fade masks.
 */
@Composable
fun Modifier.fadingEdges(scrollState: ScrollState): Modifier {
    val width = with(LocalDensity.current) { MaterialTheme.spacing.extraLarge.toPx() }
    // Only the mask's alpha counts: any opaque color of the scheme does.
    val opaque = MaterialTheme.colorScheme.surfaceContainer
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val ltr = layoutDirection == LayoutDirection.Ltr
            if (scrollState.canScrollBackward) drawEdgeFade(atLeft = ltr, width, opaque)
            if (scrollState.canScrollForward) drawEdgeFade(atLeft = !ltr, width, opaque)
        }
}

private fun DrawScope.drawEdgeFade(
    atLeft: Boolean,
    width: Float,
    opaque: Color,
) {
    val left = if (atLeft) 0f else size.width - width
    val colors = if (atLeft) listOf(opaque, Color.Transparent) else listOf(Color.Transparent, opaque)
    drawRect(
        brush = Brush.horizontalGradient(colors, startX = left, endX = left + width),
        topLeft = Offset(left, 0f),
        size = Size(width, size.height),
        blendMode = BlendMode.DstOut,
    )
}
