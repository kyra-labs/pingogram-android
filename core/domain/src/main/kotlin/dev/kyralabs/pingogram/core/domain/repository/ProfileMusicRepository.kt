package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.NewProfileTrack
import dev.kyralabs.pingogram.core.domain.model.ProfileTracks

/**
 * Profile music (boards 13a, 13b): the songs a user shows on their profile, which anyone who may see them plays, and
 * the user's own list, which they add to, reorder and remove from. td_api.tl announces no change to the list, so each
 * call asks TDLib again.
 */
interface ProfileMusicRepository {
    /** At most [limit] of user [userId]'s profile songs from the [offset]-th, in their order. */
    suspend fun tracks(
        userId: Long,
        offset: Int,
        limit: Int,
    ): Outcome<ProfileTracks>

    /** Adds [track] to the user's own profile music, uploading its file. */
    suspend fun add(track: NewProfileTrack): Outcome<Unit>

    /** Removes the song of file [fileId] from the user's own profile music. */
    suspend fun remove(fileId: Int): Outcome<Unit>

    /** Moves the song of file [fileId] after the one of [afterFileId] (0: first) in the user's own profile music. */
    suspend fun move(
        fileId: Int,
        afterFileId: Int,
    ): Outcome<Unit>
}
