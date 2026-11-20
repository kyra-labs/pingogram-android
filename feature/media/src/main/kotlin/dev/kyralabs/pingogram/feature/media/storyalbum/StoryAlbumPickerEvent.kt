package dev.kyralabs.pingogram.feature.media.storyalbum

/** What the user does while picking stories for an album. */
sealed interface StoryAlbumPickerEvent {
    /** Picks story [storyId], or unpicks it. */
    data class Toggle(
        val storyId: Int,
    ) : StoryAlbumPickerEvent

    /** Creates the album, or adds or removes the picked stories. */
    data object Confirm : StoryAlbumPickerEvent

    /** Reads the stories again after a failure. */
    data object Retry : StoryAlbumPickerEvent
}
