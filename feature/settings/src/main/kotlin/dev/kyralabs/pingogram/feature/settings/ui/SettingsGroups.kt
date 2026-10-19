package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

// The groups of rows on every settings screen (docs/DESIGN_SYSTEM.md §6, "Settings rows"): Material 3 Expressive
// segmented lists, each row its own container on `surface` (ListItemDefaults.segmentedColors), its corners from its
// place in the group (ListItemDefaults.segmentedShapes), ListItemDefaults.SegmentedGap from its neighbours.

/**
 * The rows of a [SettingsGroup], collected in order by its builder like a lazy list's items: each [item] is composed
 * with the [ListItemShapes] of its place in the group, for its [SegmentedListItem] or [SettingsSegment]. A [key]
 * keeps a row's state (its press, its animations) with it when rows before it come and go.
 */
internal class SettingsGroupScope {
    internal val items = mutableListOf<SettingsGroupItem>()

    fun item(
        key: Any? = null,
        content: @Composable (shapes: ListItemShapes) -> Unit,
    ) {
        items += SettingsGroupItem(key, content)
    }
}

internal class SettingsGroupItem(
    val key: Any?,
    val content: @Composable (shapes: ListItemShapes) -> Unit,
)

/**
 * A group of rows, Material 3 Expressive's segmented list (`docs/DESIGN_SYSTEM.md` §6, "Settings rows"): [title], if
 * any, as a heading ([SettingsSectionHeader]), then the [items], each in a container of its own,
 * `ListItemDefaults.SegmentedGap` apart, with large outer corners at the ends of the group and small ones between rows
 * (`ListItemDefaults.segmentedShapes`), and a [footer] note below them ([SettingsFooter]). [rowsModifier] applies to
 * the rows alone, e.g. `Modifier.selectableGroup()` for radio rows.
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    rowsModifier: Modifier = Modifier,
    items: SettingsGroupScope.() -> Unit,
) {
    val rows = SettingsGroupScope().apply(items).items
    Column(modifier) {
        if (title != null) SettingsSectionHeader(title)
        Column(rowsModifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { index, row ->
                key(row.key ?: index) { row.content(ListItemDefaults.segmentedShapes(index, rows.size)) }
            }
        }
        if (footer != null) SettingsFooter(footer)
    }
}

/**
 * A row of a [SettingsGroup] that holds something other than a list item's slots, e.g. segmented buttons, text fields
 * or a loading indicator: [content] on the segmented list item's container color, in the resting shape of [shapes].
 */
@Composable
internal fun SettingsSegment(
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(ListItemDefaults.segmentedColors().containerColor, shapes.shape),
        content = content,
    )
}

/**
 * A row of a [SettingsGroup]: a [SegmentedListItem] with an outlined [icon] (a Material Symbols drawable), [title] in
 * the list item's `bodyLarge`, [supportingContent] and [trailingContent]. With [onClick] it is clickable, and its shape
 * morphs to the list item's pressed shape on the motion scheme's fast spatial spring (the list item's own); without,
 * it only shows. Either way screen readers read it as one item.
 */
@Composable
internal fun SettingsRow(
    title: String,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: ListItemColors = ListItemDefaults.segmentedColors(),
) {
    val leadingContent: (@Composable () -> Unit)? = icon?.let { { SettingsItemIcon(it) } }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            supportingContent = supportingContent,
            colors = colors,
        ) {
            Text(title)
        }
    } else {
        SegmentedListItem(
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            supportingContent = supportingContent,
            colors = colors,
        ) {
            Text(title)
        }
    }
}

/** A row that opens another settings page: its [icon], [title] and [summary]. */
@Composable
internal fun PageItem(
    title: String,
    summary: String,
    @DrawableRes icon: Int,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    SettingsRow(
        title = title,
        shapes = shapes,
        icon = icon,
        supportingContent = { Text(summary) },
        onClick = onClick,
    )
}

/**
 * The colors of a row that holds other rows or a heading inside a [SettingsSegment]: transparent, so the segment's
 * container shows, with the segmented list item's colors otherwise (selected rows included).
 */
@Composable
internal fun nestedItemColors(): ListItemColors =
    ListItemDefaults.segmentedColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)

/** A row's leading icon, a Material Symbols drawable; the row's text names it for screen readers. */
@Composable
internal fun SettingsItemIcon(
    @DrawableRes icon: Int,
) {
    Icon(painter = painterResource(icon), contentDescription = null)
}
