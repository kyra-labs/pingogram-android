package dev.kyralabs.pingogram.feature.settings.ui.stickers.set

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.ui.preview.StickerSetSheetPreviewStates

// Previews of the sticker set sheet's content in each of its states.

@PingogramPreviews
@Composable
private fun StickerSetContentPreview() {
    PingogramPreview {
        StickerSetContent(StickerSetSheetPreviewStates.notAdded, onEvent = {}, onShare = {}, onClose = {})
    }
}

@PingogramPreviews
@Composable
private fun StickerSetContentAddedPreview() {
    PingogramPreview {
        StickerSetContent(StickerSetSheetPreviewStates.added, onEvent = {}, onShare = {}, onClose = {})
    }
}

@PingogramPreviews
@Composable
private fun StickerSetContentAddingPreview() {
    PingogramPreview {
        StickerSetContent(StickerSetSheetPreviewStates.adding, onEvent = {}, onShare = {}, onClose = {})
    }
}

@PingogramPreviews
@Composable
private fun StickerSetContentNotFoundPreview() {
    PingogramPreview {
        StickerSetContent(StickerSetSheetPreviewStates.notFound, onEvent = {}, onShare = {}, onClose = {})
    }
}

@PingogramPreviews
@Composable
private fun StickerSetContentLoadingPreview() {
    PingogramPreview { StickerSetContent(StickerSetSheetUiState.Loading, onEvent = {}, onShare = {}, onClose = {}) }
}
