package dev.kyralabs.pingogram.feature.chat.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import dev.kyralabs.pingogram.core.ui.file.ExternalFileResult
import dev.kyralabs.pingogram.feature.chat.ui.bots.botButtonNotice

/**
 * The snackbars of forwarding, selecting, reporting and saving, for [ChatEffects]: their text ([noticeOf], with error
 * details if [showErrorDetails]) and what their action does ([onAction]): open the chat forwarded to, open the one
 * file saved to Downloads, go on to the storage permission ([activity]'s rationale shown first where the system wants
 * it), or open the app's settings after it was refused; the same for the microphone a voice message needs. The
 * system's requests for both come from [permissions].
 */
@Stable
internal class NoticeActions(
    private val context: Context,
    private val activity: Activity?,
    private val showErrorDetails: Boolean,
    private val permissions: NoticePermissions,
    private val onOpenChat: (chatId: Long) -> Unit,
    private val files: NoticeFiles,
) {
    private val resources: Resources get() = context.resources

    /** The text of the snackbar of [effect], a forward, a [SelectionEffect] or a [BotButtonEffect]; empty for none. */
    fun noticeOf(effect: ChatEffect): String =
        when (effect) {
            is SelectionEffect -> {
                context.selectionNotice(
                    effect = effect,
                    resources = resources,
                    copyText = files.copyText,
                    showsStorageRationale = activity.showsStorageRationale(),
                    requestStoragePermission = permissions.requestStorage,
                )
            }

            is BotButtonEffect -> {
                botButtonNotice(resources, effect)
            }

            else -> {
                forwardNotice(resources, effect, showErrorDetails)
            }
        }

    /** Does what the action of [effect]'s snackbar does; the text of the snackbar that follows, empty for none. */
    fun onAction(effect: ChatEffect): String =
        when (effect) {
            is ChatEffect.Forwarded -> {
                effect.openChatId?.let(onOpenChat)
                ""
            }

            is SelectionEffect.Saved -> {
                effect.open?.let { context.openedFileNotice(it, resources, files.openFile) }.orEmpty()
            }

            SelectionEffect.RequestStoragePermission -> {
                permissions.requestStorage()
                ""
            }

            SelectionEffect.StoragePermissionDenied, VoiceEffect.MicrophoneDenied -> {
                context.openAppSettings()
                ""
            }

            VoiceEffect.ExplainMicrophone -> {
                permissions.requestMicrophone()
                ""
            }

            else -> {
                ""
            }
        }
}

/**
 * The system's permission requests the snackbars' actions start: the storage permission that saving needs on Android
 * 8–9 ([requestStorage]), and the microphone that voice messages need ([requestMicrophone]).
 */
internal class NoticePermissions(
    val requestStorage: () -> Unit,
    val requestMicrophone: () -> Unit,
)

/** How the screen opens a downloaded file in another app ([openFile]) and puts a text on the clipboard ([copyText]). */
internal class NoticeFiles(
    val openFile: Context.(path: String, mimeType: String?, chooserTitle: String) -> ExternalFileResult,
    val copyText: Context.(text: String) -> Unit,
)

/**
 * The [NoticeActions] of the screen, with the request for the storage permission that saving needs on Android 8–9,
 * whose answer goes to [onEvent] ([SelectionEvent.StoragePermissionResult]), and the one for the microphone that voice
 * messages need ([VoiceEvent.PermissionAnswered]).
 */
@Composable
internal fun rememberNoticeActions(
    onOpenChat: (chatId: Long) -> Unit,
    files: NoticeFiles,
    showErrorDetails: Boolean,
    onEvent: (ChatEvent) -> Unit,
): NoticeActions {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            currentOnEvent(SelectionEvent.StoragePermissionResult(granted))
        }
    val microphone =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            currentOnEvent(VoiceEvent.PermissionAnswered(granted))
        }
    return NoticeActions(
        context = LocalContext.current,
        activity = LocalActivity.current,
        showErrorDetails = showErrorDetails,
        permissions =
            NoticePermissions(
                requestStorage = { permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) },
                requestMicrophone = { microphone.launch(Manifest.permission.RECORD_AUDIO) },
            ),
        onOpenChat = onOpenChat,
        files = files,
    )
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
