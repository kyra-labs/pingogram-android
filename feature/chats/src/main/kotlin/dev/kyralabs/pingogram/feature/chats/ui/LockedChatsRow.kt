package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.LocalLockedChatsLauncher
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The "Locked chats" row at the end of the main list (`docs/adr/0029-locked-chats.md`), while chats are locked: a
 * lock on a neutral avatar placeholder, and that the chats open with the passcode, never which or how many. It opens
 * the locked chats through `core:ui`'s `LocalLockedChatsLauncher`, and shows nothing where nothing provides one.
 * Disabled in selection mode ([enabled]), as the archive row.
 */
@Composable
internal fun LockedChatsRow(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val open = LocalLockedChatsLauncher.current ?: return
    val title = stringResource(R.string.chat_list_locked_chats)
    val insets = rowInsets()
    ListItem(
        onClick = open,
        // Inset like the chats' rows ([ChatListItem]).
        modifier = modifier.padding(insets),
        enabled = enabled,
        contentPadding = insetContentPadding(insets),
        leadingContent = {
            Avatar(
                photo = null,
                name = title,
                id = LOCKED_AVATAR_ID,
                size = MaterialTheme.sizes.avatarRow,
                placeholderIcon = UiR.drawable.ic_lock,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.chat_list_locked_chats_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** The "Locked chats" row as an item after the chats, its modifier from [rowModifier]. */
internal fun LazyListScope.lockedChatsRow(
    enabled: Boolean,
    rowModifier: LazyItemScope.() -> Modifier,
) = item(key = LOCKED_CHATS_ROW, contentType = LOCKED_CHATS_ROW) {
    LockedChatsRow(enabled = enabled, modifier = rowModifier())
}

@PingogramPreviews
@Composable
private fun LockedChatsRowPreview() {
    PingogramPreview {
        CompositionLocalProvider(LocalLockedChatsLauncher provides {}) { LockedChatsRow(enabled = true) }
    }
}

/** The neutral placeholder pair (`surfaceContainerHighest`), as the archive row's. */
private const val LOCKED_AVATAR_ID = 3L

private const val LOCKED_CHATS_ROW = "lockedChats"
