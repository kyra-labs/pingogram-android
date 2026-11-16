package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.NewProfileTrack

/**
 * The song the user picked for their profile music (boards 13a, 13b), made ready for TDLib to upload: a copy on the
 * device with its length and tags, read on the device.
 */
interface PickedSongRepository {
    /**
     * Copies the file at [uri] (the file picker's content URI, as a string) into the app's upload cache and reads its
     * length, title and artist; a song without a title takes its file's name. `DomainError.FileUnavailable` for what
     * cannot be read as audio, `DomainError.FileTooLarge` for a file too large to send. The copy stays until
     * [discardSong].
     */
    suspend fun prepareSong(uri: String): Outcome<NewProfileTrack>

    /** Deletes [path], a copy [prepareSong] made, in the background: once TDLib uploaded it or failed to. */
    fun discardSong(path: String)
}
