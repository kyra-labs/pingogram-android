package dev.kyralabs.pingogram.core.ui.file

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.contactsync.openAppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Taking a photo with the camera app (`ACTION_IMAGE_CAPTURE`) into a camera file (`REQUIREMENTS.md` §6.2): the
 * composer's camera button, the attach menu's "Camera" and New story's "Camera".
 *
 * Pingogram declares `CAMERA` for its scanner, calls and round video messages, and Android refuses the camera app's
 * intent to an app that declares the permission without holding it. So [take] launches at once where it is granted,
 * and otherwise asks for it first, the tap having said why, then launches once it is granted. The file waits through
 * the permission's dialog in the saved state, also across process death. A refusal, a camera app missing and a launch
 * Android refuses all end in a snackbar and report no photo, never a crash.
 */
@Stable
class PhotoCapture internal constructor(
    private val pending: MutableState<String?>,
) {
    internal lateinit var host: PhotoCaptureHost
    internal lateinit var camera: ActivityResultLauncher<Uri>
    internal lateinit var permission: ActivityResultLauncher<String>

    /** Opens the camera app to take a photo into the camera file at [path]; what came of it goes to `onTaken`. */
    fun take(path: String) {
        if (host.hasPermission()) {
            launch(path)
            return
        }
        pending.value = path
        try {
            permission.launch(Manifest.permission.CAMERA)
        } catch (_: ActivityNotFoundException) {
            pending.value = null
            unavailable(PhotoCaptureProblem.NoCameraApp)
        }
    }

    internal fun onPermissionResult(granted: Boolean) {
        val path = pending.value ?: return
        pending.value = null
        if (granted) launch(path) else unavailable(PhotoCaptureProblem.PermissionNeeded)
    }

    internal fun onPhotoResult(taken: Boolean) = host.onTaken(taken)

    private fun launch(path: String) {
        val uri = host.cameraUri(path) ?: return unavailable(PhotoCaptureProblem.NoCameraApp)
        try {
            camera.launch(uri)
        } catch (_: ActivityNotFoundException) {
            unavailable(PhotoCaptureProblem.NoCameraApp)
        } catch (_: SecurityException) {
            // The permission was taken back meanwhile, or the system refuses the intent for another reason.
            unavailable(PhotoCaptureProblem.PermissionNeeded)
        }
    }

    private fun unavailable(problem: PhotoCaptureProblem) {
        host.onTaken(false)
        host.show(problem)
    }
}

/** Why no camera app opened. */
internal enum class PhotoCaptureProblem { NoCameraApp, PermissionNeeded }

/**
 * Where and what the snackbars say: on [snackbarHostState] in [scope], no camera app, the permission refused, and the
 * action that opens the app's settings.
 */
internal class PhotoCaptureNotices(
    val scope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    val noCameraApp: String,
    val permissionNeeded: String,
    val settings: String,
)

/** What a [PhotoCapture] works with, the composition's current values. */
internal class PhotoCaptureHost(
    private val context: Context,
    private val activity: Activity?,
    private val notices: PhotoCaptureNotices,
    val cameraUri: (String) -> Uri?,
    val onTaken: (Boolean) -> Unit,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** Says why in a snackbar; once Android no longer asks for the permission, with "Settings". */
    fun show(problem: PhotoCaptureProblem): Unit =
        with(notices) {
            when (problem) {
                PhotoCaptureProblem.NoCameraApp -> {
                    scope.launch { snackbarHostState.showSnackbar(noCameraApp) }
                }

                PhotoCaptureProblem.PermissionNeeded -> {
                    val canAskAgain =
                        activity != null &&
                            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
                    scope.launch {
                        if (canAskAgain) {
                            snackbarHostState.showSnackbar(permissionNeeded)
                        } else {
                            val result =
                                snackbarHostState.showSnackbar(
                                    message = permissionNeeded,
                                    actionLabel = settings,
                                    duration = SnackbarDuration.Long,
                                )
                            if (result == SnackbarResult.ActionPerformed) context.openAppSettings()
                        }
                    }
                }
            }
        }
}

/**
 * The screen's [PhotoCapture]: whether a photo was taken goes to [onTaken] (false also when no camera app opened), and
 * why none opened to [snackbarHostState], [noCameraAppMessage] when the device has no camera app. [cameraUri] hands
 * the camera app the camera file's content URI ([cameraFileUri]).
 */
@Composable
fun rememberPhotoCapture(
    snackbarHostState: SnackbarHostState,
    noCameraAppMessage: String,
    onTaken: (taken: Boolean) -> Unit,
    cameraUri: ((String) -> Uri?)? = null,
): PhotoCapture {
    val context = LocalContext.current
    val resources = LocalResources.current
    val pending = rememberSaveable { mutableStateOf<String?>(null) }
    val capture = remember(pending) { PhotoCapture(pending) }
    capture.host =
        PhotoCaptureHost(
            context = context,
            activity = LocalActivity.current,
            notices =
                PhotoCaptureNotices(
                    scope = rememberCoroutineScope(),
                    snackbarHostState = snackbarHostState,
                    noCameraApp = noCameraAppMessage,
                    permissionNeeded = resources.getString(R.string.photo_capture_permission_needed),
                    settings = resources.getString(R.string.photo_capture_settings),
                ),
            cameraUri = cameraUri ?: context::cameraFileUri,
            onTaken = onTaken,
        )
    capture.camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), capture::onPhotoResult)
    capture.permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), capture::onPermissionResult)
    return capture
}
