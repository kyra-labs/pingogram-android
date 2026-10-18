package dev.kyralabs.pingogram.feature.media.ui.files

/**
 * What the viewer does with a downloaded file outside the app that needs the platform: saving it into the gallery
 * (`REQUIREMENTS.md` FR-C6), behind an interface so the view model never touches a `Context` and tests use a fake.
 * [AndroidMediaFileActions] in the app. Sharing starts another app, so the screen does it, with its activity.
 * Public only because `MediaViewerViewModel`'s injected constructor takes it.
 */
interface MediaFileActions {
    /**
     * Saving into the gallery needs the storage permission first: on Android 8–9 until it is granted. Android 10 and
     * later need none.
     */
    fun needsStoragePermission(): Boolean

    /** Copies [item] into the gallery, true once it is there; false if it could not be. */
    suspend fun saveToGallery(item: GalleryItem): Boolean
}

/**
 * A downloaded photo, video or GIF to save into the gallery: the file at [path], of the type its extension names or
 * else [fallbackMimeType], saved as [baseName] with the file's extension. The name says nothing about the chat or the
 * sender.
 */
data class GalleryItem(
    val path: String,
    val fallbackMimeType: String,
    val baseName: String,
) {
    // The path ends in the file's name.
    override fun toString() = "GalleryItem(fallbackMimeType=$fallbackMimeType, …)"
}
