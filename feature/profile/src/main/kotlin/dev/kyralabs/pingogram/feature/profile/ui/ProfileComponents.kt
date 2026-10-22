package dev.kyralabs.pingogram.feature.profile.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.theme.isAmoledBlack
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
    Column(modifier) {
        if (title != null) ProfileSectionHeader(title)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { index, row ->
                key(row.key) { row.content(ListItemDefaults.segmentedShapes(index, rows.size)) }
            }
        }
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
