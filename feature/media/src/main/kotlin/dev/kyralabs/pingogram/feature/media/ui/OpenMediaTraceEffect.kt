package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import dev.kyralabs.pingogram.core.ui.trace.MediaViewerTrace

/**
 * Ends [MediaViewerTrace]'s `openMedia` once the viewer shows its pages ([shown]): at the start of the frame after the
 * one that composed them, so that frame has been drawn (`docs/PERFORMANCE.md` §9, as `feature:chat`'s
 * `OpenChatTraceEffect`).
 */
@Composable
internal fun OpenMediaTraceEffect(shown: Boolean) {
    if (!shown) return
    LaunchedEffect(Unit) {
        withFrameNanos { }
        MediaViewerTrace.endOpen()
    }
}

/** The viewer's root, which `OpenMediaBenchmark` waits for (`MainActivity` shows test tags as resource ids). */
internal const val MEDIA_VIEWER_TEST_TAG = "media_viewer"
