package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/** A page of a user's profile music (boards 13a, 13b, td_api.tl `getUserProfileAudios`), out of [totalCount]. */
data class ProfileTracks(
    val totalCount: Int,
    val tracks: ImmutableList<Audio>,
)

/** A song the user adds to their profile music: its file on the device, length and tags. */
data class NewProfileTrack(
    val path: String,
    val durationSeconds: Int,
    val title: String,
    val performer: String,
)
