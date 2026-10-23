package dev.kyralabs.pingogram.feature.chat.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.theme.isAmoledBlack
import dev.kyralabs.pingogram.core.ui.theme.spacing

// The export page's groups and rows: segmented list items as the settings pages', in modules this one does not
// depend on.

/**
 * A group of segmented rows: [title], if any, as a `titleSmallEmphasized` heading in `primary`, then [rows], each its
 * own container `ListItemDefaults.SegmentedGap` apart with the shapes of its place, and [footer] below them.
 */
@Composable
internal fun ExportGroup(
    rows: List<ExportRow>,
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    rowsModifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (title != null) {
            Text(
                text = title,
                modifier =
                    Modifier
                        .padding(start = MaterialTheme.spacing.large, bottom = MaterialTheme.spacing.small)
                        .semantics { heading() },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmallEmphasized,
            )
        }
        Column(rowsModifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { index, row ->
                key(row.key) { row.content(ListItemDefaults.segmentedShapes(index, rows.size)) }
            }
        }
        if (footer != null) {
            Text(
                text = footer,
                modifier =
                    Modifier.padding(
                        start = MaterialTheme.spacing.large,
                        end = MaterialTheme.spacing.large,
                        top = MaterialTheme.spacing.small,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** A row of an [ExportGroup] under [key], composed with the shapes of its place. */
internal class ExportRow(
    val key: String,
    val content: @Composable (shapes: ListItemShapes) -> Unit,
)

/** A row that holds something other than a list item's slots, on the rows' container in [shapes]' resting shape. */
@Composable
internal fun ExportSegment(
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(exportRowColors().containerColor, shapes.shape),
        content = content,
    )
}

/**
 * A switch row: the clickable segmented list item with `Role.Switch` semantics, [label], [supporting] text and a
 * trailing [CheckSwitch], toggled with a `ToggleOn` or `ToggleOff` haptic.
 */
@Composable
internal fun ExportSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
    supporting: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    SegmentedListItem(
        onClick = {
            haptics.performToggle(!checked)
            onCheckedChange(!checked)
        },
        shapes = shapes,
        modifier =
            Modifier.semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            },
        colors = exportRowColors(),
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { CheckSwitch(checked = checked) },
    ) {
        Text(label)
    }
}

/** One of several options: the selectable segmented list item with a [RadioButton], [label] and [supporting] text. */
@Composable
internal fun ExportRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    shapes: ListItemShapes,
    supporting: String? = null,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        colors = exportRowColors(),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        supportingContent = supporting?.let { { Text(it) } },
    ) {
        Text(label)
    }
}

/** The rows' colors: the segmented list item's on `surface`, or `surfaceContainer` with AMOLED black. */
@Composable
internal fun exportRowColors(contentColor: Color = Color.Unspecified): ListItemColors =
    ListItemDefaults.segmentedColors(
        containerColor =
            if (isAmoledBlack()) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        contentColor = contentColor,
    )

/** The colors of a list item inside an [ExportSegment]: transparent, so the segment's container shows. */
@Composable
internal fun nestedRowColors(): ListItemColors =
    ListItemDefaults.segmentedColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)
