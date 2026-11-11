package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import kotlinx.collections.immutable.ImmutableList

/** A story album's cover tile (board 11a): its [name] and the picture its [cover] shows, if any. */
@Immutable
data class StoryAlbumTile(
    val id: Int,
    val name: String,
    val cover: AlbumPicture?,
)

/**
 * A picture of an album: the photo's or the video still's file, by the size it is decoded at, over [minithumbnail]
 * blurred until it shows. [fileIdFor] picks the file for a size in pixels.
 */
@Immutable
data class AlbumPicture(
    val minithumbnail: Minithumbnail?,
    val files: ImmutableList<AlbumPictureFile>,
) {
    /** The smallest file whose shorter side covers [sizePx], else the largest; null without files. */
    fun fileIdFor(sizePx: Int): Int? {
        val bySide = files.sortedBy { minOf(it.width, it.height) }
        return (bySide.firstOrNull { minOf(it.width, it.height) >= sizePx } ?: bySide.lastOrNull())?.fileId
    }
}

/** One file of an [AlbumPicture], [width] × [height] pixels. */
@Immutable
data class AlbumPictureFile(
    val fileId: Int,
    val width: Int,
    val height: Int,
)

/** What a profile's story albums section shows (board 11a). */
@Immutable
sealed interface StoryAlbumsUiState {
    /** Not asked yet, still loading, failed, or the chat has no albums: the section shows nothing. */
    data object Hidden : StoryAlbumsUiState

    data class Content(
        val albums: ImmutableList<StoryAlbumTile>,
    ) : StoryAlbumsUiState
}

/** One story of an opened album (board 11b): its picture and the day it was posted, "Oct 3". */
@Immutable
data class AlbumStory(
    val id: Int,
    val date: String,
    val picture: AlbumPicture?,
    val isVideo: Boolean,
)

/** What an opened album shows (board 11b). */
@Immutable
sealed interface StoryAlbumUiState {
    data object Loading : StoryAlbumUiState

    data class Error(
        val error: DomainError,
    ) : StoryAlbumUiState

    data class Content(
        /** The album's name; empty while TDLib has not said it. */
        val name: String,
        /** Who posted the album's stories: the chat's title. */
        val posterName: String,
        /** How many stories the album holds, as Telegram counts them. */
        val totalCount: Int,
        val stories: ImmutableList<AlbumStory>,
        /** The next page is on its way. */
        val loadingMore: Boolean = false,
        /** Why the next page failed, until it is asked for again. */
        val moreError: DomainError? = null,
    ) : StoryAlbumUiState {
        /** Every story of the album is loaded. */
        val endReached: Boolean get() = stories.size >= totalCount
    }
}

/** What the opened album's user does. */
sealed interface StoryAlbumEvent {
    /** The grid nears its end: load the next page. */
    data object LoadMore : StoryAlbumEvent

    /** Try again after a failure. */
    data object Retry : StoryAlbumEvent
}
