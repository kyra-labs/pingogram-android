package dev.kyralabs.pingogram.feature.chat.ui.attach

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import dev.kyralabs.pingogram.core.ui.file.cameraFileUri
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import kotlinx.coroutines.launch

/**
 * The system's pickers and the camera app, which the attach menu opens (`REQUIREMENTS.md` FR-C5, §7): none of them
 * needs a permission. What they return goes to the view model as events: picked content URIs, readable while their
 * grant lasts, and whether a photo was taken. Their results survive the activity being recreated, also after process
 * death, as `rememberLauncherForActivityResult` keeps them.
 *
 * - **Gallery**: the Photo Picker, photos and videos, at most [MAX_PICKED_MEDIA] (an album).
 * - **File**: the document picker, any type, as many as wanted.
 * - **Camera**: the camera app, `ACTION_IMAGE_CAPTURE` into the content URI of the view model's camera file
 *   ([cameraFileUri]). The app declares no camera permission, so the camera app needs none from it.
 * - **Contact**: the contact picker, for a phone number ([PickContactPhone]).
 */
@Stable
internal class AttachLaunchers(
    private val context: Context,
    private val gallery: ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>>,
    private val files: ManagedActivityResultLauncher<Array<String>, List<Uri>>,
    private val camera: ManagedActivityResultLauncher<Uri, Boolean>,
    private val contact: ManagedActivityResultLauncher<Unit, Uri?>,
) {
    /** Whether the device has a camera; without one the menu offers none. */
    val hasCamera: Boolean get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    /**
     * Opens the gallery, the file picker or the contact picker for [option]; false when no app opens, and for the
     * camera, whose file the view model makes first, and the poll form, which is the screen's.
     */
    fun pick(option: AttachOption): Boolean =
        when (option) {
            AttachOption.Gallery -> {
                launching {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }
            }

            AttachOption.File -> {
                launching { files.launch(arrayOf(ANY_TYPE)) }
            }

            AttachOption.Contact -> {
                launching { contact.launch(Unit) }
            }

            AttachOption.Camera, AttachOption.Poll -> {
                false
            }
        }

    /** Opens the camera app to take a photo into the camera file at [path]; false when no camera app opens. */
    fun takePhoto(path: String): Boolean {
        val uri = context.cameraFileUri(path) ?: return false
        return launching { camera.launch(uri) }
    }

    private inline fun launching(launch: () -> Unit): Boolean =
        try {
            launch()
            true
        } catch (_: ActivityNotFoundException) {
            false
        }

    private companion object {
        const val ANY_TYPE = "*/*"
    }
}

/** The [AttachLaunchers] of the screen, whose results go to [onEvent]. */
@Composable
internal fun rememberAttachLaunchers(onEvent: (ChatEvent) -> Unit): AttachLaunchers {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val gallery =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_MEDIA)) { uris ->
            if (uris.isNotEmpty()) currentOnEvent(ChatEvent.AttachMedia(uris.map(Uri::toString)))
        }
    val files =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) currentOnEvent(ChatEvent.AttachFiles(uris.map(Uri::toString)))
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            currentOnEvent(ChatEvent.PhotoTaken(taken))
        }
    val contact =
        rememberLauncherForActivityResult(PickContactPhone()) { uri ->
            uri?.let { currentOnEvent(ChatEvent.AttachContact(it.toString())) }
        }
    val context = LocalContext.current
    return remember(context, gallery, files, camera, contact) {
        AttachLaunchers(context, gallery, files, camera, contact)
    }
}

/**
 * What the conversation's attach menu works with, from its route: [onPick] opens the picker of the option chosen in
 * the menu, [hasCamera] false leaves the camera out of it, [caption] is the preview's caption and [pollEditor] the
 * poll form, both the view model's.
 */
@Stable
internal class AttachInputs(
    val onPick: (AttachOption) -> Unit = {},
    val hasCamera: Boolean = true,
    val caption: ComposerText = ComposerText(),
    val pollEditor: PollEditor = PollEditor(),
)

/**
 * The route's [AttachInputs]: an option of the menu opens its picker through [launchers], and a snackbar on
 * [snackbarHostState] says when no app can; the camera and the poll form go to the view model through [onEvent].
 */
@Composable
internal fun rememberAttachInputs(
    launchers: AttachLaunchers,
    onEvent: (ChatEvent) -> Unit,
    caption: ComposerText,
    pollEditor: PollEditor,
    snackbarHostState: SnackbarHostState,
): AttachInputs {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    return remember(launchers, caption, pollEditor, snackbarHostState, resources, scope) {
        AttachInputs(
            onPick = { option ->
                when (option) {
                    AttachOption.Camera -> {
                        currentOnEvent(ChatEvent.TakePhoto)
                    }

                    AttachOption.Poll -> {
                        currentOnEvent(ChatEvent.OpenPoll)
                    }

                    else -> {
                        if (!launchers.pick(option)) {
                            val notice = resources.getString(R.string.attach_no_picker_app)
                            scope.launch { snackbarHostState.showSnackbar(notice) }
                        }
                    }
                }
            },
            hasCamera = launchers.hasCamera,
            caption = caption,
            pollEditor = pollEditor,
        )
    }
}

/**
 * The system contact picker for a phone number (`ACTION_PICK` of `Phone.CONTENT_TYPE`), whose result is the URI of
 * the picked number with a read grant: its number and its contact's name can be read without the contacts
 * permission. `ActivityResultContracts.PickContact` picks a contact instead, whose phone numbers need that permission.
 */
internal class PickContactPhone : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(
        context: Context,
        input: Unit,
    ): Intent = Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Uri? = intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

/** The most photos and videos picked at once: one album (td_api.tl `sendMessageAlbum`). */
internal const val MAX_PICKED_MEDIA = 10
