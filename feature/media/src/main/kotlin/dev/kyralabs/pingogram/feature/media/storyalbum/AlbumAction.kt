package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.annotation.StringRes
import dev.kyralabs.pingogram.feature.media.R

/** What the user's own album offers in its top bar's menu (owner decision 2026-11-20). */
internal enum class AlbumAction(
    @param:StringRes val label: Int,
) {
    Rename(R.string.story_album_rename),
    AddStories(R.string.story_album_add_stories),
    RemoveStories(R.string.story_album_remove_stories),
    Delete(R.string.story_album_delete),
}
