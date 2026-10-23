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
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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

    /** A search's match in the text: a rounded block per line it covers, behind text in the block's content color. */
    const val MATCH = "match"
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
 * on [codeColor], one shape around all of its lines; hidden spoilers as [spoilerColor] shapes per line; and a search's
 * matches ([TextBlock.MATCH]) as [matchColor] shapes per line, none without one. Each block reaches [horizontalPadding]
 * past its text at both ends, so the glyphs do not touch its round corners.
 *
 * The shapes are computed once per layout, and drawn in the draw phase; text without blocks draws nothing.
 */
fun Modifier.textBlocks(
    layout: TextBlocksLayout,
    codeColor: Color,
    spoilerColor: Color,
    shape: Shape,
    horizontalPadding: Dp,
    matchColor: Color = Color.Unspecified,
): Modifier =
    drawWithCache {
        val blocks = layout.result?.blocks(horizontalPadding.toPx()).orEmpty()
        val outlines = blocks.map { block -> shape.createOutline(block.rect.size, layoutDirection, this) }
        onDrawBehind {
            blocks.forEachIndexed { index, block ->
                val color =
                    when (block.kind) {
                        TextBlock.SPOILER -> spoilerColor
                        TextBlock.MATCH -> matchColor
                        else -> codeColor
                    }
                if (color.isSpecified) {
                    translate(block.rect.left, block.rect.top) { drawOutline(outlines[index], color) }
                }
            }
        }
    }

/**
 * This text with the matches of [highlight] as [TextBlock.MATCH] blocks for [textBlocks] to draw, their text in
 * [contentColor], so it reads on the block's color. A match within a hidden spoiler stays hidden; this text as it is
 * without matches.
 */
fun AnnotatedString.withMatches(
    highlight: MatchHighlight,
    contentColor: Color,
): AnnotatedString {
    val spoilers =
        getStringAnnotations(TEXT_BLOCK_TAG, 0, length).filter { it.item == TextBlock.SPOILER }.map {
            it.start until
                it.end
        }
    val matches =
        highlight.ranges(text).filter { match ->
            spoilers.none { spoiler -> match.first <= spoiler.last && spoiler.first <= match.last }
        }
    if (matches.isEmpty()) return this
    return AnnotatedString.Builder(this).run {
        matches.forEach { match ->
            addStringAnnotation(TEXT_BLOCK_TAG, TextBlock.MATCH, match.first, match.last + 1)
            addStyle(SpanStyle(color = contentColor), match.first, match.last + 1)
        }
        toAnnotatedString()
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
