package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.rememberItemMotion
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * The Stories tab's items for [state] in a grid of shared media cells, as a profile lays them out; for previews,
 * goldens and tests. Taps go to [actions].
 */
@Composable
internal fun ProfileStoriesGrid(
    state: ProfileStoriesUiState,
    actions: ProfileStoriesActions = ProfileStoriesActions({}, {}, {}),
) {
    val motion = rememberItemMotion()
    val margin = MaterialTheme.spacing.medium
    Surface {
        LazyVerticalGrid(columns = GridCells.Adaptive(MaterialTheme.sizes.sharedMediaCell)) {
            profileStories(state, actions, sizePx = 0, margin = margin, motion = motion)
        }
    }
}

@Preview(name = "Stories tab")
@Composable
private fun ProfileStoriesPreview() = PingogramPreview { ProfileStoriesGrid(StoryAlbumPreviewData.profileStories) }

@Preview(name = "Stories tab, next page failed")
@Composable
private fun ProfileStoriesMoreFailedPreview() =
    PingogramPreview { ProfileStoriesGrid(StoryAlbumPreviewData.profileStoriesMoreFailed) }

@Preview(name = "Stories tab, loading")
@Composable
private fun ProfileStoriesLoadingPreview() = PingogramPreview { ProfileStoriesGrid(ProfileStoriesUiState.Loading) }

@Preview(name = "Stories tab, empty")
@Composable
private fun ProfileStoriesEmptyPreview() = PingogramPreview { ProfileStoriesGrid(ProfileStoriesUiState.Empty) }

@Preview(name = "Stories tab, error")
@Composable
private fun ProfileStoriesErrorPreview() =
    PingogramPreview { ProfileStoriesGrid(ProfileStoriesUiState.Error(DomainError.Network)) }
