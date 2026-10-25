package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import dev.kyralabs.pingogram.core.ui.trace.OpenChatTrace

/**
 * Ends [OpenChatTrace] for chat [chatId] once its conversation shows more than its loading state ([shown]): at the
 * start of the frame after the one that composed the messages, so that frame has been drawn (the section runs at most
 * a frame long).
 */
@Composable
internal fun OpenChatTraceEffect(
    chatId: Long,
    shown: Boolean,
) {
    if (!shown) return
    LaunchedEffect(chatId) {
        withFrameNanos { }
        OpenChatTrace.end(chatId)
    }
}
