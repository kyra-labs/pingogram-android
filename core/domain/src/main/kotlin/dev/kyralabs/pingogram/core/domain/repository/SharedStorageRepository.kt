package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.SharedFile

/**
 * Copies of downloaded TDLib files in the device's shared storage, where other apps see them (`REQUIREMENTS.md` FR-C6
 * "save to downloads"): the gallery for photos, videos and GIFs of the media viewer, the Downloads collection for any
 * file of a conversation. Files leave the app only on the user's request, and only where Telegram lets the content be
 * saved (`MessageProperties.canBeSaved`), which the caller checks.
 */
interface SharedStorageRepository {
    /**
     * Saving needs the storage permission first: on Android 8–9 until it is granted. Android 10 and later need none.
     */
    fun needsStoragePermission(): Boolean

    /** Copies the photo, video or GIF [file] into the gallery, true once it is there; false if it could not be. */
    suspend fun saveToGallery(file: SharedFile): Boolean

    /** Copies [file] into the public Downloads collection, true once it is there; false if it could not be. */
    suspend fun saveToDownloads(file: SharedFile): Boolean
}
