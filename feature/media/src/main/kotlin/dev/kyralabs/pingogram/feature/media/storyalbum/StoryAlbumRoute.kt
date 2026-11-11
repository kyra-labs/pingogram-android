package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** An opened story album (board 11b), with its view model; [onOpenStory] plays it from a story. */
@Composable
internal fun StoryAlbumRoute(
    onBack: () -> Unit,
    onOpenStory: (storyId: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoryAlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StoryAlbumScreen(state, viewModel::onEvent, onBack, onOpenStory, modifier)
}
