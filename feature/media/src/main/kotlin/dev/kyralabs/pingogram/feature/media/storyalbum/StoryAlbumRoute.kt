package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable

/**
 * An opened story album (board 11b), with its view model; [onOpenStory] plays it from a story. On the user's own
 * album, [onPickStories] opens picking stories to add or remove, after which the album is read again as it shows
 * again; a deleted album leaves through [onBack], and a refusal is a snackbar.
 */
@Composable
internal fun StoryAlbumRoute(
    onBack: () -> Unit,
    onOpenStory: (storyId: Int) -> Unit,
    modifier: Modifier = Modifier,
    onPickStories: (removing: Boolean) -> Unit = {},
    viewModel: StoryAlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                StoryAlbumEffect.Deleted -> {
                    currentOnBack()
                }

                is StoryAlbumEffect.ShowError -> {
                    snackbars.showSnackbar(
                        errorMessage(resources, effect.error, showDetails),
                    )
                }
            }
        }
    }
    // Not on the first start, which reads the album already.
    val starts = remember { intArrayOf(0) }
    LifecycleStartEffect(viewModel) {
        if (starts[0]++ > 0) viewModel.onEvent(StoryAlbumEvent.Refresh)
        onStopOrDispose {}
    }
    StoryAlbumScreen(
        state,
        viewModel::onEvent,
        onBack,
        onOpenStory,
        modifier,
        onPickStories = onPickStories,
        snackbars = snackbars,
    )
}
