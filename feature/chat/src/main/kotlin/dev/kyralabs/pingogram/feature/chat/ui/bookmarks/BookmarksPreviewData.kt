package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import kotlinx.collections.immutable.persistentListOf

/** The Bookmarks page's states for previews and screenshot tests: loading, empty, all bookmarks and one tag's. */
internal object BookmarksPreviewData {
    private val trips = BookmarkFilter(1, "Trips", color = 3, count = 2)
    private val toRead = BookmarkFilter(2, "To read", color = 5, count = 1)
    private val work = BookmarkFilter(3, "Work", color = 1, count = 1)

    private val items =
        persistentListOf(
            BookmarkItem(
                chatId = 1,
                messageId = 10,
                chatTitle = "Goa trip",
                photo = null,
                avatarShape = AvatarShape.Group,
                sender = "Meera Iyer",
                text = "@you can you check the price for 4 people? The good homestay fills up fast",
                date = "9:41 AM",
                note = "Ask about breakfast",
                tags = persistentListOf(trips.copy(count = 0)),
            ),
            BookmarkItem(
                chatId = 2,
                messageId = 20,
                chatTitle = "Android Developers",
                photo = null,
                avatarShape = AvatarShape.Channel,
                sender = "",
                text = "Compose 1.12 is out: shared element transitions, a cache window for lazy lists and more.",
                date = "Yesterday",
                note = "",
                tags = persistentListOf(toRead.copy(count = 0), work.copy(count = 0)),
            ),
            BookmarkItem(
                chatId = 3,
                messageId = 30,
                chatTitle = "Arjun Mehta",
                photo = null,
                avatarShape = AvatarShape.Circle,
                sender = "",
                text = "Train leaves at 6:10 from platform 4. Bring the tickets!",
                date = "Mon",
                note = "",
                tags = persistentListOf(trips.copy(count = 0)),
            ),
        )

    val content =
        BookmarksUiState.Content(
            filters = persistentListOf(BookmarkFilter(null, "", color = 0, count = 3), trips, toRead, work),
            selectedTagId = null,
            items = items,
        )

    val filtered =
        content.copy(
            selectedTagId = 1,
            items = items.removingAt(1),
        )

    /** Search open with "trip" typed. */
    val searching =
        content.copy(
            query = "trip",
            items = persistentListOf(items[0], items[2]),
        )

    /** Search that found nothing. */
    val noMatches = content.copy(query = "recipes", items = persistentListOf())
}

@PingogramPreviews
@Composable
private fun BookmarksContentPreview() {
    PingogramPreview { BookmarksScreen(BookmarksPreviewData.content, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun BookmarksFilteredPreview() {
    PingogramPreview { BookmarksScreen(BookmarksPreviewData.filtered, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun BookmarksEmptyPreview() {
    PingogramPreview { BookmarksScreen(BookmarksUiState.Empty, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun BookmarksLoadingPreview() {
    PingogramPreview { BookmarksScreen(BookmarksUiState.Loading, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun BookmarksSearchingPreview() {
    PingogramPreview { BookmarksScreen(BookmarksPreviewData.searching, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun BookmarksNoMatchesPreview() {
    PingogramPreview { BookmarksScreen(BookmarksPreviewData.noMatches, onEvent = {}, onBack = {}) }
}
