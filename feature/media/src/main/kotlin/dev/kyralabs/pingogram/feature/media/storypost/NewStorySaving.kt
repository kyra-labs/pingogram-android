package dev.kyralabs.pingogram.feature.media.storypost

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentThumbnail

// The picked items survive process death as one string of fields each (features share no code: the conversation's
// attach preview keeps its own the same way). Only photos and videos are kept; their copies stay in the upload cache.

private const val KEY_ITEMS = "newStory.items"
private const val KEY_SELECTED = "newStory.selected"
private const val KEY_CAMERA_FILE = "newStory.cameraFile"
private const val KEY_STARTED = "newStory.started"
private const val SEPARATOR = "\u001F"
private const val PHOTO = "photo"
private const val VIDEO = "video"
private const val PHOTO_FIELDS = 5
private const val VIDEO_FIELDS = 9

/** The numbers a photo keeps (size, width, height) and a video (and length, the still's width and height). */
private const val PHOTO_NUMBERS = 3
private const val VIDEO_NUMBERS = 6

/** Where a video's still's path is among its fields. */
private const val VIDEO_THUMBNAIL = 6

internal fun SavedStateHandle.saveItems(
    items: List<Attachment>,
    selected: String?,
) {
    this[KEY_ITEMS] = items.mapNotNullTo(ArrayList()) { it.saved() }
    this[KEY_SELECTED] = selected
}

internal fun SavedStateHandle.restoredItems(): List<Attachment> =
    get<ArrayList<String>>(KEY_ITEMS).orEmpty().mapNotNull(::restoredAttachment)

internal fun SavedStateHandle.restoredSelected(): String? = get<String>(KEY_SELECTED)

/** The camera's file while the camera app is open, so a photo taken across process death still arrives. */
internal var SavedStateHandle.cameraFile: String?
    get() = get<String>(KEY_CAMERA_FILE)
    set(value) {
        this[KEY_CAMERA_FILE] = value
    }

/** Whether the screen already opened the photo picker by itself, once, as it first showed. */
internal var SavedStateHandle.started: Boolean
    get() = get<Boolean>(KEY_STARTED) == true
    set(value) {
        this[KEY_STARTED] = value
    }

private fun Attachment.saved(): String? =
    when (this) {
        is Attachment.Photo -> {
            listOf(PHOTO, path, size, width, height)
        }

        is Attachment.Video -> {
            listOf(
                VIDEO,
                path,
                size,
                width,
                height,
                duration,
                thumbnail?.path.orEmpty(),
                thumbnail?.width ?: 0,
                thumbnail?.height ?: 0,
            )
        }

        is Attachment.Document -> {
            null
        }
    }?.joinToString(SEPARATOR)

private fun restoredAttachment(saved: String): Attachment? {
    val fields = saved.split(SEPARATOR)
    // Every field after the kind and the path is a number, but a video's still's path.
    val numbers = fields.filterIndexed { index, _ -> index > 1 && index != VIDEO_THUMBNAIL }.map { it.toLongOrNull() }
    return when {
        fields[0] == PHOTO && fields.size == PHOTO_FIELDS -> restoredPhoto(fields[1], numbers)
        fields[0] == VIDEO && fields.size == VIDEO_FIELDS -> restoredVideo(fields[1], fields[VIDEO_THUMBNAIL], numbers)
        else -> null
    }
}

/** A photo at [path] from its saved size, width and height. */
private fun restoredPhoto(
    path: String,
    numbers: List<Long?>,
): Attachment.Photo? {
    val (size, width, height) = numbers.filterNotNull().takeIf { it.size == PHOTO_NUMBERS } ?: return null
    return Attachment.Photo(path, size, width.toInt(), height.toInt())
}

/**
 * A video at [path] with its still at [thumbnail] (none when empty), from its saved size, width, height, length and
 * the still's width and height.
 */
private fun restoredVideo(
    path: String,
    thumbnail: String,
    numbers: List<Long?>,
): Attachment.Video? {
    val values = numbers.filterNotNull().takeIf { it.size == VIDEO_NUMBERS } ?: return null
    val (size, width, height) = values
    val (duration, stillWidth, stillHeight) = values.drop(PHOTO_NUMBERS)
    val still =
        thumbnail.takeIf { it.isNotEmpty() }?.let { AttachmentThumbnail(it, stillWidth.toInt(), stillHeight.toInt()) }
    return Attachment.Video(path, size, width.toInt(), height.toInt(), duration.toInt(), still)
}
