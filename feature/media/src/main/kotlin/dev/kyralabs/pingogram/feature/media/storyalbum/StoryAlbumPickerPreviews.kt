package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Preview(name = "Picking stories")
@Composable
private fun StoryAlbumPickerPreview() = PickerPreview(StoryAlbumPreviewData.picking)

@Preview(name = "Picking stories, none to pick")
@Composable
private fun StoryAlbumPickerEmptyPreview() =
    PickerPreview(StoryAlbumPreviewData.picking.copy(stories = persistentListOf(), selected = persistentSetOf()))

@Preview(name = "Picking stories, loading")
@Composable
private fun StoryAlbumPickerLoadingPreview() = PickerPreview(StoryAlbumPickerUiState.Loading)

@Preview(name = "Picking stories, error")
@Composable
private fun StoryAlbumPickerErrorPreview() = PickerPreview(StoryAlbumPickerUiState.Error(DomainError.Network))

@Composable
private fun PickerPreview(state: StoryAlbumPickerUiState) =
    PingogramPreview { StoryAlbumPickerScreen(state, onEvent = {}, onClose = {}) }
