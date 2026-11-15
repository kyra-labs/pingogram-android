package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/**
 * The bookmark sheet of message [messageId] (the owner's board 34a): who sent it and what it says ([sender], [text];
 * null while it loads), the tags with which of them are chosen, the note the bookmark has, whether the message is
 * bookmarked already (then "Remove bookmark" shows) and whether a change is on its way.
 */
@Immutable
data class BookmarkSheetUiState(
    val messageId: Long,
    val sender: UiText?,
    val text: UiText?,
    val tags: ImmutableList<TagChoice>,
    val note: String,
    val isBookmarked: Boolean,
    val isSaving: Boolean = false,
)

/** A tag in the sheet: chosen or not. */
@Immutable
data class TagChoice(
    val tag: BookmarkTag,
    val isChosen: Boolean,
)

/** What the sheet says once. */
sealed interface BookmarkEffect {
    /** Saved: "Bookmarked". */
    data object Saved : BookmarkEffect

    /** Removed: "Bookmark removed". */
    data object Removed : BookmarkEffect

    /** Something failed. */
    data class ShowError(
        val error: DomainError,
    ) : BookmarkEffect
}
