package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.Document
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A file message without its caption (`REQUIREMENTS.md` FR-C4), padded in its bubble: an icon for its kind
 * ([documentKind]), its name (shortened in the middle, so the extension stays) and its size, and what can be done
 * with it, by [fileState]:
 *
 * - **Not downloaded**: a download [IconButton] ([onDownload]); none if TDLib cannot download it.
 * - **Downloading**: "1.2 MB / 4.5 MB" and a determinate wavy ring around a cancel button ([onCancel]).
 * - **Downloaded**: tapping the file opens it ([onOpen]).
 * - **Being sent** ([upload]): a wavy ring of the upload around a cancel button ([onCancelUpload]).
 *
 * @param fileState the file's state, from `FileRepository`; null while unknown, shown as not downloaded.
 * @param colors the bubble's colors, `MaterialTheme.pingogramColors.incomingBubble` or `outgoingBubble`.
 */
@Composable
internal fun MessageDocument(
    content: MessageContent.Document,
    fileState: FileState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
    upload: MessageUpload? = null,
    onCancelUpload: () -> Unit = {},
) {
    val document = content.document
    val state = fileState ?: FileState.NotDownloaded(document.file.size, canBeDownloaded = true)
    val opens = state is FileState.Downloaded
    // TDLib may not know the size yet when the state is first reported.
    val size =
        when (state) {
            is FileState.NotDownloaded -> state.size
            is FileState.Downloading -> state.size
            is FileState.Downloaded -> state.size
        }.takeIf { it > 0 } ?: document.file.size
    Row(
        modifier =
            if (opens) {
                modifier.clickable(onClickLabel = stringResource(R.string.content_open), onClick = onOpen)
            } else {
                modifier.semantics(mergeDescendants = true) {}
            },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContentIcon(documentKind(document).icon, colors)
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        ) {
            Text(
                text = document.fileName.ifBlank { stringResource(UiR.string.preview_file) },
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text =
                    if (state is FileState.Downloading) {
                        stringResource(
                            R.string.content_progress_of,
                            fileSizeText(state.downloadedSize),
                            fileSizeText(size),
                        )
                    } else {
                        fileSizeText(size)
                    },
                color = colors.content,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        when {
            upload != null -> UploadRing(upload, colors.accent, colors.track, onCancelUpload)
            state is FileState.NotDownloaded -> if (state.canBeDownloaded) DownloadButton(colors, onDownload)
            state is FileState.Downloading -> CancelDownloadButton(state.progress, colors, onCancel)
            else -> Unit
        }
    }
}

@Composable
private fun DownloadButton(
    colors: BubbleColors,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.iconButtonColors(contentColor = colors.accent),
    ) {
        Icon(painterResource(UiR.drawable.ic_download), contentDescription = stringResource(R.string.content_download))
    }
}

@Composable
private fun CancelDownloadButton(
    progress: Float,
    colors: BubbleColors,
    onClick: () -> Unit,
) {
    DownloadRing(progress = progress, colors = colors) {
        IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.iconButtonColors(contentColor = colors.accent),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_close),
                contentDescription = stringResource(R.string.content_cancel_download),
            )
        }
    }
}

/** What kind of file a document is, for its icon. */
internal enum class DocumentKind(
    @param:DrawableRes val icon: Int,
) {
    Pdf(UiR.drawable.ic_picture_as_pdf_filled),
    Archive(UiR.drawable.ic_folder_zip_filled),
    Image(UiR.drawable.ic_image_filled),
    Audio(UiR.drawable.ic_audio_file_filled),
    Video(UiR.drawable.ic_video_file_filled),
    Other(UiR.drawable.ic_description_filled),
}

/**
 * The kind of [document], by its MIME type and then by its file name's extension, since senders often send
 * `application/octet-stream`.
 */
internal fun documentKind(document: Document): DocumentKind = documentKind(document.fileName, document.mimeType)

/** The kind of a file named [fileName] of type [mimeType] (may be empty), as for a [Document]. */
internal fun documentKind(
    fileName: String,
    mimeType: String,
): DocumentKind {
    val type = mimeType.lowercase(Locale.ROOT)
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
    return when {
        type == "application/pdf" || extension == "pdf" -> DocumentKind.Pdf
        type in ArchiveMimeTypes || extension in ArchiveExtensions -> DocumentKind.Archive
        type.startsWith("image/") || extension in ImageExtensions -> DocumentKind.Image
        type.startsWith("audio/") || extension in AudioExtensions -> DocumentKind.Audio
        type.startsWith("video/") || extension in VideoExtensions -> DocumentKind.Video
        else -> DocumentKind.Other
    }
}

private val ArchiveMimeTypes =
    setOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/vnd.rar",
        "application/x-rar-compressed",
        "application/x-7z-compressed",
        "application/x-tar",
        "application/gzip",
        "application/x-gzip",
        "application/x-bzip2",
        "application/x-xz",
    )
private val ArchiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")
private val ImageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg", "tif", "tiff")
private val AudioExtensions = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav")
private val VideoExtensions = setOf("mp4", "m4v", "mkv", "mov", "webm", "avi", "3gp")
