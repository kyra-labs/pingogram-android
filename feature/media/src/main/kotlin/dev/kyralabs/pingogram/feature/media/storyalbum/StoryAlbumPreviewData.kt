package dev.kyralabs.pingogram.feature.media.storyalbum

import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** The story albums' states for previews and goldens: placeholders for pictures, as previews load no files. */
internal object StoryAlbumPreviewData {
    val albums =
        StoryAlbumsUiState.Content(
            persistentListOf(
                StoryAlbumTile(1, "Hampi trip", cover = null),
                StoryAlbumTile(2, "Coffee runs", cover = null),
                StoryAlbumTile(3, "Monsoon", cover = null),
                StoryAlbumTile(4, "Biscuit the cat, and a very long album name", cover = null),
                StoryAlbumTile(5, "Diwali", cover = null),
            ),
        )

    private val days = listOf("Oct 3", "Oct 3", "Oct 4", "Oct 4", "Oct 4", "Oct 5", "Oct 5", "Oct 6", "Oct 6")

    val album =
        StoryAlbumUiState.Content(
            name = "Hampi trip",
            posterName = "Neha Kulkarni",
            totalCount = days.size,
            stories =
                days
                    .mapIndexed { index, day -> AlbumStory(index + 1, day, picture = null, isVideo = index % 4 == 1) }
                    .toImmutableList(),
        )

    val loadingMore = album.copy(totalCount = 20, loadingMore = true)

    val moreFailed = album.copy(totalCount = 20, moreError = DomainError.Network)

    val empty = album.copy(totalCount = 0, stories = persistentListOf())

    val error = StoryAlbumUiState.Error(DomainError.Network)
}
