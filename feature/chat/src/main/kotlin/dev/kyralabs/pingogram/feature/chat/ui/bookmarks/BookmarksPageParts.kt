package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import java.text.NumberFormat

// Parts of the Bookmarks page's top bar and filters (BookmarksScreen), board 34b.

/** "Bookmarks" over "27 saved on this phone" (board 34b), one heading. */
@Composable
internal fun BookmarksTitle(content: BookmarksUiState.Content?) {
    val total = content?.filters?.firstOrNull { it.tagId == null }?.count
    Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(
            text = stringResource(R.string.bookmarks_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        if (total != null) {
            Text(
                text = pluralStringResource(R.plurals.bookmarks_saved, total, total),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** A filter's name, then its count set apart in `labelMedium` (board 34b). */
@Composable
internal fun FilterLabel(
    name: String,
    count: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.labelLarge.userContent)
        Text(
            text = NumberFormat.getIntegerInstance().format(count),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
