package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlin.math.ceil
import kotlin.math.max

// The frames of a text bubble and of a media bubble as one custom layout each (`docs/PERFORMANCE.md` §4): every piece
// is measured once, in one pass, with no nested rows, columns or boxes and no intrinsic measurements.

/**
 * The slots of a text bubble's layout ([rememberTextBubbleMeasurePolicy]), in the order of its `contents`: what goes
 * above the text (the header and padded content), the text, the link preview and the meta.
 */
internal object TextBubbleSlots {
    const val ABOVE = 0
    const val TEXT = 1
    const val PREVIEW = 2
    const val META = 3
}

/**
 * The slots of a media bubble's layout ([rememberMediaBubbleMeasurePolicy]), in the order of its `contents`: the
 * header, the media, the meta on its pill over the media, then the caption's text, link preview and meta.
 */
internal object MediaBubbleSlots {
    const val HEADER = 0
    const val MEDIA = 1
    const val OVERLAY_META = 2
    const val TEXT = 3
    const val PREVIEW = 4
    const val META = 5
}

/** The spacing of a bubble's layout, from the theme's spacing tokens. */
internal class BubbleSpacing(
    /** Between the content and the bubble's sides. */
    val horizontal: Dp,
    /** Between the content and the bubble's top and bottom; also around an overlay meta's pill. */
    val vertical: Dp,
    /** Between the pieces stacked in the bubble. */
    val gap: Dp,
    /** Between the text's last line and the meta that follows it. */
    val metaGap: Dp,
)

/** The bubble's spacing: `spacing.medium` at the sides, `spacing.small` at the top and bottom, `extraSmall` between. */
@Composable
internal fun rememberBubbleSpacing(): BubbleSpacing {
    val spacing = MaterialTheme.spacing
    return remember(spacing) {
        BubbleSpacing(
            horizontal = spacing.medium,
            vertical = spacing.small,
            gap = spacing.extraSmall,
            metaGap = spacing.small,
        )
    }
}

/**
 * The measure policy of a text bubble (padded content and text), its slots in [TextBubbleSlots] order: inside the
 * bubble's padding, from the top start, what goes above the text, then the text with the meta after its last line where
 * there is room for it ([textLayout]), else on a line of its own at the text's end; with a link preview, the text, the
 * preview and the meta each on its own line, the meta at the end. Each piece `gap` apart.
 */
@Composable
internal fun rememberTextBubbleMeasurePolicy(textLayout: TextLayoutHolder): MultiContentMeasurePolicy {
    val spacing = rememberBubbleSpacing()
    return remember(spacing, textLayout) { TextBubbleMeasurePolicy(spacing, textLayout) }
}

/**
 * The measure policy of a media bubble, its slots in [MediaBubbleSlots] order, as wide as its constraints (the
 * modifier gives it the media's width): the header in the bubble's padding, the media edge to edge with the overlay
 * meta's pill over its bottom end, then the caption in the bubble's padding, laid out as a text bubble's text
 * ([rememberTextBubbleMeasurePolicy]).
 */
@Composable
internal fun rememberMediaBubbleMeasurePolicy(textLayout: TextLayoutHolder): MultiContentMeasurePolicy {
    val spacing = rememberBubbleSpacing()
    return remember(spacing, textLayout) { MediaBubbleMeasurePolicy(spacing, textLayout) }
}

private class TextBubbleMeasurePolicy(
    private val spacing: BubbleSpacing,
    private val textLayout: TextLayoutHolder,
) : MultiContentMeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<List<Measurable>>,
        constraints: Constraints,
    ): MeasureResult {
        val horizontal = spacing.horizontal.roundToPx()
        val vertical = spacing.vertical.roundToPx()
        val inner = constraints.offset(-(2 * horizontal), -(2 * vertical)).copy(minWidth = 0, minHeight = 0)
        val section =
            measureBubbleSection(
                above = measurables[TextBubbleSlots.ABOVE],
                text = measurables[TextBubbleSlots.TEXT].firstOrNull(),
                preview = measurables[TextBubbleSlots.PREVIEW].firstOrNull(),
                meta = measurables[TextBubbleSlots.META].firstOrNull(),
                constraints = inner,
                spacing = spacing,
                textLayout = textLayout,
            )
        val width = constraints.constrainWidth(section.width + 2 * horizontal)
        val height = constraints.constrainHeight(section.height + 2 * vertical)
        return layout(width, height) { section.place(this, horizontal, vertical) }
    }
}

