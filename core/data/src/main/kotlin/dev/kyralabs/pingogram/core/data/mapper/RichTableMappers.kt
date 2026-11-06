package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.domain.model.TableCell
import dev.kyralabs.pingogram.core.domain.model.TableCellAlignment
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

// The tables of rich messages (td_api.tl pageBlockTable).

/** Rows without cells are left out; a span below 1 counts as 1. */
internal fun TdApi.PageBlockTable.table(): RichBlock.Table =
    RichBlock.Table(
        caption = caption.toFormattedText(),
        rows =
            cells
                .orEmpty()
                .mapNotNull { row ->
                    row?.mapNotNull { it?.toTableCell() }?.takeIf { it.isNotEmpty() }?.toImmutableList()
                }.toImmutableList(),
        isBordered = isBordered,
        isStriped = isStriped,
    )

private fun TdApi.PageBlockTableCell.toTableCell() =
    TableCell(
        text = text.toFormattedText(),
        isHeader = isHeader,
        colspan = colspan.coerceAtLeast(1),
        rowspan = rowspan.coerceAtLeast(1),
        align =
            when (align) {
                is TdApi.PageBlockHorizontalAlignmentCenter -> TableCellAlignment.Center
                is TdApi.PageBlockHorizontalAlignmentRight -> TableCellAlignment.End
                else -> TableCellAlignment.Start
            },
    )
