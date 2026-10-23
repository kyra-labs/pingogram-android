package dev.kyralabs.pingogram.feature.media.story

import androidx.annotation.OptIn
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.media.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The stories viewer of the route's chat, full screen above everything: hoists its [StoryViewerViewModel] and tells it
 * when the screen is started and stopped, which plays, pauses, and opens and closes the story for TDLib. Its
 * [StoryViewerEffect]s close the viewer ([onClose], after the last story) and show snackbars: a sent reply, a video
 * that cannot be played, an error. Videos draw on Media3's [PlayerSurface] in a `TextureView`, which scales and moves
 * with the story as it is swiped away, as the media viewer's do; choosing it is Media3's [UnstableApi].
 */
@OptIn(UnstableApi::class)
@Composable
internal fun StoryViewerRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoryViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(StoryViewerEvent.Started(true))
        onStopOrDispose { viewModel.onEvent(StoryViewerEvent.Started(false)) }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        var snackbar: Job? = null
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    StoryViewerEffect.Close -> {
                        currentOnClose()
                        return@collect
                    }

                    StoryViewerEffect.ReplySent -> {
                        resources.getString(R.string.story_reply_sent)
                    }

                    StoryViewerEffect.PlaybackFailed -> {
                        resources.getString(R.string.story_playback_failed)
                    }

                    is StoryViewerEffect.ShowError -> {
                        errorMessage(resources, effect.error, showErrorDetails)
                    }
                }
            // The newest replaces the one shown; cancelling showSnackbar dismisses it.
            snackbar?.cancel()
            snackbar = launch { snackbarHostState.showSnackbar(message) }
        }
    }
    StoryViewerScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onClose = onClose,
        replyText = viewModel.replyText,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        videoSurface = { surfaceModifier ->
            PlayerSurface(
                player = viewModel.surfacePlayer,
                modifier = surfaceModifier,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            )
        },
    )
}
