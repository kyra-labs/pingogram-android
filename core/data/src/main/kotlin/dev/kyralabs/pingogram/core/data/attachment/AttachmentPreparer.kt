package dev.kyralabs.pingogram.core.data.attachment

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.AttachmentThumbnail
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Prepares picked files for TDLib (`REQUIREMENTS.md` FR-C5, §6.2), in a new directory of [files] each, through the
 * [content] seam (the pickers' URIs) and the [media] seam (decoders):
 *
 * - **Copy**: the content is copied first, under its name, since TDLib needs a local path and the picker's grant may
 *   end. Files over TDLib's limit ([MAX_FILE_SIZE]) are refused before or while copying; empty ones too.
 * - **Photos** (images from the gallery or the camera): always decoded and written anew as a JPEG ([JPEG_QUALITY]),
 *   upright (EXIF orientation applied), at most [MAX_PHOTO_SIDE] pixels on the longer side and never enlarged
 *   ([photoPlan]). Writing anew drops the metadata, the location included; the copy with it is deleted at once.
 * - **Videos**: sent as they are, with their size as they play, their length and a JPEG still of at most
 *   [MAX_THUMBNAIL_SIDE] pixels.
 * - **Documents**: as they are, under their name, which TDLib takes from the path. From the gallery, what Android
 *   cannot decode as a photo or video (a GIF, a format it does not know) goes as a document too, unchanged.
 *
 * Blocking IO: call it on the IO dispatcher. Cancelling it stops a copy at the next buffer; a failed or cancelled
 * preparation leaves nothing behind.
 */
internal class AttachmentPreparer(
    private val files: UploadFiles,
    private val content: ContentSource,
    private val media: MediaProcessor,
) {
    /** The file at [uri] prepared as [kind] says; a photo at most [photoMaxSide] pixels on its longer side. */
    suspend fun prepare(
        uri: String,
        kind: AttachmentKind,
        photoMaxSide: Int = MAX_PHOTO_SIDE,
    ): Outcome<Attachment> {
        val info = content.describe(uri)
        if ((info.size ?: 0) > MAX_FILE_SIZE) return Outcome.Failure(DomainError.FileTooLarge)
        return inNewDirectory { directory ->
            val copy = File(directory, safeFileName(info.displayName))
            when (val copied = copy(uri, copy)) {
                Copy.Unreadable -> {
                    Outcome.Failure(DomainError.FileUnavailable)
                }

                Copy.TooLarge -> {
                    Outcome.Failure(DomainError.FileTooLarge)
                }

                is Copy.Done -> {
                    val type = info.mimeType ?: mimeTypeOf(copy.name)
                    val document = { Attachment.Document(copy.path, copied.size, copy.name, type.orEmpty()) }
                    val attachment =
                        when {
                            kind == AttachmentKind.Document -> document()
                            type == GIF -> document()
                            type?.startsWith(IMAGE) == true -> photo(copy, directory, photoMaxSide) ?: document()
                            type?.startsWith(VIDEO) == true -> video(copy, copied.size, directory) ?: document()
                            else -> document()
                        }
                    Outcome.Success(attachment)
                }
            }
        }
    }

    /**
     * The photo in [file], which the camera app wrote, prepared like a photo from the gallery; [file] itself is left
     * as it is.
     */
    fun prepareCameraPhoto(file: File): Outcome<Attachment> {
        if (file.length() <= 0) return Outcome.Failure(DomainError.FileUnavailable)
        return inNewDirectory { directory ->
            photo(file, directory, deleteSource = false)?.let { Outcome.Success(it) }
                ?: Outcome.Failure(DomainError.FileUnavailable)
        }
    }

    /**
     * [block] with a new directory of [files], which is deleted unless [block] succeeds, also when it throws or is
     * cancelled.
     */
    private inline fun inNewDirectory(block: (File) -> Outcome<Attachment>): Outcome<Attachment> {
        val directory =
            try {
                files.newUploadDirectory()
            } catch (_: IOException) {
                return Outcome.Failure(DomainError.FileUnavailable)
            }
        var prepared = false
        try {
            return block(directory).also { prepared = it is Outcome.Success }
        } finally {
            if (!prepared) directory.deleteRecursively()
        }
    }

    /** Copies the content at [uri] to [target], checking for cancellation after every buffer. */
    private suspend fun copy(
        uri: String,
        target: File,
    ): Copy {
        val input = content.open(uri) ?: return Copy.Unreadable
        return try {
            val size = input.use { source -> target.outputStream().use { sink -> source.copyAtMost(sink) } }
            when (size) {
                null -> Copy.TooLarge

                // TDLib refuses empty files ("File must be non-empty").
                0L -> Copy.Unreadable

                else -> Copy.Done(size)
            }
        } catch (_: IOException) {
            Copy.Unreadable
        }
    }

    /**
     * Copies this stream to [sink], checking for cancellation before every buffer: the number of bytes, or null as
     * soon as there are more than [MAX_FILE_SIZE].
     */
    private suspend fun InputStream.copyAtMost(sink: OutputStream): Long? {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = read(buffer)
            if (read < 0) return total
            total += read
            if (total > MAX_FILE_SIZE) return null
            sink.write(buffer, 0, read)
        }
    }

    /**
     * The image in [source] written anew into [directory] as the photo to send, at most [maxSide] pixels on its longer
     * side; null when it cannot be decoded. The source, which still has its metadata, is deleted once the photo is
     * written if [deleteSource].
     */
    private fun photo(
        source: File,
        directory: File,
        maxSide: Int = MAX_PHOTO_SIDE,
        deleteSource: Boolean = true,
    ): Attachment.Photo? {
        val image = media.imageInfo(source) ?: return null
        val plan = photoPlan(image.width, image.height, image.orientation, maxSide)
        val target = File.createTempFile(PHOTO_PREFIX, JPEG_SUFFIX, directory)
        return if (media.writePhoto(source, plan, target) && target.length() > 0) {
            if (deleteSource) source.delete()
            Attachment.Photo(target.path, target.length(), plan.width, plan.height)
        } else {
            target.delete()
            null
        }
    }

    /** The video in [source] with its size, length and a still written into [directory]; null when unreadable. */
    private fun video(
        source: File,
        size: Long,
        directory: File,
    ): Attachment.Video? {
        val info = media.videoInfo(source) ?: return null
        val (width, height) = info.displaySize
        val (thumbnailWidth, thumbnailHeight) = fitWithin(width, height, MAX_THUMBNAIL_SIDE)
        val still = File.createTempFile(THUMBNAIL_PREFIX, JPEG_SUFFIX, directory)
        val thumbnail =
            if (media.writeVideoThumbnail(source, thumbnailWidth, thumbnailHeight, still) && still.length() > 0) {
                AttachmentThumbnail(still.path, thumbnailWidth, thumbnailHeight)
            } else {
                still.delete()
                null
            }
        return Attachment.Video(source.path, size, width, height, durationSeconds(info.durationMillis), thumbnail)
    }

    /** What came of copying a picked file. */
    private sealed interface Copy {
        data object Unreadable : Copy

        data object TooLarge : Copy

        data class Done(
            val size: Long,
        ) : Copy
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PHOTO_PREFIX = "photo"
        const val THUMBNAIL_PREFIX = "thumbnail"
        const val JPEG_SUFFIX = ".jpg"
        const val GIF = "image/gif"
        const val IMAGE = "image/"
        const val VIDEO = "video/"
    }
}

