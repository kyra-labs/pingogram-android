package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.theme.spacing

@Preview(name = "Albums on a profile")
@Composable
private fun StoryAlbumsPreview() =
    PingogramPreview {
        Surface {
            StoryAlbumsContent(StoryAlbumPreviewData.albums, {}, Modifier.padding(MaterialTheme.spacing.medium))
        }
    }

@Preview(name = "Album")
@Composable
private fun StoryAlbumPreview() = AlbumPreview(StoryAlbumPreviewData.album)

@Preview(name = "Album, loading more")
@Composable
private fun StoryAlbumLoadingMorePreview() = AlbumPreview(StoryAlbumPreviewData.loadingMore)

@Preview(name = "Album, next page failed")
@Composable
private fun StoryAlbumMoreFailedPreview() = AlbumPreview(StoryAlbumPreviewData.moreFailed)

@Preview(name = "Album, empty")
@Composable
private fun StoryAlbumEmptyPreview() = AlbumPreview(StoryAlbumPreviewData.empty)

@Preview(name = "Album, loading")
@Composable
private fun StoryAlbumLoadingPreview() = AlbumPreview(StoryAlbumUiState.Loading)

@Preview(name = "Album, error")
@Composable
private fun StoryAlbumErrorPreview() = AlbumPreview(StoryAlbumPreviewData.error)

@Composable
private fun AlbumPreview(state: StoryAlbumUiState) =
    PingogramPreview { StoryAlbumScreen(state, onEvent = {}, onBack = {}, onOpenStory = {}) }
