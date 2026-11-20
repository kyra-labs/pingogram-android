package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/** What picking stories for an album shows (owner decision 2026-11-20). */
@Immutable
sealed interface StoryAlbumPickerUiState {
    data object Loading : StoryAlbumPickerUiState

    data class Error(
        val error: DomainError,
    ) : StoryAlbumPickerUiState

    data class Content(
        /** What picking is for: creating, adding to or removing from an album. */
        val purpose: StoryAlbumPickPurpose,
        /** The stories to pick from, each with its day; empty when there are none. */
        val stories: ImmutableList<AlbumStory>,
        val selected: ImmutableSet<Int> = persistentSetOf(),
        /** The change is on its way to Telegram. */
        val isSaving: Boolean = false,
    ) : StoryAlbumPickerUiState {
        /** Something is picked and nothing is being saved. */
        val canConfirm: Boolean get() = selected.isNotEmpty() && !isSaving
    }
}

/** What the stories are picked for. */
enum class StoryAlbumPickPurpose {
    Create,
    Add,
    Remove,
}
