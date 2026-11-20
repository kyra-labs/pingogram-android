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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable

/**
 * Picking stories for one of the user's albums (owner decision 2026-11-20), with its view model: [onDone] once the
 * album was created or changed, [onClose] to leave without a change; a refusal is a snackbar.
 */
@Composable
internal fun StoryAlbumPickerRoute(
    onDone: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoryAlbumPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                StoryAlbumPickerEffect.Done -> {
                    currentOnDone()
                }

                is StoryAlbumPickerEffect.ShowError -> {
                    snackbars.showSnackbar(
                        errorMessage(resources, effect.error, showDetails),
                    )
                }
            }
        }
    }
    StoryAlbumPickerScreen(state, viewModel::onEvent, onClose, modifier, snackbars)
}
