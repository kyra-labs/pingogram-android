package dev.kyralabs.pingogram.feature.media.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import dev.kyralabs.pingogram.core.ui.file.ExternalFileResult
import dev.kyralabs.pingogram.core.ui.file.shareTdlibFile
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.media.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The media viewer of the route's message, pushed above everything: hoists its [MediaViewerViewModel] and tells it
 * when the screen is started and stopped, which plays and pauses. Its [MediaViewerEffect]s show snackbars (one for a
 * failed page offers to try again), hand a file to the system's share sheet, and on Android 8–9 check and ask for the
 * storage permission that saving needs ([MediaViewerEffects]). [onBack] leaves it.
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
    MediaViewerEffects(viewModel.effects, snackbarHostState, onEvent = viewModel::onEvent)
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
 * Handles [effects], sending what comes of them to [onEvent]:
 *
 * - **Snackbars**, the newest replacing the one shown: errors, offering to try again ([MediaViewerEvent.Retry]); a
 *   file that cannot be played, shared or saved; a file saved into the gallery; a refused storage permission, offering
 *   the app's settings, where it can be granted once the system no longer asks.
 * - **Share**: the file goes to the system's share sheet with the activity ([shareTdlibFile]); a snackbar if it cannot.
 * - **Storage permission** (Android 8–9): whether the system wants its reason shown first
 *   ([ActivityCompat.shouldShowRequestPermissionRationale]), and the system's request, whose answer goes back.
 */
@Composable
private fun MediaViewerEffects(
    effects: Flow<MediaViewerEffect>,
    snackbarHostState: SnackbarHostState,
    onEvent: (MediaViewerEvent) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val currentOnEvent by rememberUpdatedState(onEvent)
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            currentOnEvent(MediaViewerEvent.StoragePermissionResult(granted))
        }
    LaunchedEffect(effects) {
        val snackbars = Snackbars(this, snackbarHostState)
        effects.collect { effect ->
            when (effect) {
                is MediaViewerEffect.ShowError -> {
                    snackbars.show(
                        message = errorMessage(resources, effect.error, showErrorDetails),
                        actionLabel = resources.getString(R.string.media_retry),
                        onAction = { currentOnEvent(MediaViewerEvent.Retry) },
                    )
                }

                MediaViewerEffect.PlaybackFailed -> {
                    snackbars.show(resources.getString(R.string.media_playback_failed))
                }

                is MediaViewerEffect.ShareFile -> {
                    context.share(effect, resources.getString(R.string.media_share))?.let { failure ->
                        snackbars.show(resources.getString(failure))
                    }
                }

                MediaViewerEffect.ShareFailed -> {
                    snackbars.show(resources.getString(R.string.media_share_failed))
                }

                MediaViewerEffect.SavedToGallery -> {
                    snackbars.show(resources.getString(R.string.media_saved))
                }

                MediaViewerEffect.SaveFailed -> {
                    snackbars.show(resources.getString(R.string.media_save_failed))
                }

                MediaViewerEffect.CheckStoragePermission -> {
                    currentOnEvent(MediaViewerEvent.StoragePermissionChecked(activity.showsStorageRationale()))
                }

                MediaViewerEffect.RequestStoragePermission -> {
                    permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }

                MediaViewerEffect.StoragePermissionDenied -> {
                    snackbars.show(
                        message = resources.getString(R.string.media_storage_denied),
                        actionLabel = resources.getString(R.string.media_storage_settings),
                        onAction = { context.openAppSettings() },
                    )
                }
            }
        }
    }
}

/** One snackbar at a time in [scope], the newest replacing the one shown; one with an action stays longer. */
private class Snackbars(
    private val scope: CoroutineScope,
    private val hostState: SnackbarHostState,
) {
    private var shown: Job? = null

    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: () -> Unit = {},
    ) {
        // Cancelling showSnackbar dismisses its snackbar.
        shown?.cancel()
        shown =
            scope.launch {
                val duration = if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long
                val result = hostState.showSnackbar(message, actionLabel, duration = duration)
                if (result == SnackbarResult.ActionPerformed) onAction()
            }
    }
}

/**
 * Hands the file of [effect] to the system's share sheet titled [chooserTitle]; what a snackbar says if that fails,
 * null if it did not.
 */
@StringRes
private fun Context.share(
    effect: MediaViewerEffect.ShareFile,
    chooserTitle: String,
): Int? =
    when (shareTdlibFile(effect.path, effect.mimeType, chooserTitle)) {
        ExternalFileResult.Started -> null
        ExternalFileResult.NoApp -> R.string.media_no_share_app
        ExternalFileResult.Unavailable -> R.string.media_share_failed
    }

/** Whether the system wants the reason for the storage permission shown before asking for it again. */
private fun Activity?.showsStorageRationale(): Boolean =
    this != null &&
        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

/** Opens the app's page in the system settings, where its permissions are granted. */
private fun Context.openAppSettings() {
    try {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    } catch (_: ActivityNotFoundException) {
        // A device without the page: there is nothing else to open.
    }
}
