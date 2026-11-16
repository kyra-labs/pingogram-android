package dev.kyralabs.pingogram.feature.chat.ui.drop

import android.app.Activity
import android.content.ClipDescription
import android.view.DragEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import dev.kyralabs.pingogram.core.domain.model.SharedContent

/**
 * A drag of files from another app over the conversation (`docs/DESIGN_SYSTEM.md` §6 "Drag and drop", the owner's
 * board 35a): whether one is under way ([isActive]), whether it is over the messages ([isHovered]) and whether it
 * carries only photos and videos ([isMedia]), from the drag's MIME types (Android tells how many items only at the
 * drop).
 */
@Stable
internal class ChatDropState(
    isActive: Boolean = false,
    isHovered: Boolean = false,
    isMedia: Boolean = false,
) {
    var isActive by mutableStateOf(isActive)
        internal set
    var isHovered by mutableStateOf(isHovered)
        internal set
    var isMedia by mutableStateOf(isMedia)
        internal set
}

/** A [ChatDropState] for the conversation. */
@Composable
internal fun rememberChatDropState(): ChatDropState = remember { ChatDropState() }

/**
 * Makes the whole message area accept files dropped from another app (owner decision 2026-11-15), where the chat
 * takes attachments ([enabled]): it reads them with the drop's permission (`Activity.requestDragAndDropPermissions`)
 * and hands their `content://` URIs to [onDrop], with whether they all are photos and videos, for the attach preview;
 * nothing is sent until the user taps Send there. Text alone, and drags without files, are left to the composer.
 */
@Composable
internal fun Modifier.chatDropTarget(
    enabled: Boolean,
    state: ChatDropState,
    onDrop: (uris: List<String>, isMedia: Boolean) -> Unit,
): Modifier {
    val activity = LocalActivity.current
    val currentOnDrop by rememberUpdatedState(onDrop)
    val target =
        remember(state, activity) { ChatDropTarget(state, activity) { uris, media -> currentOnDrop(uris, media) } }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> enabled && event.mimeTypes().any(::isFileType) },
        target = target,
    )
}

private class ChatDropTarget(
    private val state: ChatDropState,
    private val activity: Activity?,
    private val onDrop: (List<String>, Boolean) -> Unit,
) : DragAndDropTarget {
    override fun onStarted(event: DragAndDropEvent) {
        state.isMedia = event.mimeTypes().filter(::isFileType).all(::isMediaType)
        state.isActive = true
    }

    override fun onEntered(event: DragAndDropEvent) {
        state.isHovered = true
    }

    override fun onExited(event: DragAndDropEvent) {
        state.isHovered = false
    }

    override fun onEnded(event: DragAndDropEvent) {
        state.isActive = false
        state.isHovered = false
    }

    override fun onDrop(event: DragAndDropEvent): Boolean {
        val drag: DragEvent = event.toAndroidDragEvent()
        // The other app's files are readable only with the drop's permission, for as long as the activity lives;
        // the attach preview copies them into the upload cache at once.
        activity?.requestDragAndDropPermissions(drag)
        val clip = drag.clipData
        val uris =
            (0 until (clip?.itemCount ?: 0))
                .mapNotNull { index -> clip?.getItemAt(index)?.uri?.takeIf { it.scheme == CONTENT_SCHEME } }
                .map { it.toString() }
                .take(SharedContent.MAX_FILES)
        val description = clip?.description
        val types = (0 until (description?.mimeTypeCount ?: 0)).mapNotNull { description?.getMimeType(it) }
        if (uris.isNotEmpty()) onDrop(uris, types.filter(::isFileType).all(::isMediaType))
        return uris.isNotEmpty()
    }
}

/** Whether a drag's item of [mimeType] is a file the attach preview takes, rather than text. */
internal fun isFileType(mimeType: String): Boolean =
    mimeType != ClipDescription.MIMETYPE_TEXT_PLAIN &&
        mimeType != ClipDescription.MIMETYPE_TEXT_HTML &&
        mimeType != ClipDescription.MIMETYPE_TEXT_INTENT

/** Whether an item of [mimeType] goes as a photo or video (an album) rather than a document. */
internal fun isMediaType(mimeType: String): Boolean = mimeType.startsWith("image/") || mimeType.startsWith("video/")

private const val CONTENT_SCHEME = "content"
