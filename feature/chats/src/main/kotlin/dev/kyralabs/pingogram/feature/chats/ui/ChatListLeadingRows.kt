package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The rows above the chats: the story rings, the archive row of [state] (enabled outside selection mode) and, with
 * [hasPinned], the "Pinned" header above the pinned chats' group. [rowModifier] places each as a row of the list.
 */
internal fun LazyListScope.leadingRows(
    stories: (@Composable (Modifier) -> Unit)?,
    state: ChatListUiState,
    hasPinned: Boolean,
    onEvent: (ChatListEvent) -> Unit,
    rowModifier: LazyItemScope.() -> Modifier,
) {
    val archive = state.archive
    if (stories != null) {
        item(key = STORIES_ROW, contentType = STORIES_ROW) { stories(rowModifier()) }
    }
    if (archive != null) {
        item(key = ARCHIVE_ROW, contentType = ARCHIVE_ROW) {
            ArchiveRow(
                entry = archive,
                enabled = state.selection == null,
                onClick = { onEvent(ChatListEvent.OpenArchive) },
                modifier = rowModifier(),
                onStyle = { onEvent(ChatListEvent.SetArchiveRowStyle(it)) },
            )
        }
    }
    if (hasPinned) {
        item(key = PINNED_HEADER, contentType = PINNED_HEADER) { PinnedHeader(rowModifier()) }
    }
}

/** The key of the list's first row, which [KeepTopShown] keeps in view while the list shows its top. */
internal fun firstRowKey(
    hasStories: Boolean,
    hasArchive: Boolean,
    pinnedCount: Int,
    items: List<ChatListItemModel>,
    communities: List<CommunityEntry>,
): Any? =
    when {
        hasStories -> STORIES_ROW
        hasArchive -> ARCHIVE_ROW
        pinnedCount > 0 -> PINNED_HEADER
        communities.firstOrNull()?.before == 0 -> communityKey(communities.first().id)
        else -> items.firstOrNull()?.id
    }

internal const val ARCHIVE_ROW = "archive"

internal const val STORIES_ROW = "stories"

internal const val PINNED_HEADER = "pinned"
