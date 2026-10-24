package dev.kyralabs.pingogram.feature.settings.ui.reactions

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

@PingogramPreviews
@Composable
private fun QuickReactionScreenPreview() {
    PingogramPreview {
        QuickReactionScreen(QuickReactionPreviewStates.content, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun QuickReactionScreenCustomEmojiPreview() {
    PingogramPreview {
        QuickReactionScreen(QuickReactionPreviewStates.customEmoji, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun QuickReactionScreenLoadingPreview() {
    PingogramPreview {
        QuickReactionScreen(QuickReactionPreviewStates.loading, onEvent = {}, onBack = {})
    }
}
