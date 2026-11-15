package dev.kyralabs.pingogram.feature.chat.ui.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.LocalBookmarksLauncher
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Saved Messages' Bookmarks card (`docs/DESIGN_SYSTEM.md` §6 "Bookmarks", the owner's board 34c), shown on "All" only
 * once a message is bookmarked: a row on `surfaceContainerHigh` with `large` corners, at most `contentMaxWidth`, the
 * filled bookmark in `primary`, "Bookmarks · 3" in `labelLargeEmphasized` over "On this phone" in `bodySmall`
 * `onSurfaceVariant`, and a chevron; one link for screen readers, which opens the Bookmarks page.
 */
@Composable
internal fun SavedBookmarksCard(
    count: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = pluralStringResource(R.plurals.saved_bookmarks_card, count, count)
    Surface(
        onClick = onOpen,
        modifier =
            modifier
                .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall)
                .semantics { role = Role.Button },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = MaterialTheme.spacing.medium,
                    vertical = MaterialTheme.spacing.small,
                ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(UiR.drawable.ic_bookmark_filled),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
            Text(
                text = stringResource(R.string.saved_bookmarks_on_this_phone),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
            )
            Icon(
                painterResource(UiR.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The Bookmarks card's slot on "All" ([showsTags]): null until a message is bookmarked, or where the app provides no
 * Bookmarks page.
 */
@Composable
internal fun rememberBookmarksCard(
    showsTags: Boolean,
    viewModel: SavedBookmarksViewModel,
): (@Composable () -> Unit)? {
    val count by viewModel.count.collectAsStateWithLifecycle()
    val open = LocalBookmarksLauncher.current
    return if (showsTags && count > 0 && open != null) {
        { SavedBookmarksCard(count, open) }
    } else {
        null
    }
}
