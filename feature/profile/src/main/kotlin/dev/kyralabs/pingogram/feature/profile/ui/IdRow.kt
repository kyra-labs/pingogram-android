package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.feature.profile.R

/** A user's or chat's ID, left to right, over "ID"; a tap copies it ([onCopy]). */
@Composable
internal fun IdRow(
    id: Long,
    shapes: ListItemShapes,
    onCopy: () -> Unit,
) {
    val copy = stringResource(R.string.profile_copy)
    SegmentedListItem(
        onClick = onCopy,
        // Names what a tap does; the list item's click supplies the action.
        modifier = Modifier.semantics { onClick(label = copy, action = null) },
        shapes = shapes,
        colors = profileRowColors(),
        supportingContent = { Text(stringResource(R.string.profile_id)) },
    ) {
        Text(id.toString(), style = MaterialTheme.typography.bodyLarge.numericContent)
    }
}
