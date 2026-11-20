package dev.kyralabs.pingogram.feature.media.storyalbum

import dev.kyralabs.pingogram.core.common.result.DomainError

/** What an opened album does once. */
sealed interface StoryAlbumEffect {
    /** The album was deleted: the screen leaves. */
    data object Deleted : StoryAlbumEffect

    /** A change was refused with [error]. */
    data class ShowError(
        val error: DomainError,
    ) : StoryAlbumEffect
}
