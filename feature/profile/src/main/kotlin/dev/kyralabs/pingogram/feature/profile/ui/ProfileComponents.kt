package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.theme.growInVertically
import dev.kyralabs.pingogram.core.ui.theme.isAmoledBlack
import dev.kyralabs.pingogram.core.ui.theme.shrinkOutVertically
import dev.kyralabs.pingogram.core.ui.theme.spacing

// The look the profile shares with settings pages (docs/DESIGN_SYSTEM.md §2.2, §6): the page on
// surfaceContainerHigh (true black with AMOLED black), its rows Material 3 Expressive segmented list items on surface
// (surfaceContainer with AMOLED black), each row its own container, ListItemDefaults.SegmentedGap apart.

/** The color of the profile page behind its rows and its transparent top bar, as a settings page's. */
@Composable
@ReadOnlyComposable
internal fun profilePageColor(): Color =
    if (isAmoledBlack()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh

/**
 * The colors of a profile row: the segmented list item's, on `surface`, or `surfaceContainer` with AMOLED black;
 * [contentColor] replaces the list item's, e.g. `error` for Block and Report.
 */
@Composable
internal fun profileRowColors(contentColor: Color = Color.Unspecified): ListItemColors =
    ListItemDefaults.segmentedColors(
        containerColor = profileRowContainerColor(),
        contentColor = contentColor,
    )

/** The container of a profile row and of the segments around other content. */
@Composable
@ReadOnlyComposable
internal fun profileRowContainerColor(): Color =
    if (isAmoledBlack()) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface

/**
 * A group of rows, Material 3 Expressive's segmented list: [title], if any, as a heading in `titleSmallEmphasized` and
 * `primary`, then [rows], each composed with the [ListItemShapes] of its place in the group (large outer corners at
 * its ends, small ones between rows), `ListItemDefaults.SegmentedGap` apart. A row's key keeps its state with it when
 * rows before it come and go.
 */
@Composable
internal fun ProfileGroup(
    rows: List<ProfileRow>,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    if (rows.isEmpty()) return
    val shown = remember { ShownRows() }
    val entries = shown.update(rows)
    val motion = MaterialTheme.motionScheme
    val enter = remember(motion) { growInVertically(motion) }
    val exit = remember(motion) { shrinkOutVertically(motion) }
    Column(modifier) {
        if (title != null) ProfileSectionHeader(title)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            entries.forEach { entry ->
                key(entry.row.key) {
                    AnimatedVisibility(visibleState = entry.visibility, enter = enter, exit = exit) {
                        entry.row.content(ListItemDefaults.segmentedShapes(entry.index, entry.count))
                    }
                }
            }
        }
    }
}

/** A row a group shows: one of its rows, or one on its way out, until that way ends. */
private class ShownRow(
    var row: ProfileRow,
    var index: Int,
    var count: Int,
    val visibility: MutableTransitionState<Boolean>,
)

/**
 * The rows of a [ProfileGroup] as they come and go (`docs/DESIGN_SYSTEM.md` §5.1): a row that comes after the group
 * first showed (one a switch reveals) grows in, and one that goes shrinks away in its place, on the motion scheme's
 * springs, so the rows and groups below move rather than jump; the others take the shapes of their place among the rows
 * that stay.
 */
private class ShownRows {
    private var shown = emptyList<ShownRow>()
    private var started = false

    fun update(rows: List<ProfileRow>): List<ShownRow> {
        val previous = shown.associateBy { it.row.key }
        val current =
            rows.mapIndexed { index, row ->
                previous[row.key]?.also {
                    it.row = row
                    it.index = index
                    it.count = rows.size
                    it.visibility.targetState = true
                } ?: ShownRow(row, index, rows.size, MutableTransitionState(!started).apply { targetState = true })
            }
        val keys = current.mapTo(HashSet()) { it.row.key }
        val result = current.toMutableList()
        shown.forEachIndexed { place, entry ->
            val gone = entry.visibility.isIdle && !entry.visibility.currentState
            if (entry.row.key !in keys && !gone) {
                entry.visibility.targetState = false
                // After the row that was above it, if it stays; at the top otherwise.
                val above = shown.subList(0, place).lastOrNull { it.row.key in keys || it in result }
                result.add(above?.let { result.indexOf(it) + 1 } ?: 0, entry)
            }
        }
        shown = result
        started = true
        return result
    }
}

/** A row of a [ProfileGroup]: its [key], and its content for the shapes of its place. */
internal class ProfileRow(
    val key: Any,
    val content: @Composable (shapes: ListItemShapes) -> Unit,
)

/** A section's heading above its rows (`docs/DESIGN_SYSTEM.md` §3.3: `titleSmallEmphasized` in `primary`). */
@Composable
internal fun ProfileSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Text(
        text = title,
        modifier =
            modifier
                .padding(start = spacing.large, end = spacing.large, top = spacing.small, bottom = spacing.medium)
                .semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmallEmphasized,
    )
}
