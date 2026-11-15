package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import kotlinx.collections.immutable.ImmutableList

/** The Bookmarks page (the owner's board 34b). */
@Immutable
sealed interface BookmarksUiState {
    /** The bookmarks are being read. */
    data object Loading : BookmarksUiState

    /** Nothing bookmarked yet (board 34d). */
    data object Empty : BookmarksUiState

    /**
     * The tag filters ([filters], "All" first) with the one chosen ([selectedTagId], null for "All"), and the bookmarks
     * it and the search's words ([query], null while search is closed) let through, the newest first (owner decision
     * 2026-11-15).
     */
    data class Content(
        val filters: ImmutableList<BookmarkFilter>,
        val selectedTagId: Long?,
        val items: ImmutableList<BookmarkItem>,
        val query: String? = null,
    ) : BookmarksUiState {
        /** Words are typed and no bookmark has them all. */
        val hasNoMatches: Boolean get() = items.isEmpty() && !query.isNullOrBlank()
    }
}

/** A filter chip: a tag ([tagId] and [name], in sender hue [color]) or "All" ([tagId] null), with how many it holds. */
@Immutable
data class BookmarkFilter(
    val tagId: Long?,
    val name: String,
    val color: Int,
    val count: Int,
)

/**
 * A bookmark's card: its chat (name, photo, avatar shape), who sent the message and what it said, when it was saved,
 * the note and the tags.
 */
@Immutable
data class BookmarkItem(
    val chatId: Long,
    val messageId: Long,
    val chatTitle: String,
    val photo: ProfilePhoto?,
    val avatarShape: AvatarShape,
    val sender: String,
    val text: String,
    val date: String,
    val note: String,
    val tags: ImmutableList<BookmarkFilter>,
)

/** What the page takes. */
sealed interface BookmarksEvent {
    /** A filter chip: [tagId], or "All" for null. */
    data class SelectTag(
        val tagId: Long?,
    ) : BookmarksEvent

    /** A card: its message opens in its chat. */
    data class Open(
        val chatId: Long,
        val messageId: Long,
    ) : BookmarksEvent

    /** "Remove bookmark" on a card. */
    data class Remove(
        val chatId: Long,
        val messageId: Long,
    ) : BookmarksEvent

    /** "Undo" after a removal. */
    data object UndoRemove : BookmarksEvent

    /** The top bar's search button. */
    data object OpenSearch : BookmarksEvent

    /** The search field's text. */
    data class Search(
        val query: String,
    ) : BookmarksEvent

    /** The search field's back, or system back while it shows. */
    data object CloseSearch : BookmarksEvent
}

/** What the page says or does once. */
sealed interface BookmarksEffect {
    /** Opens message [messageId] of chat [chatId]. */
    data class OpenMessage(
        val chatId: Long,
        val messageId: Long,
    ) : BookmarksEffect

    /** "Bookmark removed", with "Undo". */
    data object Removed : BookmarksEffect

    data class ShowError(
        val error: DomainError,
    ) : BookmarksEffect
}
