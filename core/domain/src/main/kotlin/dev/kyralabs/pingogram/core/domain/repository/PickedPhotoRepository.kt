package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome

/**
 * The image the user picked for a photo of their own or of a chat (`REQUIREMENTS.md` FR-S1, FR-P3, FR-P4), made ready
 * for TDLib to upload: Edit profile, a new group or channel, and a group's or channel's info page prepare it here, so
 * every such photo is prepared the same way.
 */
interface PickedPhotoRepository {
    /**
     * Copies the image at [uri] (the photo picker's content URI, as a string) into the app's upload cache as an upright
     * JPEG without metadata, at most `PickedPhotoLimits.MAX_SIDE_PX` on its longer side: its path.
     * `DomainError.PhotoInvalid` for what cannot be read as an image. The copy stays until [discardPhoto].
     */
    suspend fun preparePhoto(uri: String): Outcome<String>

    /**
     * Deletes [path], a copy [preparePhoto] made, in the background: once TDLib uploaded it or failed to, or when
     * nothing will use it.
     */
    fun discardPhoto(path: String)
}

/** The size of a prepared photo. */
object PickedPhotoLimits {
    /**
     * The longer side of a prepared photo. Avatars show at 640 × 640 px at most (`ProfilePhoto.big`), and Telegram
     * crops the photo to a square itself, so this keeps enough for any crop without uploading a camera's full
     * resolution.
     */
    const val MAX_SIDE_PX = 1280
}
