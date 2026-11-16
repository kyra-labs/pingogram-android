package dev.kyralabs.pingogram.feature.media.ui

import android.content.res.Resources
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.ui.file.DraggedFile
import dev.kyralabs.pingogram.core.ui.file.rememberDragShadowStyle
import dev.kyralabs.pingogram.core.ui.file.startFileDrag
import dev.kyralabs.pingogram.feature.media.R

/**
 * Drags the shown photo, video or GIF into another app (`docs/DESIGN_SYSTEM.md` §6 "Drag and drop", the owner's board
 * 35c): a long press, then moving the finger past the touch slop ([detectDragOutAfterLongPress]), starts Android's
 * drag between apps with the item's downloaded file and a read grant for it alone ([startFileDrag]), with a
 * `LongPress` haptic. Only a file already on the device drags; nothing downloads for it. The screen offers it only
 * where Telegram lets the content leave the chat ([MediaActions.canSave]) and never in a secret chat (owner decision
 * 2026-11-15).
 */
@Stable
internal class MediaDragOut(
    private val label: (ViewerMedia) -> String,
    private val drag: (DraggedFile, label: String) -> Boolean,
    private val onStarted: () -> Unit,
) {
    /** Starts dragging [media]'s file out, its state in [files]; false where it is not on the device or refused. */
    fun start(
        media: ViewerMedia,
        files: Map<Int, FileState>,
    ): Boolean {
        val path = (files[media.fileId] as? FileState.Downloaded)?.path
        val started = path != null && drag(DraggedFile(path, media.fallbackMimeType), label(media))
        if (started) onStarted()
        return started
    }
}

/**
 * What a long press then a move does on [item]'s page: drag its file out, for the shown item only, where Telegram lets
 * it leave the chat ([MediaActions.canSave]); its file is read when a drag starts. Null for the other pages.
 */
internal fun MediaDragOut?.forPage(
    content: MediaViewerUiState.Content,
    item: MediaItem,
    isShown: Boolean,
): (() -> Boolean)? =
    this
        ?.takeIf { isShown && content.actions.canSave && !item.isHidden }
        ?.let { out -> { out.start(item.media, content.files) } }

/** The viewer's [MediaDragOut]: the drag from the screen's view, its shadow in the theme's style. */
@Composable
internal fun rememberMediaDragOut(): MediaDragOut {
    val view = LocalView.current
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val shadow = rememberDragShadowStyle()
    return remember(view, resources, haptics, shadow) {
        MediaDragOut(
            label = { media -> dragLabel(resources, media) },
            drag = { file, label -> view.startFileDrag(listOf(file), label, shadow) },
            onStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
        )
    }
}

/** What the drag shows: "Photo", "Video" or "GIF". */
internal fun dragLabel(
    resources: Resources,
    media: ViewerMedia,
): String =
    resources.getString(
        when (media) {
            is ViewerMedia.Image -> R.string.media_drag_photo
            is ViewerMedia.Clip -> R.string.media_drag_video
            is ViewerMedia.Gif -> R.string.media_drag_gif
        },
    )

/**
 * A long press held without moving, then a move past the touch slop: [onDragOut] is asked to start a drag, and where
 * it does, the gesture's events are consumed, so the page's taps, holds, pans and the viewer's swipes let go of it.
 * Nothing while [canStart] says no as the finger goes down (a zoomed photo pans instead), and nothing once the finger
 * lifts, a second one comes, it moves before the long press or a parent takes the gesture (the pager, a swipe to
 * close).
 */
internal suspend fun PointerInputScope.detectDragOutAfterLongPress(
    canStart: () -> Boolean,
    onDragOut: () -> Boolean,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (canStart()) {
        val slop = viewConfiguration.touchSlop
        val endedEarly =
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitEnd(down.id, down.position, slop) }
        if (endedEarly == null) awaitDragOut(down.id, down.position, slop, onDragOut)
    }
}

/** Waits until the press ends before a long press: lifted, a second finger, moved past [slop], or used by a parent. */
private suspend fun AwaitPointerEventScope.awaitEnd(
    id: PointerId,
    start: Offset,
    slop: Float,
) {
    var ended = false
    while (!ended) {
        ended = awaitPointerEvent().endsPress(id, start, slop) ||
            // A parent that scrolls or swipes consumes on the main pass, after the page.
            awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }
    }
}

/** After the long press: the first move past [slop] asks [onDragOut]; a lift or a second finger ends it. */
private suspend fun AwaitPointerEventScope.awaitDragOut(
    id: PointerId,
    start: Offset,
    slop: Float,
    onDragOut: () -> Boolean,
) {
    var done = false
    while (!done) {
        // On the initial pass, so a drag that starts takes the move before the page's gestures see it.
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == id }
        val moved = change != null && (change.position - start).getDistance() > slop
        done = moved || event.endsPress(id, start, Float.POSITIVE_INFINITY)
        if (moved && onDragOut()) event.changes.forEach { it.consume() }
    }
}

/** Whether pointer [id] lifted, another finger is down, or [id] moved past [slop] from [start]. */
private fun PointerEvent.endsPress(
    id: PointerId,
    start: Offset,
    slop: Float,
): Boolean {
    val change = changes.firstOrNull { it.id == id }
    return change == null ||
        !change.pressed ||
        changes.count { it.pressed } > 1 ||
        (change.position - start).getDistance() > slop
}
