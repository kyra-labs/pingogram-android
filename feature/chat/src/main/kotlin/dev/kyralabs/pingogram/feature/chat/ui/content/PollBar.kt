package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Draws a poll answer's share of the votes (0–1) as a bar across the element's width, as Material's linear progress
 * indicator draws one: the share in [color] from the start (the end's side in right-to-left layouts), then a [gap],
 * then the rest in [trackColor], both with round ends. A new share animates on [spec] (the motion scheme's spatial
 * spring) in the node's own coroutine, read only in the draw phase, so a vote count changing recomposes nothing and
 * no frame of the animation composes or lays out; the first share shows at once, so bars scrolling into view do not
 * grow.
 */
internal fun Modifier.pollBar(
    share: Float,
    color: Color,
    trackColor: Color,
    gap: Dp,
    spec: FiniteAnimationSpec<Float>,
): Modifier = this then PollBarElement(share.coerceIn(0f, 1f), color, trackColor, gap, spec)

private data class PollBarElement(
    val share: Float,
    val color: Color,
    val trackColor: Color,
    val gap: Dp,
    val spec: FiniteAnimationSpec<Float>,
) : ModifierNodeElement<PollBarNode>() {
    override fun create() = PollBarNode(share, color, trackColor, gap, spec)

    override fun update(node: PollBarNode) = node.update(share, color, trackColor, gap, spec)
}

private class PollBarNode(
    share: Float,
    private var color: Color,
    private var trackColor: Color,
    private var gap: Dp,
    private var spec: FiniteAnimationSpec<Float>,
) : Modifier.Node(),
    DrawModifierNode {
    private val shown = Animatable(share)
    private var target = share
    private var animation: Job? = null

    fun update(
        share: Float,
        color: Color,
        trackColor: Color,
        gap: Dp,
        spec: FiniteAnimationSpec<Float>,
    ) {
        this.color = color
        this.trackColor = trackColor
        this.gap = gap
        this.spec = spec
        if (share != target) {
            target = share
            animation?.cancel()
            if (isAttached) animation = coroutineScope.launch { shown.animateTo(share, spec) }
        }
        if (isAttached) invalidateDraw()
    }

    // A share that changed while detached shows as it is.
    override fun onAttach() {
        if (shown.targetValue != target) coroutineScope.launch { shown.snapTo(target) }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val height = size.height
        val radius = CornerRadius(height / 2, height / 2)
        // A spatial spring overshoots: the bar never leaves its track.
        val filled = (shown.value.coerceIn(0f, 1f) * size.width).let { if (it > 0f) it.coerceAtLeast(height) else 0f }
        val gapPx = if (filled > 0f) gap.toPx() else 0f
        val trackStart = (filled + gapPx).coerceAtMost(size.width)
        val rtl = layoutDirection == LayoutDirection.Rtl

        fun x(
            start: Float,
            width: Float,
        ) = if (rtl) size.width - start - width else start
        if (trackStart < size.width) {
            val width = size.width - trackStart
            drawRoundRect(trackColor, Offset(x(trackStart, width), 0f), Size(width, height), radius)
        }
        if (filled > 0f) drawRoundRect(color, Offset(x(0f, filled), 0f), Size(filled, height), radius)
    }
}
