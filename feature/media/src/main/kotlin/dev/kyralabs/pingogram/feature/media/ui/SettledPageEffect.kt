package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import dev.kyralabs.pingogram.feature.media.ui.zoom.ZoomState

/** Tells the view model of each page the pager settles on, by its message id, and resets [zoom] for it. */
@Composable
internal fun SettledPageEffect(
    pagerState: PagerState,
    items: List<MediaItem>,
    zoom: ZoomState,
    onEvent: (MediaViewerEvent) -> Unit,
) {
    val currentItems by rememberUpdatedState(items)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(pagerState, zoom) {
        var settledId: Long? = null
        snapshotFlow { currentItems.getOrNull(pagerState.settledPage)?.messageId }.collect { messageId ->
            // By id: older media loading in front moves the settled index, not the page.
            if (messageId != null && messageId != settledId) {
                settledId = messageId
                zoom.reset()
                currentOnEvent(MediaViewerEvent.PageSettled(messageId))
            }
        }
    }
}
