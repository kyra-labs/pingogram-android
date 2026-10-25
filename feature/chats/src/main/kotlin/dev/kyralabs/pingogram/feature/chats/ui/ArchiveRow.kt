package dev.kyralabs.pingogram.feature.chats.ui

import android.icu.text.ListFormatter
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.spokenList
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The "Archived chats" row at the top of the main list (`REQUIREMENTS.md` FR-L3), which opens the archive: an archive
 * on a neutral avatar placeholder, the names of the first archived chats TDLib has loaded or else how many chats the
 * archive has, and its unread chats in a badge that is always in the muted style, as the archive mostly holds chats
 * the user put out of the way. Its title is emphasized while it has unread chats, as a chat's is
 * (`docs/DESIGN_SYSTEM.md` §3.3). It cannot be swiped or selected; in selection mode it is disabled ([enabled]).
 *
 * For screen readers it is one node: "Archived chats, Ada, Grace and Alan, 2 unread chats".
 */
@Composable
internal fun ArchiveRow(
    entry: ArchiveEntry,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.chat_list_archived_chats)
    val supporting = archiveSummary(entry)
    val unread =
        if (entry.unreadCount > 0) {
            val count = entry.unreadCount
            LocalResources.current.getQuantityString(R.plurals.chat_list_a11y_unread_chats, count, count)
        } else {
            null
        }
    val description = spokenList(LocalResources.current, listOf(title, supporting, unread))
    val colors = MaterialTheme.colorScheme
    val insets = rowInsets()
    ListItem(
        onClick = onClick,
        // Inset like the chats' rows ([ChatListItem]).
        modifier = modifier.padding(insets).semantics { contentDescription = description },
        enabled = enabled,
        contentPadding = insetContentPadding(insets),
        leadingContent = {
            Avatar(
                photo = null,
                name = title,
                id = ARCHIVE_AVATAR_ID,
                size = MaterialTheme.sizes.avatarRow,
                placeholderIcon = UiR.drawable.ic_archive,
            )
        },
        trailingContent = {
            if (entry.unreadCount > 0) {
                Badge(
                    modifier = Modifier.clearAndSetSemantics {},
                    containerColor = colors.surfaceVariant,
                    contentColor = colors.onSurfaceVariant,
                ) { Text(unreadCountText(entry.unreadCount)) }
            }
        },
        supportingContent = {
            Text(
                text = supporting,
                modifier = Modifier.clearAndSetSemantics {},
                color = colors.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
        },
    ) {
        Text(
            text = title,
            modifier = Modifier.clearAndSetSemantics {},
            color = colors.onSurface,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style =
                with(MaterialTheme.typography) { if (entry.unreadCount > 0) titleMediumEmphasized else titleMedium },
        )
    }
}

/** "Rocket Club, Old Team, and Newsletter" in the language's list style, or "14 chats" until any are loaded. */
@Composable
private fun archiveSummary(entry: ArchiveEntry): String {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(entry.titles, entry.chatCount, resources, locale) {
        if (entry.titles.isEmpty()) {
            resources.getQuantityString(R.plurals.chat_list_archive_chat_count, entry.chatCount, entry.chatCount)
        } else {
            ListFormatter.getInstance(locale).format(entry.titles)
        }
    }
}

/**
 * The avatar placeholder's id: it picks the neutral `surfaceContainerHighest` color pair, the fourth of the avatar
 * colors (`docs/DESIGN_SYSTEM.md` §2.2), so the archive does not look like one of the chats.
 */
private const val ARCHIVE_AVATAR_ID = 3L

@PingogramPreviews
@Composable
private fun ArchiveRowPreview() {
    PingogramPreview { ArchiveRow(entry = ChatListPreviewData.archive, enabled = true, onClick = {}) }
}
