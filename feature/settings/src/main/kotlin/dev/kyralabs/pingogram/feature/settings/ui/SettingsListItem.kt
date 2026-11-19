package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Every settings row is Material's segmented list item with its title in `titleMedium` (owner decision 2026-11-19,
// docs/DESIGN_SYSTEM.md §6 "Settings rows"): the list item sets its headline's style itself, so these set it again
// inside, and a row that adapts the style (user content, numbers) adapts `titleMedium`. Summaries keep the list item's
// `bodyMedium` in `onSurfaceVariant`.

/** A settings row that shows only: [SegmentedListItem] with its [content] in `titleMedium`. */
@Suppress("LongParameterList") // Material's list item slots, passed through.
@Composable
internal fun SettingsListItem(
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    colors: ListItemColors = settingsRowColors(),
    content: @Composable () -> Unit,
) {
    SegmentedListItem(
        shapes = shapes,
        modifier = modifier,
        enabled = enabled,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        colors = colors,
    ) { RowTitle(content) }
}

/** A clickable settings row: [SegmentedListItem] with its [content] in `titleMedium`. */
@Suppress("LongParameterList") // Material's list item slots, passed through.
@Composable
internal fun SettingsListItem(
    onClick: () -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    colors: ListItemColors = settingsRowColors(),
    content: @Composable () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        enabled = enabled,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        colors = colors,
    ) { RowTitle(content) }
}

/** A selectable settings row: [SegmentedListItem] with its [content] in `titleMedium`. */
@Suppress("LongParameterList") // Material's list item slots, passed through.
@Composable
internal fun SettingsListItem(
    selected: Boolean,
    onClick: () -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    colors: ListItemColors = settingsRowColors(),
    content: @Composable () -> Unit,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        enabled = enabled,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        colors = colors,
    ) { RowTitle(content) }
}

@Composable
private fun RowTitle(content: @Composable () -> Unit) {
    ProvideTextStyle(MaterialTheme.typography.titleMedium, content)
}
