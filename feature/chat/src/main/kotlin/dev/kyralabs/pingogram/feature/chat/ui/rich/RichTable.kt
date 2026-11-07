package dev.kyralabs.pingogram.feature.chat.ui.rich

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.domain.model.TableCell
import dev.kyralabs.pingogram.core.domain.model.TableCellAlignment
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * A table: its caption, then its cells in a grid that scrolls sideways where it is wider than the bubble. Each column
 * is as wide as its widest cell (at most `mediaMaxWidth`), each row as tall as its tallest; a cell spanning columns
 * takes their width. Header cells are `titleSmallEmphasized`, the others `bodyMedium`. A bordered table has
 * `outlineVariant` lines between its cells and around it, with `small` corners; a striped one puts every other row on
 * the bubble's block color. Cells spanning rows take one row (Pingogram does not merge them yet).
 */
@Composable
internal fun RichTable(
    block: RichBlock.Table,
    scope: RichScope,
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val thickness = DividerDefaults.Thickness
    val shape = MaterialTheme.shapes.small
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        if (block.caption.text.isNotBlank()) {
            RichText(block.caption, scope, MaterialTheme.typography.bodyMedium, color = scope.colors.secondaryContent)
        }
        val tableModifier =
            if (block.isBordered) Modifier.clip(shape).border(thickness, line, shape) else Modifier
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            val policy = remember(block) { TableMeasurePolicy(block.rows.map { row -> row.map { it.colspan } }) }
            val cellMaxWidth = MaterialTheme.sizes.mediaMaxWidth
            Layout(
                content = {
                    block.rows.forEachIndexed { rowIndex, row ->
                        val stripe = block.isStriped && rowIndex % 2 == 1
                        row.forEach { cell ->
                            RichTableCell(cell, scope, stripe, if (block.isBordered) line else null, thickness)
                        }
                    }
                },
                modifier = tableModifier,
                measurePolicy = remember(policy, cellMaxWidth) { policy.withMaxCellWidth(cellMaxWidth) },
            )
        }
    }
}

@Composable
private fun RichTableCell(
    cell: TableCell,
    scope: RichScope,
    striped: Boolean,
    line: Color?,
    thickness: Dp,
) {
    val background = if (striped || cell.isHeader) scope.colors.block else Color.Transparent
    val typography = MaterialTheme.typography
    Box(
        modifier =
            Modifier
                .background(background)
                .then(if (line != null) Modifier.cellLines(line, thickness) else Modifier)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
    ) {
        RichText(
            text = cell.text,
            scope = scope,
            style = if (cell.isHeader) typography.titleSmallEmphasized else typography.bodyMedium,
            textAlign =
                when (cell.align) {
                    TableCellAlignment.Start -> TextAlign.Start
                    TableCellAlignment.Center -> TextAlign.Center
                    TableCellAlignment.End -> TextAlign.End
                },
        )
    }
}

/** The line after a cell and the one below it, so the cells of a bordered table are ruled once each. */
private fun Modifier.cellLines(
    color: Color,
    thickness: Dp,
): Modifier =
    drawBehind {
        val stroke = thickness.toPx()
        val end = if (layoutDirection == LayoutDirection.Ltr) size.width - stroke / 2 else stroke / 2
        drawLine(color, Offset(end, 0f), Offset(end, size.height), stroke)
        val bottom = size.height - stroke / 2
        drawLine(color, Offset(0f, bottom), Offset(size.width, bottom), stroke)
    }

/**
 * Lays out the cells of a table, given row by row in [spans] (each cell's column span): columns as wide as their
 * widest single-column cell's text, from its intrinsic width (at most [maxCellWidth]), a spanning cell widening the
 * last of its columns where they are too narrow for it; rows as tall as their tallest cell at its width. Each cell is
 * then measured once, at exactly its place's size.
 */
private class TableMeasurePolicy(
    private val spans: List<List<Int>>,
    private val maxCellWidth: Dp = Dp.Unspecified,
) : MeasurePolicy {
    fun withMaxCellWidth(width: Dp) = TableMeasurePolicy(spans, width)

    private val columns = spans.maxOfOrNull { it.sum() } ?: 0

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val cellMax = if (maxCellWidth == Dp.Unspecified) Constraints.Infinity else maxCellWidth.roundToPx()
        val cells = placesOf(measurables)
        val widths = IntArray(columns)
        cells.filter { it.span == 1 }.forEach { cell ->
            val width = cell.measurable.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(cellMax)
            widths[cell.column] = maxOf(widths[cell.column], width)
        }
        cells.filter { it.span > 1 }.forEach { cell ->
            val needed = cell.measurable.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(cellMax)
            val has = (cell.column until cell.column + cell.span).sumOf { widths[it] }
            if (needed > has) widths[cell.column + cell.span - 1] += needed - has
        }
        val heights = IntArray(spans.size)
        cells.forEach { cell ->
            val width = cell.width(widths)
            heights[cell.row] = maxOf(heights[cell.row], cell.measurable.maxIntrinsicHeight(width))
        }
        val placeables =
            cells.map { cell ->
                cell.measurable.measure(Constraints.fixed(cell.width(widths), heights[cell.row]))
            }
        val width = widths.sum()
        val height = heights.sum()
        return layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
            var y = 0
            var row = 0
            cells.forEachIndexed { index, cell ->
                if (cell.row != row) {
                    y += heights[row]
                    row = cell.row
                }
                placeables[index].placeRelative((0 until cell.column).sumOf { widths[it] }, y)
            }
        }
    }

    /** Where each measurable goes: its row, first column and span. */
    private fun placesOf(measurables: List<Measurable>): List<CellPlace> {
        val places = ArrayList<CellPlace>(measurables.size)
        var index = 0
        spans.forEachIndexed { row, rowSpans ->
            var column = 0
            rowSpans.forEach { span ->
                measurables.getOrNull(index++)?.let { places += CellPlace(it, row, column, span) }
                column += span
            }
        }
        return places
    }

    private class CellPlace(
        val measurable: Measurable,
        val row: Int,
        val column: Int,
        val span: Int,
    ) {
        fun width(widths: IntArray) = (column until column + span).sumOf { widths[it] }
    }
}
