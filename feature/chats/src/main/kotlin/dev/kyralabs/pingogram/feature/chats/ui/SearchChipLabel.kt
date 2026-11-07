package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R

/**
 * A search chip's [label], then the [count] TDLib gave for it, if any, in `labelMedium` with tabular figures
 * (`docs/DESIGN_SYSTEM.md` §6 "Global search"), capped at "999+" as the unread badges are. Screen readers hear one
 * text: "Messages, 42 results".
 */
@Composable
internal fun ChipLabel(
    label: String,
    count: Int?,
) {
    if (count == null) {
        Text(label)
        return
    }
    val resources = LocalResources.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.clearAndSetSemantics {
                contentDescription = resources.getQuantityString(R.plurals.search_filter_count, count, label, count)
            },
    ) {
        Text(label)
        Text(
            unreadCountText(count),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

private const val TABULAR_FIGURES = "tnum"
