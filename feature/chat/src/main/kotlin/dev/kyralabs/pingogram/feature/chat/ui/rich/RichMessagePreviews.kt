package dev.kyralabs.pingogram.feature.chat.ui.rich

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.content.rememberSampleMinithumbnail
import dev.kyralabs.pingogram.feature.chat.ui.preview.RichPreviewData

// A channel's rich message with its sections closed, opened, and with only its start sent.
@PingogramPreviews
@Composable
private fun RichMessageCollapsedPreview() {
    PingogramPreview {
        ChatScreen(state = RichPreviewData.collapsed(rememberSampleMinithumbnail()), onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun RichMessageExpandedPreview() {
    PingogramPreview {
        ChatScreen(state = RichPreviewData.expanded(rememberSampleMinithumbnail()), onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun RichMessagePartialPreview() {
    PingogramPreview {
        ChatScreen(state = RichPreviewData.partial(rememberSampleMinithumbnail()), onEvent = {}, onBack = {})
    }
}
