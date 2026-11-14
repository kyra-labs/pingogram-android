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
import dev.kyralabs.pingogram.core.ui.file.PhotoCapture
import dev.kyralabs.pingogram.core.ui.file.cameraFileUri
import dev.kyralabs.pingogram.core.ui.file.rememberPhotoCapture
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ReplyEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ReplaceMedia
import dev.kyralabs.pingogram.feature.chat.ui.location.LocationInputs
import dev.kyralabs.pingogram.feature.chat.ui.location.ShareLocationEvent
import kotlinx.coroutines.launch

/**
 * The system's pickers and the camera app, which the attach menu opens (`REQUIREMENTS.md` FR-C5, §7). What they
 * return goes to the view model as events: picked content URIs, readable while their grant lasts, and whether a photo
 * was taken. Their results survive the activity being recreated, also after process death, as
 * `rememberLauncherForActivityResult` keeps them.
 *
 * - **Gallery**: the Photo Picker, photos and videos, at most [MAX_PICKED_MEDIA] (an album).
 * - **File**: the document picker, any type, as many as wanted.
 * - **Camera** ([photo]): the camera app, `ACTION_IMAGE_CAPTURE` into the content URI of the view model's camera file
 *   ([cameraFileUri]). Pingogram declares the camera permission (scanner, calls, round video messages), so Android
 *   opens the camera app only once it is granted: [PhotoCapture] asks for it first (`REQUIREMENTS.md` §6.2).
 * - The pickers need no permission.
 * - **Contact**: the contact picker, for a phone number ([PickContactPhone]).
 * - **Replacing media** ([replace], `REQUIREMENTS.md` FR-C6): one photo or video from the Photo Picker, or one file
 *   from the document picker, for the message being edited.
 */
@Stable
internal class AttachLaunchers(
    private val context: Context,
    private val gallery: ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>>,
    private val files: ManagedActivityResultLauncher<Array<String>, List<Uri>>,
    private val photo: PhotoCapture,
    private val contact: ManagedActivityResultLauncher<Unit, Uri?>,
    private val replacement: ReplacementLaunchers,
) {
    /** Whether the device has a camera; without one the menu offers none. */
    val hasCamera: Boolean get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    /**
     * Opens the gallery, the file picker or the contact picker for [option]; false when no app opens, and for the
     * camera, whose file the view model makes first, and the location, poll and checklist sheets, which are the
     * screen's.
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

            AttachOption.Camera, AttachOption.Location, AttachOption.Poll, AttachOption.Checklist -> {
                false
            }
        }

    /** Opens the picker of one [kind] of media that replaces the edited message's; false when no app opens. */
    fun replace(kind: ReplaceMedia): Boolean =
        when (kind) {
            ReplaceMedia.Media -> {
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                launching { replacement.media.launch(request) }
            }

            ReplaceMedia.Document -> {
                launching { replacement.file.launch(arrayOf(ANY_TYPE)) }
            }
        }

    /**
     * Opens the camera app to take a photo into the camera file at [path], asking for the camera permission first
     * where it is not granted; `ChatEvent.PhotoTaken` says what came of it, and a snackbar why no camera app opened.
     */
    fun takePhoto(path: String) = photo.take(path)

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

/**
 * The [AttachLaunchers] of the screen, whose results go to [onEvent]; why the camera app did not open goes to
 * [snackbarHostState]. [cameraUri] hands the camera app the camera file's content URI (tests give their own).
 */
@Composable
internal fun rememberAttachLaunchers(
    onEvent: (ChatEvent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    cameraUri: ((String) -> Uri?)? = null,
): AttachLaunchers {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val gallery =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_MEDIA)) { uris ->
            if (uris.isNotEmpty()) currentOnEvent(ChatEvent.AttachMedia(uris.map(Uri::toString)))
        }
    val files =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) currentOnEvent(ChatEvent.AttachFiles(uris.map(Uri::toString)))
        }
    val photo =
        rememberPhotoCapture(
            snackbarHostState = snackbarHostState,
            noCameraAppMessage = LocalResources.current.getString(R.string.attach_no_camera_app),
            onTaken = { taken -> currentOnEvent(ChatEvent.PhotoTaken(taken)) },
            cameraUri = cameraUri,
        )
    val contact =
        rememberLauncherForActivityResult(PickContactPhone()) { uri ->
            uri?.let { currentOnEvent(ChatEvent.AttachContact(it.toString())) }
        }
    val replacementMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { currentOnEvent(ReplyEvent.ReplacementPicked(it.toString())) }
        }
    val replacementFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { currentOnEvent(ReplyEvent.ReplacementPicked(it.toString())) }
        }
    val context = LocalContext.current
    return remember(context, gallery, files, photo, contact, replacementMedia, replacementFile) {
        val replacement = ReplacementLaunchers(replacementMedia, replacementFile)
        AttachLaunchers(context, gallery, files, photo, contact, replacement)
    }
}

/** The pickers of one photo or video ([media]) and of one file ([file]) that replace the edited message's media. */
internal class ReplacementLaunchers(
    val media: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    val file: ManagedActivityResultLauncher<Array<String>, Uri?>,
)

/**
 * What the conversation's attach menu works with, from its route: [onPick] opens the picker of the option chosen in
 * the menu, [hasCamera] false leaves the camera out of it, [caption] is the preview's caption and [pollEditor] the
 * poll form, both the view model's, and [location] the "Location" sheet's.
 */
@Stable
internal class AttachInputs(
    val onPick: (AttachOption) -> Unit = {},
    val hasCamera: Boolean = true,
    val caption: ComposerText = ComposerText(),
    val pollEditor: PollEditor = PollEditor(),
    val checklistEditor: ChecklistEditor = ChecklistEditor(),
    val location: LocationInputs = LocationInputs(),
)

/**
 * The route's [AttachInputs]: an option of the menu opens its picker through [launchers], and a snackbar on
 * [snackbarHostState] says when no app can; the camera and the poll form go to the view model through [onEvent], the
 * location to its sheet's ([location]).
 */
@Composable
internal fun rememberAttachInputs(
    launchers: AttachLaunchers,
    onEvent: (ChatEvent) -> Unit,
    caption: ComposerText,
    pollEditor: PollEditor,
    snackbarHostState: SnackbarHostState,
    location: LocationInputs = LocationInputs(),
    checklistEditor: ChecklistEditor = ChecklistEditor(),
): AttachInputs {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    return remember(launchers, caption, pollEditor, checklistEditor, snackbarHostState, resources, scope, location) {
        AttachInputs(
            onPick = { option ->
                when (option) {
                    AttachOption.Camera -> {
                        currentOnEvent(ChatEvent.TakePhoto)
                    }

                    AttachOption.Poll -> {
                        currentOnEvent(ChatEvent.OpenPoll)
                    }

                    AttachOption.Checklist -> {
                        currentOnEvent(ChatEvent.OpenChecklist)
                    }

                    AttachOption.Location -> {
                        location.onEvent(ShareLocationEvent.Open)
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
            checklistEditor = checklistEditor,
            location = location,
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
