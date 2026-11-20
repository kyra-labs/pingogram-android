package dev.kyralabs.pingogram.feature.media.storyalbum

import dev.kyralabs.pingogram.core.common.result.DomainError

/** What picking stories for an album does once. */
sealed interface StoryAlbumPickerEffect {
    /** The album was created or changed: picking is over. */
    data object Done : StoryAlbumPickerEffect

    /** Saving failed with [error]; the picked stories stay. */
    data class ShowError(
        val error: DomainError,
    ) : StoryAlbumPickerEffect
}
