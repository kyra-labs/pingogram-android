package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import dev.kyralabs.pingogram.feature.media.ui.zoom.ZoomState
import kotlinx.coroutines.launch
import kotlin.math.pow

// The media viewer with a hardware keyboard and a mouse (`docs/DESIGN_SYSTEM.md` §9 "Keyboard & mouse",
// `docs/audit/accessibility.md` A3): the keys wherever the focus is in the viewer, the pages' keys and the wheel.

/**
 * The viewer's keys, wherever its focus is, after the focused control had its turn: Esc closes it ([onClose]); +
 * (also = and the number pad's +) zooms the photo shown in, − out and 0 back to fitted, on the motion scheme's default
 * spatial spring, where it zooms ([zoomable]).
 */
@Composable
internal fun Modifier.viewerKeys(
    zoom: ZoomState,
    zoomable: Boolean,
    onClose: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val currentZoomable by rememberUpdatedState(zoomable)
    val currentOnClose by rememberUpdatedState(onClose)
    val handler =
        remember(zoom, spec, scope) {
            { event: KeyEvent ->
                val zoomKey = event.zoomKey()
                when {
                    event.key == Key.Escape -> {
                        if (event.type == KeyEventType.KeyDown) currentOnClose()
                        true
                    }

                    zoomKey != null && currentZoomable -> {
                        if (event.type == KeyEventType.KeyDown) {
                            when (zoomKey) {
                                ZoomKey.In -> zoom.step(ZOOM_STEP, spec, scope)
                                ZoomKey.Out -> zoom.step(1 / ZOOM_STEP, spec, scope)
                                ZoomKey.Fit -> zoom.fit(spec, scope)
                            }
                        }
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
        }
    return onKeyEvent(handler)
}

/** What a key asks of the zoom. */
private enum class ZoomKey { In, Out, Fit }

/** The zoom [this] key asks for, with or without Ctrl or Shift (+ is Shift and = on most layouts); null for none. */
private fun KeyEvent.zoomKey(): ZoomKey? {
    if (isAltPressed || isMetaPressed) return null
    return when (key) {
        Key.Plus, Key.Equals, Key.NumPadAdd -> ZoomKey.In
        Key.Minus, Key.NumPadSubtract -> ZoomKey.Out
        Key.Zero, Key.NumPad0 -> ZoomKey.Fit
        else -> null
    }
}

/**
 * The pages' keys and wheel for [content], which take the focus as the pages show, so that the keys work at once: Left
 * and Right turn to the page on that side (mirrored in right-to-left layouts, as the pager is) on the pager's own
 * animation, and Space plays or pauses the video shown ([MediaViewerEvent.PlayPause]). The wheel with Ctrl zooms the
 * photo shown around the pointer ([zoom], [wheelZoom]). Elsewhere (the toolbar's buttons) the arrows keep moving the
 * focus.
 */
@Composable
internal fun Modifier.pagesKeys(
    pagerState: PagerState,
    content: MediaViewerUiState.Content,
    zoom: ZoomState,
    onEvent: (MediaViewerEvent) -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val current = content.current
    val canPlay = current.media is ViewerMedia.Clip && content.playback?.messageId == current.messageId
    val currentCanPlay by rememberUpdatedState(canPlay)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(focus) { focus.requestFocus() }
    val handler =
        remember(pagerState, scope, rtl) {
            { event: KeyEvent ->
                val step = event.pageStep(rtl)
                when {
                    step != 0 -> {
                        val target = pagerState.settledPage + step
                        val inRange = target in 0 until pagerState.pageCount
                        if (inRange && event.type == KeyEventType.KeyDown) {
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                        inRange
                    }

                    event.key == Key.Spacebar && currentCanPlay -> {
                        if (event.type == KeyEventType.KeyDown) currentOnEvent(MediaViewerEvent.PlayPause)
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
        }
    return onKeyEvent(handler)
        .focusRequester(focus)
        .focusable()
        .wheelZoom(zoom, zoomable = current.zooms)
}

/** The page an arrow key turns to, from the one shown: 1 newer, −1 older, 0 for another key. */
private fun KeyEvent.pageStep(rtl: Boolean): Int =
    when (key) {
        Key.DirectionRight -> if (rtl) -1 else 1
        Key.DirectionLeft -> if (rtl) 1 else -1
        else -> 0
    }

/**
 * The wheel with Ctrl zooms the photo shown ([zoomable]) around the pointer at once ([ZoomState.transform]), in the
 * initial pass, before the pager could take the wheel as a scroll.
 */
@Composable
private fun Modifier.wheelZoom(
    zoom: ZoomState,
    zoomable: Boolean,
): Modifier {
    val currentZoomable by rememberUpdatedState(zoomable)
    return pointerInput(zoom) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull()
                val ctrlScroll = event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed
                if (ctrlScroll && change != null && currentZoomable) {
                    val scale = WHEEL_ZOOM.pow(-change.scrollDelta.y)
                    zoom.transform(change.position - size.toSize().center, scale, Offset.Zero)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
}

/** How much one notch of the wheel zooms, in or out. */
private const val WHEEL_ZOOM = 1.25f
