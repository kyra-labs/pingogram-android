package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.SharedFile
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.SharedStorageRepository
import dev.kyralabs.pingogram.feature.chat.ui.content.largestPhotoSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * "Save to Downloads" of a conversation's documents, music, videos, GIFs and photos (`REQUIREMENTS.md` FR-C6), for
 * `ChatViewModel`: the files of the messages it is given ([save], which the message actions checked TDLib allows: the
 * chat lets their content be saved) go into the public Downloads collection through the [storage].
 *
 * - **The file** is the message's own, a photo's largest size. One that is not on the device downloads first
 *   ([FileRepository.download], at visible priority, so its bubble shows the progress where it has one), with a
 *   snackbar saying so ([SelectionEffect.Saving]); one TDLib cannot download, or whose download stops, is not saved.
 * - **Android 8–9** need the storage permission first: the screen asks for it
 *   ([SelectionEffect.RequestStoragePermission]), the messages waiting meanwhile in the [savedState], so a grant still
 *   saves them after process death ([onPermissionResult], which finds them again through `messagesOf`); a refusal is a
 *   snackbar.
 * - **Then** a snackbar says how many files were saved, with an action that opens the file when it is one
 *   ([SelectionEffect.Saved]), or that none could be ([SelectionEffect.SaveFailed]).
 *
 * One save at a time; another meanwhile is ignored. Runs in [scope], the view model's, on the main thread.
 */
internal class ChatSaving(
    private val files: FileRepository,
    private val storage: SharedStorageRepository,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private var saving: Job? = null

    /** Saves the files of [messages] to Downloads, after the storage permission where it is needed. */
    fun save(messages: List<Message>) {
        val toSave = messages.filter { it.downloadsFile != null }
        if (toSave.isEmpty() || saving?.isActive == true) return
        if (storage.needsStoragePermission()) {
            savedState[KEY_SAVE_AFTER_PERMISSION] = toSave.map { it.id }.toLongArray()
            onEffect(SelectionEffect.RequestStoragePermission)
        } else {
            run(toSave)
        }
    }

    /**
     * The system's answer to the storage permission: [granted] saves the messages that waited for it, those of them
     * [messagesOf] still finds; a refusal says that saving needs it.
     */
    fun onPermissionResult(
        granted: Boolean,
        messagesOf: suspend (List<Long>) -> List<Message>,
    ) {
        val ids = savedState.remove<LongArray>(KEY_SAVE_AFTER_PERMISSION) ?: return
        if (!granted) {
            onEffect(SelectionEffect.StoragePermissionDenied)
            return
        }
        scope.launch { save(messagesOf(ids.toList())) }
    }

    private fun run(messages: List<Message>) {
        saving =
            scope.launch {
                val toSave = messages.mapNotNull { message -> message.downloadsFile?.let { message.id to it } }
                var announced = false
                val saved =
                    toSave.mapNotNull { (_, file) ->
                        val path =
                            downloadedPath(file.fileId) {
                                if (!announced) onEffect(SelectionEffect.Saving)
                                announced = true
                            }
                        path?.takeIf { storage.saveToDownloads(SharedFile(it, file.mimeType, file.baseName)) }?.let {
                            ChatEffect.OpenFile(it, file.mimeType)
                        }
                    }
                onEffect(
                    if (saved.isEmpty()) {
                        SelectionEffect.SaveFailed
                    } else {
                        SelectionEffect.Saved(saved.size, open = saved.singleOrNull()?.takeIf { toSave.size == 1 })
                    },
                )
            }
    }

    /**
     * The path of file [fileId] once it is on the device, downloading it first if needed ([onDownload] is told then);
     * null for a file TDLib cannot download, or whose download fails or stops.
     */
    private suspend fun downloadedPath(
        fileId: Int,
        onDownload: () -> Unit,
    ): String? {
        val state = files.observe(fileId).first()
        return when {
            state is FileState.Downloaded -> {
                state.path
            }

            state is FileState.NotDownloaded && !state.canBeDownloaded -> {
                null
            }

            else -> {
                onDownload()
                download(fileId)
            }
        }
    }

    /** Downloads file [fileId]: its path once it is on the device; null if the download fails or stops. */
    private suspend fun download(fileId: Int): String? {
        if (files.download(fileId, DownloadPriority.Visible) is Outcome.Failure) return null
        var wasActive = false
        return files
            .observe(fileId)
            .mapNotNull { state ->
                when (state) {
                    is FileState.Downloaded -> {
                        Downloaded(state.path)
                    }

                    is FileState.NotDownloaded -> {
                        if (state.canBeDownloaded) null else Downloaded(null)
                    }

                    is FileState.Downloading -> {
                        // Stopped once it ran: failed, or cancelled from its bubble.
                        if (!state.isActive && wasActive) Downloaded(null) else null.also { wasActive = state.isActive }
                    }
                }
            }.first()
            .path
    }

    /** Where a download ended: at [path], or nowhere. */
    private class Downloaded(
        val path: String?,
    )

    internal companion object {
        /** The messages whose files wait for the storage permission, which survives process death. */
        const val KEY_SAVE_AFTER_PERMISSION = "chat.saveAfterPermission"
    }
}

/**
 * The file [fileId] of a message as it is saved to Downloads, of type [mimeType] if its extension names none, named
 * [baseName] with its extension.
 */
internal class DownloadsFile(
    val fileId: Int,
    val mimeType: String,
    val baseName: String,
)

/**
 * The file of this message that "Save to Downloads" saves: a document's or song's under the name it was sent with,
 * a video's, a GIF's or a photo's (its largest size) named after the message ("Pingogram_<id>": nothing about the chat
 * or the sender); null for other messages and for self-destructing media.
 */
internal val Message.downloadsFile: DownloadsFile?
    get() {
        val ownName = "$NAME_PREFIX$id"
        return when (val content = content) {
            is MessageContent.Document -> {
                val document = content.document
                DownloadsFile(
                    document.file.id,
                    document.mimeType.ifBlank { GENERIC_TYPE },
                    baseNameOf(document.fileName, ownName),
                )
            }

            is MessageContent.Audio -> {
                DownloadsFile(content.audio.file.id, AUDIO_TYPE, baseNameOf(content.audio.fileName, ownName))
            }

            is MessageContent.Video -> {
                if (content.isSecret) null else DownloadsFile(content.video.file.id, VIDEO_TYPE, ownName)
            }

            is MessageContent.Animation -> {
                val animation = content.animation
                if (content.isSecret) {
                    null
                } else {
                    DownloadsFile(
                        animation.file.id,
                        animation.mimeType.ifBlank { VIDEO_TYPE },
                        ownName,
                    )
                }
            }

            is MessageContent.Photo -> {
                val size = largestPhotoSize(content.photo.sizes)
                if (content.isSecret || size == null) null else DownloadsFile(size.file.id, PHOTO_TYPE, ownName)
            }

            else -> {
                null
            }
        }
    }

/** [fileName] without its extension, which the saved file takes from the file itself; [fallback] if it is blank. */
private fun baseNameOf(
    fileName: String,
    fallback: String,
): String = fileName.substringBeforeLast('.').trim().ifEmpty { fallback }

private const val NAME_PREFIX = "Pingogram_"
private const val GENERIC_TYPE = "application/octet-stream"
private const val AUDIO_TYPE = "audio/mpeg"
private const val VIDEO_TYPE = "video/mp4"
private const val PHOTO_TYPE = "image/jpeg"
