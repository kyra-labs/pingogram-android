package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.AttachmentThumbnail

// The attach preview and the poll form in a SavedStateHandle, which keeps only Bundle types, for process death: each
// attachment as one string of its fields, separated by a NUL character, which neither paths nor the cleaned file
// names of copies hold. Attachments still being prepared are not kept: their picker's read grant does not survive.

/** Keeps the preview's [kind] and its ready [attachments]; nothing for no preview. */
internal fun SavedStateHandle.savePreview(
    kind: AttachmentKind?,
    attachments: List<Attachment>,
) {
    this[KEY_PREVIEW_KIND] = kind?.name
    this[KEY_PREVIEW_ITEMS] = attachments.mapTo(ArrayList()) { it.saved() }
}

/** The preview [savePreview] kept: its kind and attachments; null when there was none or nothing is left of it. */
internal fun SavedStateHandle.restoredPreview(): Pair<AttachmentKind, List<Attachment>>? {
    val kind = get<String>(KEY_PREVIEW_KIND)?.let { name -> AttachmentKind.entries.firstOrNull { it.name == name } }
    val attachments = get<ArrayList<String>>(KEY_PREVIEW_ITEMS).orEmpty().mapNotNull(::restoredAttachment)
    return if (kind != null && attachments.isNotEmpty()) kind to attachments else null
}

/** Keeps the poll form: whether it is open, and what it holds. */
internal fun SavedStateHandle.savePoll(
    open: Boolean,
    draft: PollDraft,
) {
    this[KEY_POLL_OPEN] = open
    this[KEY_POLL_QUESTION] = draft.question
    this[KEY_POLL_OPTIONS] = ArrayList(draft.options)
    this[KEY_POLL_FLAGS] = booleanArrayOf(draft.isAnonymous, draft.allowsMultipleAnswers, draft.isQuiz)
    this[KEY_POLL_CORRECT] = draft.correctOptionIndex ?: NO_CORRECT_OPTION
    this[KEY_POLL_REQUIRED_QUIZ] = draft.requiredQuiz
}

/** Whether the poll form was open. */
internal fun SavedStateHandle.restoredPollOpen(): Boolean = get<Boolean>(KEY_POLL_OPEN) == true

/** What the poll form held, or an empty form. */
internal fun SavedStateHandle.restoredPoll(): PollDraft {
    val flags = get<BooleanArray>(KEY_POLL_FLAGS)?.takeIf { it.size == POLL_FLAGS } ?: return PollDraft()
    return PollDraft(
        question = get<String>(KEY_POLL_QUESTION).orEmpty(),
        options = get<ArrayList<String>>(KEY_POLL_OPTIONS).orEmpty(),
        isAnonymous = flags[0],
        allowsMultipleAnswers = flags[1],
        isQuiz = flags[2],
        correctOptionIndex = get<Int>(KEY_POLL_CORRECT)?.takeIf { it >= 0 },
        requiredQuiz = get<Boolean>(KEY_POLL_REQUIRED_QUIZ),
    )
}

/** This attachment as one string of its fields; [restoredAttachment] reads it back. */
internal fun Attachment.saved(): String =
    when (this) {
        is Attachment.Photo -> {
            listOf(PHOTO, path, size, width, height)
        }

        is Attachment.Video -> {
            val still = thumbnail
            listOf(VIDEO, path, size, width, height, duration) +
                listOf(still?.path.orEmpty(), still?.width ?: 0, still?.height ?: 0)
        }

        is Attachment.Document -> {
            listOf(DOCUMENT, path, size, fileName, mimeType)
        }
    }.joinToString(SEPARATOR)

/** The attachment that [Attachment.saved] wrote as [saved]; null for a string it cannot read. */
internal fun restoredAttachment(saved: String): Attachment? {
    val fields = saved.split(SEPARATOR)
    val path = fields.getOrNull(1)?.takeIf { it.isNotEmpty() }
    val size = fields.getOrNull(2)?.toLongOrNull()
    if (path == null || size == null) return null
    return when (fields[0]) {
        PHOTO -> {
            restoredPhoto(fields, path, size)
        }

        VIDEO -> {
            restoredVideo(fields, path, size)
        }

        DOCUMENT -> {
            fields.getOrNull(FILE_NAME)?.let { fileName ->
                Attachment.Document(path, size, fileName, fields.getOrNull(MIME_TYPE).orEmpty())
            }
        }

        else -> {
            null
        }
    }
}

private fun restoredPhoto(
    fields: List<String>,
    path: String,
    size: Long,
): Attachment.Photo? {
    val width = fields.int(WIDTH)
    val height = fields.int(HEIGHT)
    return if (width != null && height != null) Attachment.Photo(path, size, width, height) else null
}

private fun restoredVideo(
    fields: List<String>,
    path: String,
    size: Long,
): Attachment.Video? {
    val width = fields.int(WIDTH)
    val height = fields.int(HEIGHT)
    val duration = fields.int(DURATION)
    if (width == null || height == null || duration == null) return null
    val still =
        fields.getOrNull(THUMBNAIL_PATH)?.takeIf { it.isNotEmpty() }?.let { stillPath ->
            AttachmentThumbnail(stillPath, fields.int(THUMBNAIL_WIDTH) ?: 0, fields.int(THUMBNAIL_HEIGHT) ?: 0)
        }
    return Attachment.Video(path, size, width, height, duration, still)
}

private fun List<String>.int(index: Int): Int? = getOrNull(index)?.toIntOrNull()

private const val SEPARATOR = "\u0000"
private const val PHOTO = "photo"
private const val VIDEO = "video"
private const val DOCUMENT = "document"

// Field positions after the kind, the path and the size.
private const val WIDTH = 3
private const val HEIGHT = 4
private const val DURATION = 5
private const val THUMBNAIL_PATH = 6
private const val THUMBNAIL_WIDTH = 7
private const val THUMBNAIL_HEIGHT = 8
private const val FILE_NAME = 3
private const val MIME_TYPE = 4

private const val POLL_FLAGS = 3
private const val NO_CORRECT_OPTION = -1

/** The kind of the open preview ([AttachmentKind.name]), for process death; null for none. */
internal const val KEY_PREVIEW_KIND = "chat.attachKind"

/** The preview's ready attachments ([saved]), for process death. */
internal const val KEY_PREVIEW_ITEMS = "chat.attachItems"

/** The file the camera app was asked to write a photo into, while it is open; for process death. */
internal const val KEY_CAMERA_FILE = "chat.cameraFile"

private const val KEY_POLL_OPEN = "chat.pollOpen"
private const val KEY_POLL_QUESTION = "chat.pollQuestion"
private const val KEY_POLL_OPTIONS = "chat.pollOptions"
private const val KEY_POLL_FLAGS = "chat.pollFlags"
private const val KEY_POLL_CORRECT = "chat.pollCorrect"
private const val KEY_POLL_REQUIRED_QUIZ = "chat.pollRequiredQuiz"
