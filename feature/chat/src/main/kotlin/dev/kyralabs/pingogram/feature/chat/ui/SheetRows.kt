package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.theme.spacing

// The rows of the conversation's sheets (forward, send-as, the self-destruct timer), inset like the chat list's rows
// (docs/DESIGN_SYSTEM.md §4).

/**
 * How far a row's container sits in from its place in the list, as in the chat list: [MaterialTheme.spacing]'s small
 * step from the sides of the sheet, and half of Material's gap between segmented list items from the rows around.
 */
@Composable
internal fun sheetRowInsets(): PaddingValues =
    PaddingValues(horizontal = MaterialTheme.spacing.small, vertical = ListItemDefaults.SegmentedGap / 2)

/** The list item's content padding less [insets], so a row's content stays where the list item puts it. */
internal fun insetContentPadding(insets: PaddingValues): PaddingValues {
    val padding = ListItemDefaults.ContentPadding
    val direction = LayoutDirection.Ltr
    return PaddingValues(
        start = padding.calculateStartPadding(direction) - insets.calculateStartPadding(direction),
        top = padding.calculateTopPadding() - insets.calculateTopPadding(),
        end = padding.calculateEndPadding(direction) - insets.calculateEndPadding(direction),
        bottom = padding.calculateBottomPadding() - insets.calculateBottomPadding(),
    )
}