private class MediaBubbleMeasurePolicy(
    private val spacing: BubbleSpacing,
    private val textLayout: TextLayoutHolder,
) : MultiContentMeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<List<Measurable>>,
        constraints: Constraints,
    ): MeasureResult {
        val horizontal = spacing.horizontal.roundToPx()
        val vertical = spacing.vertical.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val padded = loose.offset(-(2 * horizontal), -(2 * vertical))
        val header =
            measurables[MediaBubbleSlots.HEADER].takeIf { it.isNotEmpty() }?.let {
                measureBubbleSection(it, null, null, null, padded, spacing, textLayout)
            }
        val media = MediaWithMeta.measure(measurables, loose, vertical)
        val text = measurables[MediaBubbleSlots.TEXT].firstOrNull()
        val preview = measurables[MediaBubbleSlots.PREVIEW].firstOrNull()
        val meta = measurables[MediaBubbleSlots.META].firstOrNull()
        val caption =
            if (text != null || preview != null || meta != null) {
                measureBubbleSection(emptyList(), text, preview, meta, padded, spacing, textLayout)
            } else {
                null
            }
        val headerHeight = header?.let { it.height + 2 * vertical } ?: 0
        val captionHeight = caption?.let { it.height + 2 * vertical } ?: 0
        val headerWidth = header?.let { it.width + 2 * horizontal } ?: 0
        val captionWidth = caption?.let { it.width + 2 * horizontal } ?: 0
        val contentWidth = maxOf(headerWidth, media.width, captionWidth)
        val width = constraints.constrainWidth(contentWidth)
        val height = constraints.constrainHeight(headerHeight + media.height + captionHeight)
        return layout(width, height) {
            header?.place(this, horizontal, vertical)
            media.place(this, headerHeight)
            caption?.place(this, horizontal, headerHeight + media.height + vertical)
        }
    }
}

/**
 * A media bubble's media and the meta on its pill over the media's bottom end, `inset` from its edges, as a box would
 * stack them: as large as the larger of the two (with the inset).
 */
private class MediaWithMeta(
    private val media: List<Placeable>,
    private val meta: Placeable?,
    private val inset: Int,
    val width: Int,
    val height: Int,
) {
    fun place(
        scope: Placeable.PlacementScope,
        y: Int,
    ) = with(scope) {
        media.forEach { it.placeRelative(0, y) }
        meta?.placeRelative(width - inset - meta.width, y + height - inset - meta.height)
    }

    companion object {
        fun measure(
            measurables: List<List<Measurable>>,
            constraints: Constraints,
            inset: Int,
        ): MediaWithMeta {
            val media = measurables[MediaBubbleSlots.MEDIA].map { it.measure(constraints) }
            val meta =
                measurables[MediaBubbleSlots.OVERLAY_META].firstOrNull()?.measure(
                    constraints.offset(-(2 * inset), -(2 * inset)),
                )
            val metaWidth = meta?.let { it.width + 2 * inset } ?: 0
            val metaHeight = meta?.let { it.height + 2 * inset } ?: 0
            return MediaWithMeta(
                media = media,
                meta = meta,
                inset = inset,
                width = max(media.maxOfOrNull { it.width } ?: 0, metaWidth),
                height = max(media.maxOfOrNull { it.height } ?: 0, metaHeight),
            )
        }
    }
}

/**
 * A padded part of a bubble, measured: the whole content of a text bubble, a media bubble's header or caption. Its
 * lines stack `gap` apart from its top as a column's children would, each placeable at the start of its line or, where
 * it is added [atEnd][add], at the section's end.
 */
