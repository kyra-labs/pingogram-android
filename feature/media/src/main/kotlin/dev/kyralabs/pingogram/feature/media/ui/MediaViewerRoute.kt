package dev.kyralabs.pingogram.feature.media.ui

import androidx.annotation.OptIn
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The media viewer of the route's message, pushed above everything: hoists its [MediaViewerViewModel] and tells it
 * when the screen is started and stopped, which plays and pauses. Its [MediaViewerEffect]s show snackbars; one for a
 * failed page offers to try again. [onBack] leaves it.
 *
 * Videos draw on Media3's [PlayerSurface] in a `TextureView`: unlike a `SurfaceView`, it scales, moves and fades with
 * the page (predictive back, swipe to close, and the container transform that opens the viewer), at a little more
 * power per frame. Choosing the surface type is Media3's [UnstableApi], hence the opt-in.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun MediaViewerRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(MediaViewerEvent.Started)
        onStopOrDispose { viewModel.onEvent(MediaViewerEvent.Stopped) }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    MediaViewerEffects(viewModel.effects, snackbarHostState, onRetry = { viewModel.onEvent(MediaViewerEvent.Retry) })
    MediaViewerScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
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

/**
 * Handles [effects]: a snackbar for each error, offering to try again ([onRetry]), and one for a file that cannot be
 * played, the newest replacing the one shown.
 */
@Composable
private fun MediaViewerEffects(
    effects: Flow<MediaViewerEffect>,
    snackbarHostState: SnackbarHostState,
    onRetry: () -> Unit,
) {
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val currentOnRetry by rememberUpdatedState(onRetry)
    LaunchedEffect(effects) {
        var snackbar: Job? = null
        effects.collect { effect ->
            // Cancelling showSnackbar dismisses its snackbar.
            snackbar?.cancel()
            snackbar =
                launch {
                    when (effect) {
                        is MediaViewerEffect.ShowError -> {
                            val result =
                                snackbarHostState.showSnackbar(
                                    message = errorMessage(resources, effect.error, showErrorDetails),
                                    actionLabel = resources.getString(R.string.media_retry),
                                )
                            if (result == SnackbarResult.ActionPerformed) currentOnRetry()
                        }

                        MediaViewerEffect.PlaybackFailed -> {
                            snackbarHostState.showSnackbar(resources.getString(R.string.media_playback_failed))
                        }
                    }
                }
        }
    }
}
