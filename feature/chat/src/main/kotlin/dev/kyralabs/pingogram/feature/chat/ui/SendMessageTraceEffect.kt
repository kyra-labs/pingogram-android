package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import dev.kyralabs.pingogram.core.ui.trace.SendMessageTrace

/**
 * Ends [SendMessageTrace] once the conversation's newest item ([content], newest first) is an outgoing bubble still on
 * its way to Telegram: at the start of the frame after the one that composed it, so that frame has been drawn.
 */
@Composable
internal fun SendMessageTraceEffect(content: ChatContent) {
    val newest = (content as? ChatContent.Content)?.items?.firstOrNull() as? ChatItem.Bubble
    val sending = newest?.takeIf { it.isOutgoing && it.status == OutgoingStatus.Pending }?.id ?: return
    LaunchedEffect(sending) {
        withFrameNanos { }
        SendMessageTrace.end()
    }
}
