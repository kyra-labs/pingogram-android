package dev.kyralabs.pingogram.feature.media.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onSuccess
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.SharedFile
import dev.kyralabs.pingogram.core.domain.repository.SharedStorageRepository
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Share, Save and Copy of the viewer's current item (`REQUIREMENTS.md` FR-C6 "save to downloads", FR-C10), for
 * [MediaViewerViewModel]: what the toolbar offers ([state]) and what its buttons do. Main thread only.
 *
 * - **Allowed** only where Telegram lets the content leave it: the chat does not protect its content ([isProtected],
 *   true while the chat is not known) and TDLib says the message can be saved ([canBeSaved], `ChatMediaRepository`),
 *   asked once per item for the viewer's life; until it answers, and if it fails, the actions do not show.
 *   Self-destructing media never reaches the viewer.
 * - **The file** is the item's full one, which the viewer downloads anyway at visible priority ([MediaDownloads]).
 *   A tap while it downloads waits for it, with the download's progress on the button; turning the page drops the
 *   wait. A file TDLib cannot download, or a failed download ([onDownloadFailed]), ends it.
 * - **Share** goes out as [MediaViewerEffect.ShareFile], which the screen hands to the system's share sheet.
 * - **Copy**, for photos only, goes out as [MediaViewerEffect.CopyImage], which the screen puts on the clipboard.
 * - **Save** copies the file into the gallery ([SharedStorageRepository.saveToGallery]), then a snackbar says how
 *   that went. On Android 8–9 the storage permission comes first ([permission]).
 */
internal class MediaSharing(
    private val canBeSaved: suspend (messageId: Long) -> Outcome<Boolean>,
    isProtected: Flow<Boolean>,
    private val sharedStorage: SharedStorageRepository,
    savedStateHandle: SavedStateHandle,
    private val scope: CoroutineScope,
    private val effects: SendChannel<MediaViewerEffect>,
) {
    /** The current item, as [focus] last set it, and the states of its files. */
    private var current: Target? = null
    private var files: Map<Int, FileState> = emptyMap()

    private val isProtected: StateFlow<Boolean> =
        isProtected.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, true)

    /** TDLib's answers, by message id; the items asked about meanwhile. */
    private val savable = MutableStateFlow<PersistentMap<Long, Boolean>>(persistentMapOf())
    private val asking = mutableSetOf<Long>()

    private val pending = MutableStateFlow<Pending?>(null)

    /** The storage permission that saving needs on Android 8–9, asked for when Save is tapped. */
    val permission = StoragePermissionRequest(savedStateHandle, effects, onGranted = ::saveAfterPermission)

    /** What the toolbar offers for the item of message [currentId] (null while there is none). */
    fun state(currentId: Flow<Long?>): Flow<MediaActions> =
        combine(
            isProtected,
            savable,
            pending,
            permission.showsRationale,
            currentId,
        ) { isProtected, savable, pending, rationale, id ->
            MediaActions(
                canSave = id != null && !isProtected && savable[id] == true,
                pending = pending?.takeIf { it.messageId == id }?.action,
                showsStorageRationale = rationale,
            )
        }.distinctUntilChanged()

    /**
     * The current item is [target] now, its files in the states [files]: TDLib is asked whether it can be saved, and
     * what waits for its file runs once the file is there. A wait for another item is dropped.
     */
    fun focus(
        target: Target?,
        files: Map<Int, FileState>,
    ) {
        val previous = current
        if (previous != null && previous.messageId != target?.messageId) {
            pending.update { it?.takeIf { waiting -> waiting.isRunning } }
            permission.dismissRationale()
        }
        current = target
        this.files = files
        if (target == null) return
        ask(target.messageId)
        runPending()
    }

    /**
     * Shares, saves or copies the current item, once its file is there; a Save asks for the storage permission first
     * where that is needed. Nothing while another action waits, where the item cannot be saved, or for a Copy of
     * anything but a photo.
     */
    fun start(action: MediaAction) {
        val target =
            current?.takeIf { canSave(it.messageId) && (action != MediaAction.Copy || it.media is ViewerMedia.Image) }
        if (target == null || pending.value != null) return
        if (action == MediaAction.Save && sharedStorage.needsStoragePermission()) {
            permission.ask(target.messageId)
        } else {
            pending.value = Pending(target.messageId, action)
            runPending()
        }
    }

    /** The current item's download failed (the viewer shows the error): nothing waits for it any more. */
    fun onDownloadFailed() {
        pending.update { it?.takeIf { waiting -> waiting.isRunning } }
    }

    /**
     * The storage permission was granted for message [messageId]'s Save. After process death the item may not be
     * loaded yet: the wait starts anyway, and runs once it is.
     */
    private fun saveAfterPermission(messageId: Long) {
        if (pending.value == null) pending.value = Pending(messageId, MediaAction.Save)
        runPending()
    }

    private fun canSave(messageId: Long): Boolean = !isProtected.value && savable.value[messageId] == true

    private fun ask(messageId: Long) {
        if (messageId in savable.value || !asking.add(messageId)) return
        scope.launch {
            canBeSaved(messageId).onSuccess { canBeSaved -> savable.update { it.putting(messageId, canBeSaved) } }
            asking.remove(messageId)
            // A Save granted its permission after process death waited for this answer.
            runPending()
        }
    }

    /** Runs what waits for the current item's file once the file is there; ends the wait if it cannot come. */
    private fun runPending() {
        val target = current?.takeIf { canSave(it.messageId) }
        val waiting = pending.value?.takeIf { it.messageId == target?.messageId && !it.isRunning }
        if (target == null || waiting == null) return
        when (val file = files[target.media.fileId]) {
            is FileState.Downloaded -> {
                run(waiting, target, file.path)
            }

            is FileState.NotDownloaded -> {
                if (!file.canBeDownloaded) {
                    pending.value = null
                    effects.trySend(waiting.action.failure)
                }
            }

            // The viewer downloads the current item's file; a file TDLib has not reported yet comes with it.
            is FileState.Downloading, null -> {}
        }
    }

    private fun run(
        waiting: Pending,
        target: Target,
        path: String,
    ) {
        val media = target.media
        when (waiting.action) {
            MediaAction.Share -> {
                pending.value = null
                effects.trySend(MediaViewerEffect.ShareFile(path, media.fallbackMimeType))
            }

            MediaAction.Copy -> {
                pending.value = null
                effects.trySend(MediaViewerEffect.CopyImage(path, media.fallbackMimeType))
            }

            MediaAction.Save -> {
                pending.value = waiting.copy(isRunning = true)
                scope.launch {
                    val name = "$GALLERY_NAME_PREFIX${target.messageId}"
                    val saved = sharedStorage.saveToGallery(SharedFile(path, media.fallbackMimeType, name))
                    pending.value = null
                    effects.trySend(if (saved) MediaViewerEffect.SavedToGallery else MediaViewerEffect.SaveFailed)
                }
            }
        }
    }

    /** The current item: message [messageId] and its [media]. */
    class Target(
        val messageId: Long,
        val media: ViewerMedia,
    )

    /** [action] of message [messageId], waiting for its file, or running once [isRunning] (a copy into the gallery). */
    private data class Pending(
        val messageId: Long,
        val action: MediaAction,
        val isRunning: Boolean = false,
    )

    private companion object {
        /** Saved files are named "Pingogram_<message id>.<extension>": nothing about the chat or the sender. */
        const val GALLERY_NAME_PREFIX = "Pingogram_"
    }
}

