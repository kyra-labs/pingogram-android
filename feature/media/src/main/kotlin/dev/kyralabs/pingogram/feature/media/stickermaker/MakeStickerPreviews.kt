package dev.kyralabs.pingogram.feature.media.stickermaker

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview

@Preview(name = "Make a sticker", heightDp = 891)
@Composable
private fun MakeStickerPreview() = EditorPreview(MakeStickerPreviewData.editing)

@Preview(name = "Make a sticker, photo downloading", heightDp = 891)
@Composable
private fun MakeStickerLoadingPreview() = EditorPreview(MakeStickerPreviewData.loading)

@Preview(name = "Make a sticker, photo failed", heightDp = 891)
@Composable
private fun MakeStickerFailedPreview() = EditorPreview(MakeStickerPreviewData.failed)

@Preview(name = "Add to sticker set")
@Composable
private fun AddToSetPreview() = SheetPreview(MakeStickerPreviewData.choose)

@Preview(name = "Add to sticker set, loading the sets")
@Composable
private fun AddToSetLoadingPreview() = SheetPreview(MakeStickerPreviewData.chooseLoading)

@Preview(name = "Add to sticker set, the sets failed")
@Composable
private fun AddToSetFailedPreview() = SheetPreview(MakeStickerPreviewData.chooseFailed)

@Preview(name = "New sticker set")
@Composable
private fun NewSetPreview() = SheetPreview(MakeStickerPreviewData.newSet)

@Preview(name = "Added to the set")
@Composable
private fun DonePreview() = SheetPreview(MakeStickerPreviewData.done)

@Composable
private fun EditorPreview(state: MakeStickerUiState) =
    PingogramPreview {
        MakeStickerScreen(state = state, photo = previewStickerPhoto(), capture = null, onEvent = {}, onClose = {})
    }

@Composable
private fun SheetPreview(sheet: AddToSetState) =
    PingogramPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { AddToSetContent(sheet, onEvent = {}) }
    }
