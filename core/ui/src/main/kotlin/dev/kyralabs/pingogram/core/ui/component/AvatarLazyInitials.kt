package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/**
 * Draws [initials] in [color] centered on the placeholder of an avatar that has a photo, where a centering `Box` places
 * a `Text`, but lays them out only when they show: while the photo has not covered them yet ([photoAlpha] below 1). A
 * photo that Coil's memory cache holds (a list scrolling back, a chat list after its first screen) covers the
 * placeholder before its first frame, so its initials are never laid out, and a list row coming into view measures one
 * text less (`docs/PERFORMANCE.md` §4). An avatar without a photo keeps its `Text`, laid out with the row, also while a
 * lazy list prefetches it, since its initials always show.
 */
internal fun Modifier.lazyInitials(
    initials: String,
    color: Color,
    style: TextStyle,
    photoAlpha: Animatable<Float, AnimationVector1D>,
): Modifier = this then LazyInitialsElement(initials, color, style, photoAlpha)

private data class LazyInitialsElement(
    val initials: String,
    val color: Color,
    val style: TextStyle,
    val photoAlpha: Animatable<Float, AnimationVector1D>,
) : ModifierNodeElement<LazyInitialsNode>() {
    override fun create() = LazyInitialsNode(initials, color, style, photoAlpha)

    override fun update(node: LazyInitialsNode) = node.update(initials, color, style, photoAlpha)
}

private class LazyInitialsNode(
    private var initials: String,
    private var color: Color,
    private var style: TextStyle,
    private var photoAlpha: Animatable<Float, AnimationVector1D>,
) : Modifier.Node(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    private var layout: TextLayoutResult? = null
    private var layoutSize = IntSize.Zero
    private var layoutDirection = LayoutDirection.Ltr
    private var layoutDensity: Density? = null

    fun update(
        initials: String,
        color: Color,
        style: TextStyle,
        photoAlpha: Animatable<Float, AnimationVector1D>,
    ) {
        if (initials != this.initials || style != this.style) layout = null
        this.initials = initials
        this.color = color
        this.style = style
        this.photoAlpha = photoAlpha
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        // Read in the draw phase: the photo fading in redraws the placeholder, which stops drawing them once covered.
        if (photoAlpha.value >= 1f) return
        val bounds = IntSize(size.width.toInt(), size.height.toInt())
        val text = textLayout(bounds, layoutDirection)
        // Where a centering Box places a Text of that size, rounded to whole pixels as it does.
        val offset = Alignment.Center.align(text.size, bounds, layoutDirection)
        translate(offset.x.toFloat(), offset.y.toFloat()) { drawText(text, color) }
    }

    private fun textLayout(
        bounds: IntSize,
        direction: LayoutDirection,
    ): TextLayoutResult {
        val density = currentValueOf(LocalDensity)
        layout?.let { if (bounds == layoutSize && direction == layoutDirection && density == layoutDensity) return it }
        val measurer = TextMeasurer(currentValueOf(LocalFontFamilyResolver), density, direction, 0)
        // As the Text it stands in for: one line, never wrapped, at most as large as the placeholder.
        return measurer
            .measure(
                text = initials,
                style = style,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = bounds.width, maxHeight = bounds.height),
            ).also {
                layout = it
                layoutSize = bounds
                layoutDirection = direction
                layoutDensity = density
            }
    }
}
