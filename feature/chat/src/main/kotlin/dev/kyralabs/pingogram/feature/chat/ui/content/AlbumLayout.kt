package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.ui.unit.IntRect
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where the items of an album go: [cells] in the order of the items, in a [width] × [height] box, in pixels. */
internal data class AlbumLayout(
    val width: Int,
    val height: Int,
    val cells: List<IntRect>,
)

/**
 * A mosaic of the album items with these [aspectRatios] (width / height) in rows [width] pixels wide, [gap] pixels
 * apart: each row is as tall as it must be for its items to fill the width at their aspect ratios, which are kept
 * between 1:2 and 2:1 (items beyond are cropped).
 *
 * Of every way to split the items into rows, in order and with up to [MAX_ROW_ITEMS] per row, it takes the one whose
 * height is closest to a square, avoiding rows shorter than a fifth of the width: two photos side by side, or stacked
 * when both are wide; three as one large over two; four as two by two; ten as two, four and four. Ties go to the first
 * split, which puts fewer, larger items first.
 *
 * Edges are rounded so that every gap is exactly [gap] and the rows end exactly at [width].
 */
internal fun albumLayout(
    aspectRatios: List<Float>,
    width: Int,
    gap: Int,
): AlbumLayout {
    if (aspectRatios.isEmpty() || width <= 0) return AlbumLayout(width.coerceAtLeast(0), 0, emptyList())
    val ratios = aspectRatios.map { if (it.isFinite() && it > 0f) it.coerceIn(MIN_RATIO, MAX_RATIO) else 1f }
    val rows = bestRows(ratios, width.toFloat(), gap.toFloat())
    val cells = ArrayList<IntRect>(ratios.size)
    var top = 0f
    var first = 0
    var bottom = 0
    for (count in rows) {
        val row = ratios.subList(first, first + count)
        val rowHeight = rowHeight(row, width.toFloat(), gap.toFloat())
        val rowTop = top.roundToInt()
        bottom = (top + rowHeight).roundToInt()
        var left = 0f
        row.forEachIndexed { index, ratio ->
            val right = if (index == row.lastIndex) width else (left + ratio * rowHeight).roundToInt()
            cells += IntRect(left.roundToInt(), rowTop, right, bottom)
            left += ratio * rowHeight + gap
        }
        top += rowHeight + gap
        first += count
    }
    return AlbumLayout(width, bottom, cells)
}

/** How many items go in each row, in order. */
private fun bestRows(
    ratios: List<Float>,
    width: Float,
    gap: Float,
): List<Int> {
    var best: List<Int> = listOf(ratios.size)
    var bestCost = Float.MAX_VALUE
    forEachSplit(ratios.size) { rows ->
        val cost = cost(rows, ratios, width, gap)
        if (cost < bestCost - COST_EPSILON) {
            best = rows.toList()
            bestCost = cost
        }
    }
    return best
}

private fun cost(
    rows: List<Int>,
    ratios: List<Float>,
    width: Float,
    gap: Float,
): Float {
    var height = gap * (rows.size - 1)
    var shortRows = 0
    var first = 0
    for (count in rows) {
        val rowHeight = rowHeight(ratios.subList(first, first + count), width, gap)
        if (rowHeight < width * MIN_ROW_HEIGHT_FRACTION) shortRows++
        height += rowHeight
        first += count
    }
    return abs(height - width) / width + shortRows * SHORT_ROW_PENALTY
}

/** The height at which items of these [ratios] side by side, [gap] apart, fill [width]. */
private fun rowHeight(
    ratios: List<Float>,
    width: Float,
    gap: Float,
): Float = ((width - gap * (ratios.size - 1)) / ratios.sum()).coerceAtLeast(1f)

/** Calls [action] with every split of [count] items into rows of 1 to [MAX_ROW_ITEMS], fewer items first. */
private fun forEachSplit(
    count: Int,
    action: (List<Int>) -> Unit,
) {
    val rows = ArrayList<Int>()

    fun split(remaining: Int) {
        if (remaining == 0) {
            action(rows)
            return
        }
        for (size in 1..minOf(MAX_ROW_ITEMS, remaining)) {
            rows += size
            split(remaining - size)
            rows.removeAt(rows.lastIndex)
        }
    }
    split(count)
}

/** At most this many photos and videos form an album in Telegram. */
internal const val MAX_ALBUM_ITEMS = 10

private const val MAX_ROW_ITEMS = 4
private const val MIN_RATIO = 0.5f
private const val MAX_RATIO = 2f
private const val MIN_ROW_HEIGHT_FRACTION = 0.2f
private const val SHORT_ROW_PENALTY = 1f
private const val COST_EPSILON = 1e-4f
