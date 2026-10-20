package dev.kyralabs.pingogram.core.ui.text

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp

/** The tag of the string annotations over the blocks [textBlocks] draws; their values are [TextBlock]'s. */
const val TEXT_BLOCK_TAG = "pingogram.textBlock"

/** The kinds of block behind text, the values of [TEXT_BLOCK_TAG] annotations. */
object TextBlock {
    /** Inline code: a rounded background per line it covers. */
    const val CODE = "code"

    /** A code block: one rounded background around all of its lines. */
    const val PRE = "pre"

    /** A hidden spoiler: a rounded block per line, its text transparent. */
    const val SPOILER = "spoiler"
}

/**
 * The latest layout of a text whose blocks [textBlocks] draws: pass [onTextLayout] to the text. Snapshot state read
 * in the draw phase only, so a new layout redraws the blocks without composing anything again.
 */
@Stable
class TextBlocksLayout {
    internal var result: TextLayoutResult? by mutableStateOf(null, structuralEqualityPolicy())

    val onTextLayout: (TextLayoutResult) -> Unit = { result = it }
}

/**
 * Draws the blocks of a text built with `toAnnotatedString(drawsBlocks = true)` behind it (`docs/DESIGN_SYSTEM.md`
 * §4), from its [layout]: inline code on [codeColor] in [shape], a rounded background per line it covers; a code block
 * on [codeColor], one shape around all of its lines; and hidden spoilers as [spoilerColor] shapes per line. Each block
 * reaches [horizontalPadding] past its text at both ends, so the glyphs do not touch its round corners.
 *
 * The shapes are computed once per layout, and drawn in the draw phase; text without blocks draws nothing.
 */
fun Modifier.textBlocks(
    layout: TextBlocksLayout,
    codeColor: Color,
    spoilerColor: Color,
    shape: Shape,
    horizontalPadding: Dp,
): Modifier =
    drawWithCache {
        val blocks = layout.result?.blocks(horizontalPadding.toPx()).orEmpty()
        val outlines = blocks.map { block -> shape.createOutline(block.rect.size, layoutDirection, this) }
        onDrawBehind {
            blocks.forEachIndexed { index, block ->
                val color = if (block.kind == TextBlock.SPOILER) spoilerColor else codeColor
                translate(block.rect.left, block.rect.top) { drawOutline(outlines[index], color) }
            }
        }
    }

/** Whether this text has blocks for [textBlocks] to draw. */
fun AnnotatedString.hasTextBlocks(): Boolean = hasStringAnnotations(TEXT_BLOCK_TAG, 0, length)

/** A block's [kind] ([TextBlock]) and where it is drawn. */
private class DrawnBlock(
    val kind: String,
    val rect: Rect,
)

/** The blocks of the laid-out text, each line's part [padding] wider at both ends. */
private fun TextLayoutResult.blocks(padding: Float): List<DrawnBlock> {
    val text = layoutInput.text
    return text.getStringAnnotations(TEXT_BLOCK_TAG, 0, text.length).flatMap { annotation ->
        val lines = lineRects(annotation.start, annotation.end, padding)
        when {
            lines.isEmpty() -> emptyList()
            annotation.item == TextBlock.PRE -> listOf(DrawnBlock(annotation.item, lines.reduce(::enclosing)))
            else -> lines.map { DrawnBlock(annotation.item, it) }
        }
    }
}

/**
 * The part of each line that [start] (inclusive) to [end] (exclusive) cover, from the line's top to its bottom, without
 * the line's trailing whitespace; lines past the laid-out ones (cut by `maxLines`) are left out.
 */
private fun TextLayoutResult.lineRects(
    start: Int,
    end: Int,
    padding: Float,
): List<Rect> {
    if (lineCount == 0 || end <= start) return emptyList()
    val firstLine = getLineForOffset(start)
    val lastLine = getLineForOffset(end - 1)
    return (firstLine..lastLine).mapNotNull { line ->
        val from = maxOf(start, getLineStart(line))
        val to = minOf(end, getLineEnd(line, visibleEnd = true))
        if (to <= from) return@mapNotNull null
        // The first and the last character's boxes: their outer edges, in either direction.
        val first = getBoundingBox(from)
        val last = getBoundingBox(to - 1)
        val left = minOf(first.left, last.left) - padding
        val right = maxOf(first.right, last.right) + padding
        Rect(Offset(left, getLineTop(line)), Offset(right, getLineBottom(line)))
    }
}

/** The smallest rectangle around [a] and [b]. */
private fun enclosing(
    a: Rect,
    b: Rect,
): Rect = Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
