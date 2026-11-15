package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import kotlinx.collections.immutable.persistentListOf

/** The tag editor's states for previews and screenshot tests: the tags, one renamed, a new one, a deletion asked. */
internal object BookmarkTagsPreviewData {
    val content =
        BookmarkTagsUiState.Content(
            tags =
                persistentListOf(
                    TagRow(1, "Trip & travel", color = 3, count = 12, alone = 8),
                    TagRow(2, "Work", color = 5, count = 9, alone = 4),
                    TagRow(3, "Read later", color = 2, count = 6, alone = 6),
                    TagRow(4, "Recipes", color = 1, count = 3, alone = 2),
                    TagRow(5, "Ideas", color = 7, count = 5, alone = 1),
                ),
        )

    val renaming = content.copy(draft = TagDraft(id = 1, name = "Trip & travel", color = 3))

    val adding = content.copy(draft = TagDraft(id = null, name = "", color = 5))

    val deleting = content.copy(deleting = content.tags[3])

    val empty = BookmarkTagsUiState.Content(tags = persistentListOf())
}

@PingogramPreviews
@Composable
private fun BookmarkTagsPreview() {
    PingogramPreview { BookmarkTagsScreen(BookmarkTagsPreviewData.content, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun BookmarkTagsRenamingPreview() {
    PingogramPreview { BookmarkTagsScreen(BookmarkTagsPreviewData.renaming, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun BookmarkTagsDeletingPreview() {
    PingogramPreview { BookmarkTagsScreen(BookmarkTagsPreviewData.deleting, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun BookmarkTagsEmptyPreview() {
    PingogramPreview { BookmarkTagsScreen(BookmarkTagsPreviewData.empty, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun BookmarkTagsLoadingPreview() {
    PingogramPreview { BookmarkTagsScreen(BookmarkTagsUiState.Loading, onEvent = {}, onBack = {}) }
}
