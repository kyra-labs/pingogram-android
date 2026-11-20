package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList

/** What a profile's Stories tab shows (boards 11a, 11b, owner decision 2026-11-20). */
@Immutable
sealed interface ProfileStoriesUiState {
    data object Loading : ProfileStoriesUiState

    /** The profile has neither stories nor albums: "No stories yet". */
    data object Empty : ProfileStoriesUiState

    data class Error(
        val error: DomainError,
    ) : ProfileStoriesUiState

    data class Content(
        /** The albums' covers above the grid, each with its story count once read. */
        val albums: ImmutableList<StoryAlbumTile>,
        /** The stories on the profile, pinned first and then newest first, each with its day. */
        val stories: ImmutableList<AlbumStory>,
        /** How many stories the profile holds, as Telegram counts them. */
        val totalCount: Int,
        /** The next page is on its way. */
        val loadingMore: Boolean = false,
        /** Why the next page failed, until it is asked for again. */
        val moreError: DomainError? = null,
    ) : ProfileStoriesUiState {
        /** Every story on the profile is loaded. */
        val endReached: Boolean get() = stories.size >= totalCount
    }
}
