package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.persistentListOf

/** The bookmark sheet's states for previews and screenshot tests: a new bookmark, and one bookmarked already. */
internal object BookmarkSheetPreviewData {
    val new =
        BookmarkSheetUiState(
            messageId = 42,
            sender = UiText.Plain("Meera Iyer"),
            text = UiText.Plain("@you can you check the price for 4 people? The good homestay fills up fast"),
            tags =
                persistentListOf(
                    TagChoice(BookmarkTag(1, "Trips", 3), isChosen = true),
                    TagChoice(BookmarkTag(2, "To read", 5), isChosen = false),
                    TagChoice(BookmarkTag(3, "Work", 1), isChosen = false),
                ),
            note = "",
            isBookmarked = false,
        )

    val bookmarked = new.copy(note = "Ask about breakfast", isBookmarked = true)
}

@PingogramPreviews
@Composable
private fun BookmarkSheetPreview() {
    PingogramPreview { Surface { BookmarkSheetContent(BookmarkSheetPreviewData.new, onEvent = {}) } }
}

@PingogramPreviews
@Composable
private fun BookmarkSheetBookmarkedPreview() {
    PingogramPreview { Surface { BookmarkSheetContent(BookmarkSheetPreviewData.bookmarked, onEvent = {}) } }
}
