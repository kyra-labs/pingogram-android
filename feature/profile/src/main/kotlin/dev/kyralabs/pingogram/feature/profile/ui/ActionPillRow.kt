package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * The action pills sharing a row, or stacked one under the other from 150% font (board 24), so a long label such as
 * "Start voice chat" never squeezes into a third of the row.
 */
@Composable
internal fun ActionPillRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    val stacked = LocalDensity.current.fontScale >= STACKED_PILLS_FONT_SCALE
    val gap = Arrangement.spacedBy(MaterialTheme.spacing.small)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = gap,
        verticalArrangement = gap,
        maxItemsInEachRow = if (stacked) 1 else Int.MAX_VALUE,
        content = content,
    )
}

/** The font scale from which the action pills stack. */
private const val STACKED_PILLS_FONT_SCALE = 1.5f
