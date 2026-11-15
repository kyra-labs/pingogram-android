package dev.kyralabs.pingogram.feature.chat.ui.drop

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.file.DraggedFile
import dev.kyralabs.pingogram.core.ui.file.rememberDragShadowStyle
import dev.kyralabs.pingogram.core.ui.file.startFileDrag
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.content.largestPhotoSize

/** What a dragged-out file is, for the drag's label. */
internal enum class DragOutKind { Photo, Video, Document }

/**
 * A downloaded photo, video or document a bubble lets be dragged into another app (the owner's board 35c): its TDLib
 * [path], served to the other app as a `content://` URI of the app's `FileProvider`, its [mimeType], and for a document
 * the [name] it was sent with.
 */
@Immutable
internal data class DragOutFile(
    val path: String,
    val mimeType: String,
    val kind: DragOutKind,
    val name: String? = null,
) {
    override fun toString() = "DragOutFile(kind=$kind, mimeType=$mimeType)"
}

/**
 * The files of message [messageId]'s bubble that can be dragged out now: its photo, video or document, or each of an
 * album's, whose file is on the device. None where the chat keeps its content in (owner decision 2026-11-15: never a
 * secret chat, as screenshots are blocked there; nor a chat with protected content), which is where it cannot be
 * forwarded either ([dev.kyralabs.pingogram.feature.chat.ui.MessageShortcuts.canForward]).
 */
internal fun ChatUiState.dragOutFiles(messageId: Long): List<DragOutFile> {
    if (!shortcuts.canForward) return emptyList()
    val items = (content as? ChatContent.Content)?.items.orEmpty()
    val bubble = items.firstOrNull { it is ChatItem.Bubble && it.id == messageId } as? ChatItem.Bubble
    val contents = bubble?.album?.items ?: listOfNotNull(bubble?.content)
    return contents.mapNotNull { content -> content.dragOutFile(fileStates) }
}

private fun MessageContent.dragOutFile(states: Map<Int, FileState>): DragOutFile? {
    val found = draggableFile()
    val path = found?.let { (fileId, _) -> (states[fileId] as? FileState.Downloaded)?.path }
    return path?.let { found.second.copy(path = it) }
}

/** The file of this content that can leave the chat, by TDLib id, its path still to fill in; null for none. */
private fun MessageContent.draggableFile(): Pair<Int, DragOutFile>? =
    when (this) {
        is MessageContent.Photo -> {
            largestPhotoSize(photo.sizes)?.takeUnless { isSecret }?.let { size ->
                size.file.id to DragOutFile("", PHOTO_TYPE, DragOutKind.Photo)
            }
        }

        is MessageContent.Video -> {
            (video.file.id to DragOutFile("", VIDEO_TYPE, DragOutKind.Video))
                .takeUnless { isSecret }
        }

        is MessageContent.Document -> {
            val type = document.mimeType.ifBlank { GENERIC_TYPE }
            document.file.id to DragOutFile("", type, DragOutKind.Document, document.fileName.ifBlank { null })
        }

        else -> {
            null
        }
    }

/**
 * Drags a bubble's files into another app (`docs/DESIGN_SYSTEM.md` §6 "Drag and drop", the owner's board 35c): a long
 * press lifts the message for its actions, and moving the finger on starts Android's drag between apps with the
 * files ([startFileDrag]), then closes the actions. [start] answers whether a drag started; a message without such
 * files keeps its actions open.
 */
@Stable
internal class MessageDragOut(
    private val files: (messageId: Long) -> List<DragOutFile>,
    private val drag: (List<DragOutFile>) -> Boolean,
    private val onStarted: () -> Unit,
) {
    /** Starts dragging message [messageId]'s files out; false where it has none to drag or Android refused. */
    fun start(messageId: Long): Boolean {
        val found = files(messageId)
        if (found.isEmpty() || !drag(found)) return false
        onStarted()
        return true
    }
}

/** The conversation's [MessageDragOut], reading [state]'s files when a drag starts, never in composition. */
@Composable
internal fun rememberMessageDragOut(
    state: ChatUiState,
    onEvent: (ChatEvent) -> Unit,
): MessageDragOut {
    val currentState by rememberUpdatedState(state)
    val currentOnEvent by rememberUpdatedState(onEvent)
    val view = LocalView.current
    val resources = LocalResources.current
    val shadow = rememberDragShadowStyle()
    return remember(view, resources, shadow) {
        MessageDragOut(
            files = { messageId -> currentState.dragOutFiles(messageId) },
            drag = { files ->
                val dragged = files.map { DraggedFile(it.path, it.mimeType) }
                view.startFileDrag(dragged, dragLabel(resources, files), shadow)
            },
            onStarted = { currentOnEvent(ChatEvent.DismissMessageActions) },
        )
    }
}

/** What the drag shows: a document's name, "Photo" or "Video", or how many items it carries. */
internal fun dragLabel(
    resources: Resources,
    files: List<DragOutFile>,
): String {
    val single = files.singleOrNull()
    return when {
        single == null -> resources.getQuantityString(R.plurals.drag_out_items, files.size, files.size)
        single.name != null -> single.name
        single.kind == DragOutKind.Photo -> resources.getString(R.string.drag_out_photo)
        single.kind == DragOutKind.Video -> resources.getString(R.string.drag_out_video)
        else -> resources.getString(R.string.drag_out_file)
    }
}

private const val PHOTO_TYPE = "image/jpeg"
private const val VIDEO_TYPE = "video/mp4"
private const val GENERIC_TYPE = "application/octet-stream"

/**
 * What a long press on [bubble] then a move does: drag its files out through [dragOut], for photos, videos, documents
 * and albums only; null for other bubbles, which then take no drag at all.
 */
@Composable
internal fun rememberBubbleDragOut(
    bubble: ChatItem.Bubble,
    dragOut: MessageDragOut?,
): (() -> Boolean)? {
    val draggable = bubble.album != null || bubble.content.isDraggable
    val id = bubble.id
    return remember(dragOut, id, draggable) { dragOut?.takeIf { draggable }?.let { out -> { out.start(id) } } }
}

private val MessageContent.isDraggable: Boolean
    get() = this is MessageContent.Photo || this is MessageContent.Video || this is MessageContent.Document