/**
 * The storage permission that saving into the gallery needs on Android 8–9, asked for as Android's permission guidance
 * says: the screen checks whether the system wants the reason shown first ([MediaViewerEffect.CheckStoragePermission],
 * answered by [onChecked]), which then shows ([showsRationale]) until it is answered ([onRationaleAnswered]), and the
 * system is asked ([MediaViewerEffect.RequestStoragePermission], answered by [onResult]). The message whose Save waits
 * is kept in [savedStateHandle], so a grant still saves it after process death ([onGranted]); a refusal is a snackbar
 * ([MediaViewerEffect.StoragePermissionDenied]).
 */
internal class StoragePermissionRequest(
    private val savedStateHandle: SavedStateHandle,
    private val effects: SendChannel<MediaViewerEffect>,
    private val onGranted: (messageId: Long) -> Unit,
) {
    private val rationale = MutableStateFlow(false)

    /** Why saving needs the permission shows, until it is answered or the page is turned. */
    val showsRationale: StateFlow<Boolean> = rationale.asStateFlow()

    /** Message [messageId]'s Save needs the permission: the screen checks whether to explain it first. */
    fun ask(messageId: Long) {
        savedStateHandle[KEY_SAVE_AFTER_PERMISSION] = messageId
        effects.trySend(MediaViewerEffect.CheckStoragePermission)
    }

    /** The screen checked whether the system wants the reason shown first ([showRationale]). */
    fun onChecked(showRationale: Boolean) {
        if (savedStateHandle.get<Long>(KEY_SAVE_AFTER_PERMISSION) == null) return
        if (showRationale) {
            rationale.value = true
        } else {
            effects.trySend(MediaViewerEffect.RequestStoragePermission)
        }
    }

    /** The reason was answered: go on and ask for the permission ([proceed]), or not save after all. */
    fun onRationaleAnswered(proceed: Boolean) {
        rationale.value = false
        if (proceed) {
            effects.trySend(MediaViewerEffect.RequestStoragePermission)
        } else {
            savedStateHandle.remove<Long>(KEY_SAVE_AFTER_PERMISSION)
        }
    }

    /** The system's answer: [granted] saves the item that waited for it, a refusal says that saving needs it. */
    fun onResult(granted: Boolean) {
        val messageId = savedStateHandle.remove<Long>(KEY_SAVE_AFTER_PERMISSION) ?: return
        if (granted) onGranted(messageId) else effects.trySend(MediaViewerEffect.StoragePermissionDenied)
    }

    /** The page was turned: the reason no longer shows. */
    fun dismissRationale() {
        rationale.value = false
    }

    internal companion object {
        /** The message whose Save waits for the storage permission, which survives process death. */
        const val KEY_SAVE_AFTER_PERMISSION = "media.saveAfterPermission"
    }
}

/** What a failed [MediaAction] says. */
private val MediaAction.failure: MediaViewerEffect
    get() =
        when (this) {
            MediaAction.Share -> MediaViewerEffect.ShareFailed
            MediaAction.Save -> MediaViewerEffect.SaveFailed
            MediaAction.Copy -> MediaViewerEffect.CopyFailed
        }

/**
 * The type to share or save the media's file as when its extension names none: TDLib's photos are JPEG, videos MP4,
 * and a GIF has its own type.
 */
internal val ViewerMedia.fallbackMimeType: String
    get() =
        when (this) {
            is ViewerMedia.Image -> "image/jpeg"
            is ViewerMedia.Clip -> VIDEO_MIME_TYPE
            is ViewerMedia.Gif -> animation.mimeType.ifBlank { VIDEO_MIME_TYPE }
        }

private const val VIDEO_MIME_TYPE = "video/mp4"
