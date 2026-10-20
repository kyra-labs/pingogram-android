package dev.kyralabs.pingogram.core.domain.model

/**
 * A file the user picked to send (`REQUIREMENTS.md` FR-C5), copied into the app's private cache and prepared for
 * TDLib by `AttachmentRepository.prepare`: a photo downscaled and re-encoded, a video as it is with its size, length
 * and a thumbnail, any other file as a document. [path] is the copy, which TDLib uploads from; [size] its length in
 * bytes.
 *
 * `toString()` leaves out paths and names, which can be personal.
 */
sealed interface Attachment {
    val path: String
    val size: Long

    /** A JPEG of [width] × [height] pixels, upright (its EXIF orientation applied), without metadata. */
    data class Photo(
        override val path: String,
        override val size: Long,
        val width: Int,
        val height: Int,
    ) : Attachment {
        override fun toString() = "Photo(size=$size, width=$width, height=$height, …)"
    }

    /**
     * A video as it was picked: [width] × [height] as it plays (its rotation applied), [duration] in seconds, and a
     * still of it as [thumbnail]; null if none could be made.
     */
    data class Video(
        override val path: String,
        override val size: Long,
        val width: Int,
        val height: Int,
        val duration: Int,
        val thumbnail: AttachmentThumbnail?,
    ) : Attachment {
        override fun toString() = "Video(size=$size, width=$width, height=$height, duration=$duration, …)"
    }

    /** Any file, sent as it is under its original [fileName]; [mimeType] may be empty when unknown. */
    data class Document(
        override val path: String,
        override val size: Long,
        val fileName: String,
        val mimeType: String,
    ) : Attachment {
        override fun toString() = "Document(size=$size, mimeType=$mimeType, …)"
    }
}

/** A JPEG still of a video in the app's cache, at most 320 pixels on its longer side, sent along with it. */
data class AttachmentThumbnail(
    val path: String,
    val width: Int,
    val height: Int,
) {
    override fun toString() = "AttachmentThumbnail(width=$width, height=$height, …)"
}

/** How a picked file is sent. */
enum class AttachmentKind {
    /**
     * From the gallery or the camera: photos as photos and videos as videos, which group into an album; anything that
     * cannot be read as either (a GIF, an image Android cannot decode) as a document.
     */
    Media,

    /** From the file picker: every file as a document, as it is, also photos and videos. */
    Document,
}

/**
 * A voice message the user recorded in the composer (`REQUIREMENTS.md` FR-C5), ready to send: the audio at [path] in
 * the app's cache (a file from `VoiceNoteRepository.newVoiceNoteFile`), [duration] in seconds, at least 1, and its
 * loudness as Telegram's 5-bit [waveform].
 *
 * `toString()` leaves out the path.
 */
data class RecordedVoiceNote(
    val path: String,
    val duration: Int,
    val waveform: Waveform,
) {
    override fun toString() = "RecordedVoiceNote(duration=$duration, $waveform, …)"
}