private class BubbleSection(
    capacity: Int,
    private val gap: Int,
    private val metaGap: Int,
) {
    private val placeables = ArrayList<Placeable>(capacity)
    private val xs = IntArray(capacity)
    private val ys = IntArray(capacity)
    private val atEnd = BooleanArray(capacity)
    private var lines = 0

    /** As wide as its widest line. */
    var width = 0
        private set

    /** The bottom of its last line. */
    var height = 0
        private set

    /** Adds a line [lineWidth] wide and [lineHeight] tall below the others, `gap` apart, and returns its top. */
    fun line(
        lineWidth: Int,
        lineHeight: Int,
    ): Int {
        val top = if (lines == 0) 0 else height + gap
        lines++
        width = max(width, lineWidth)
        height = top + lineHeight
        return top
    }

    /** Puts [placeable] at [x], [y] from the section's top start, or at its end ([atEnd]). */
    fun add(
        placeable: Placeable,
        x: Int,
        y: Int,
        atEnd: Boolean = false,
    ) {
        val index = placeables.size
        xs[index] = x
        ys[index] = y
        this.atEnd[index] = atEnd
        placeables += placeable
    }

    /** Puts [placeable] on a line of its own below the others, at its start or at the section's end ([atEnd]). */
    fun addLine(
        placeable: Placeable,
        atEnd: Boolean = false,
    ) = add(placeable, 0, line(placeable.width, placeable.height), atEnd)

    /**
     * Puts [text] and [meta] on one line block below the others, the meta at its bottom end: after the text's last line
     * ([textLayout] in [layoutDirection]), `metaGap` from it, where that fits in [maxWidth], else below the text.
     */
    fun addTextWithMeta(
        text: Placeable,
        meta: Placeable,
        maxWidth: Int,
        layoutDirection: LayoutDirection,
        textLayout: TextLayoutHolder,
    ) {
        val lineWidth = textLayout.lastLineWidth(layoutDirection) ?: text.width
        val inlineWidth = if (lineWidth > 0) lineWidth + metaGap + meta.width else meta.width
        val inline = inlineWidth <= maxWidth
        val blockWidth = max(text.width, if (inline) inlineWidth else meta.width)
        val blockHeight = if (inline) max(text.height, meta.height) else text.height + meta.height
        val top = line(blockWidth, blockHeight)
        add(text, 0, top)
        add(meta, blockWidth - meta.width, top + blockHeight - meta.height)
    }

    fun place(
        scope: Placeable.PlacementScope,
        x: Int,
        y: Int,
    ) = with(scope) {
        // From the start; placeRelative mirrors the pieces in right-to-left layouts.
        placeables.forEachIndexed { index, placeable ->
            val start = if (atEnd[index]) width - placeable.width else xs[index]
            placeable.placeRelative(x + start, y + ys[index])
        }
    }
}

/** The text, the link preview and the meta: the most a section holds below what goes above the text. */
private const val SECTION_BODY_PIECES = 3

/**
 * Measures [above], [text], [preview] and [meta] with [constraints] and stacks them `gap` apart at the start, as a
 * column would: [above] each on its own line; the [text] with the [meta] after its last line ([textLayout]) when there
 * is no [preview] and the line leaves room for it, else on a line of its own at the text's end, without a gap; with a
 * [preview], the text, the preview and the meta each on its own line, the meta at the section's end.
 */
@Suppress("LongParameterList") // The section's four slots, its constraints, spacing and text layout.
private fun MeasureScope.measureBubbleSection(
    above: List<Measurable>,
    text: Measurable?,
    preview: Measurable?,
    meta: Measurable?,
    constraints: Constraints,
    spacing: BubbleSpacing,
    textLayout: TextLayoutHolder,
): BubbleSection {
    val section =
        BubbleSection(above.size + SECTION_BODY_PIECES, spacing.gap.roundToPx(), spacing.metaGap.roundToPx())
    above.forEach { section.addLine(it.measure(constraints)) }
    val textPlaceable = text?.measure(constraints)
    val previewPlaceable = preview?.measure(constraints)
    val metaPlaceable = meta?.measure(constraints)
    if (textPlaceable != null && previewPlaceable == null && metaPlaceable != null) {
        section.addTextWithMeta(textPlaceable, metaPlaceable, constraints.maxWidth, layoutDirection, textLayout)
    } else {
        textPlaceable?.let { section.addLine(it) }
        previewPlaceable?.let { section.addLine(it) }
        metaPlaceable?.let { section.addLine(it, atEnd = true) }
    }
    return section
}

/**
 * A bubble text's latest layout, which its `onTextLayout` keeps and the bubble's layout reads right after measuring
 * the text, to put the meta after its last line: the text's layout result cached for sizing the bubble, not
 * composition state.
 */
internal class TextLayoutHolder {
    var result: TextLayoutResult? = null

    /**
     * How wide the last line is, so the meta can follow it; the text's full width when the line runs against the
     * layout's direction (right-to-left text in a left-to-right layout), as the meta would cover its end then. Null
     * before the text's first layout.
     */
    fun lastLineWidth(layoutDirection: LayoutDirection): Int? {
        val layout = result?.takeIf { it.lineCount > 0 } ?: return null
        val last = layout.lineCount - 1
        val lineDirection = layout.getParagraphDirection(layout.getLineStart(last))
        val againstLayout = (lineDirection == ResolvedTextDirection.Rtl) != (layoutDirection == LayoutDirection.Rtl)
        return if (againstLayout) {
            layout.size.width
        } else {
            ceil(layout.getLineRight(last) - layout.getLineLeft(last)).toInt()
        }
    }
}