/** The largest file TDLib sends: 4000 MB (td/telegram/files/FileLoaderUtils.cpp); Telegram may allow less. */
internal const val MAX_FILE_SIZE: Long = 4000L shl 20

/**
 * [displayName] as a file name in its own directory: path separators and control characters replaced, at most
 * [MAX_FILE_NAME_BYTES] bytes of UTF-8 with the extension kept, and [FALLBACK_FILE_NAME] when nothing is left.
 */
internal fun safeFileName(displayName: String?): String {
    val cleaned =
        displayName
            .orEmpty()
            .map { char -> if (char == '/' || char == '\\' || char.isISOControl()) '_' else char }
            .joinToString("")
            .trim()
    return when {
        cleaned.isEmpty() || cleaned.all { it == '.' } -> FALLBACK_FILE_NAME
        cleaned.toByteArray().size <= MAX_FILE_NAME_BYTES -> cleaned
        else -> shortened(cleaned)
    }
}

/** [name], longer than [MAX_FILE_NAME_BYTES] bytes of UTF-8, cut to them with its extension kept. */
private fun shortened(name: String): String {
    val extension = name.substringAfterLast('.', missingDelimiterValue = "").take(MAX_EXTENSION_LENGTH)
    val suffix = if (extension.isEmpty()) "" else ".$extension"
    var base = name.removeSuffix(suffix)
    while (base.isNotEmpty() && (base + suffix).toByteArray().size > MAX_FILE_NAME_BYTES) base = base.dropLast(1)
    return (base + suffix).ifBlank { FALLBACK_FILE_NAME }
}

/** The MIME type of a few common extensions of [fileName], for providers that tell none; null for others. */
internal fun mimeTypeOf(fileName: String): String? =
    when (fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heif"
        "gif" -> "image/gif"
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        "mkv" -> "video/x-matroska"
        else -> null
    }

/** A video's length in whole seconds, as Telegram counts it: rounded, and at least 1 for a video with any length. */
internal fun durationSeconds(millis: Long): Int =
    when {
        millis <= 0 -> 0
        else -> (millis / MILLIS_PER_SECOND.toDouble()).roundToInt().coerceAtLeast(1)
    }

private const val FALLBACK_FILE_NAME = "file"
private const val MAX_FILE_NAME_BYTES = 255
private const val MAX_EXTENSION_LENGTH = 16
private const val MILLIS_PER_SECOND = 1000
