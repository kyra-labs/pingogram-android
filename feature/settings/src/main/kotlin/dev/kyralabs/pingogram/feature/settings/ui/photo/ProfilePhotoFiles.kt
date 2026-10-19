package dev.kyralabs.pingogram.feature.settings.ui.photo

/**
 * The image files of a new profile photo, which need the platform: the image the user picked, copied into the app's
 * cache for TDLib to upload, and deleted afterwards. Behind an interface so the view model never touches a `Context`
 * and tests use a fake; [AndroidProfilePhotoFiles] in the app. Public only because `EditProfileViewModel`'s injected
 * constructor takes it.
 */
interface ProfilePhotoFiles {
    /**
     * Copies the image at [uri] (the photo picker's content URI, as a string) into the app's cache as a JPEG whose
     * longest side is at most [MAX_SIDE_PX] pixels, upright, and returns its path; null if the image cannot be read.
     * Copies of earlier photos still there are deleted first.
     */
    suspend fun prepare(uri: String): String?

    /** Deletes [path], a copy [prepare] made, once TDLib uploaded it or failed to. */
    suspend fun delete(path: String)

    companion object {
        /**
         * The longest side of a prepared photo. Avatars show at 640 × 640 px at most (`ProfilePhoto.big`), and
         * Telegram crops the photo to a square itself, so this keeps enough for any crop without uploading a camera's
         * full resolution.
         */
        const val MAX_SIDE_PX = 1280
    }
}
