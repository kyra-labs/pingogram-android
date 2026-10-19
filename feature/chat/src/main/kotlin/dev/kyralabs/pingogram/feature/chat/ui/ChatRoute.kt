package dev.kyralabs.pingogram.feature.chat.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.file.ExternalFileResult
import dev.kyralabs.pingogram.core.ui.file.viewTdlibFile
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachLaunchers
import dev.kyralabs.pingogram.feature.chat.ui.attach.rememberAttachInputs
import dev.kyralabs.pingogram.feature.chat.ui.attach.rememberAttachLaunchers
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerFocusState
import dev.kyralabs.pingogram.feature.chat.ui.composer.rememberComposerFocusState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The conversation of chat [chatId], pushed above the Chats tab or in its detail pane, opened at [messageId] when
 * search found it there (the list then jumps to it instead of the unread divider): hoists its [ChatViewModel], and
 * tells it when the screen is started and stopped, which opens and closes the chat in TDLib (`docs/PERFORMANCE.md`
 * §3). The effect follows the screen's lifecycle rather than the view model's: the screen stops when another screen
 * covers it or the app goes to the background, and stops and disposes when it is left, or when the pane shows another
 * chat, whose view model replaces this one. A rotation stops and starts it again with the same view model, so the chat
 * ends up open; the composer's text lives in the view model too. [onBack] leaves it; null shows no back button. The
 * view model's [ChatEffect]s show snackbars, open a
 * downloaded document in another app or a location in a maps app, jump to a message ([MessageJumpState]), or open a
 * photo, video or GIF in the media viewer through [onOpenMedia], which the app navigates with (features do not know
 * each other). The attach menu's options open the system's pickers and the camera app ([AttachLaunchers]); a snackbar
 * says when no app opens.
 */
@Composable
internal fun ChatRoute(
    chatId: Long,
    onBack: (() -> Unit)?,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
    messageId: Long? = null,
    viewModel: ChatViewModel =
        hiltViewModel<ChatViewModel, ChatViewModel.Factory>(key = "chat-$chatId") { it.create(chatId, messageId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(ChatEvent.Started)
        onStopOrDispose { viewModel.onEvent(ChatEvent.Stopped) }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val jumpState = rememberMessageJumpState(opensAtMessage = messageId != null)
    val composerFocus = rememberComposerFocusState()
    val currentOnOpenMedia by rememberUpdatedState(onOpenMedia)
    val launchers = rememberAttachLaunchers(viewModel::onEvent)
    val attach =
        rememberAttachInputs(
            launchers = launchers,
            onEvent = viewModel::onEvent,
            caption = viewModel.attachCaption,
            pollEditor = viewModel.pollEditor,
            snackbarHostState = snackbarHostState,
        )
    ChatEffects(
        effects = viewModel.effects,
        snackbarHostState = snackbarHostState,
        jumpState = jumpState,
        onOpenMedia = { currentOnOpenMedia(chatId, it) },
        composerFocus = composerFocus,
        onLaunchCamera = { path ->
            launchers.takePhoto(path).also { launched ->
                if (!launched) viewModel.onEvent(ChatEvent.PhotoTaken(taken = false))
            }
        },
    )
    ChatScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        jumpState = jumpState,
        composerText = viewModel.composerText,
        composerFocus = composerFocus,
        attach = attach,
    )
}

/**
 * Handles [effects]: a snackbar for errors, for a file that cannot be played, for a replied message that is gone, for
 * a found message that is not in the loaded history, for a scheduled message's time ([scheduledTime]), for an action
 * that is not allowed and for what is not there yet, the newest replacing the one shown; a downloaded document opens
 * in another app through [openFile] (the app's `FileProvider`, [viewTdlibFile]) and a location in a maps app, or a
 * snackbar says that no app can; a copied text goes to the clipboard through [copyText], with a snackbar before
 * Android 13, which confirms copies itself; a jump to a message goes to [jumpState], a focus request to
 * [composerFocus]; media to open goes to [onOpenMedia]; the camera app opens through [onLaunchCamera], or a snackbar
 * says that no camera app can.
 */
@Composable
internal fun ChatEffects(
    effects: Flow<ChatEffect>,
    snackbarHostState: SnackbarHostState,
    jumpState: MessageJumpState,
    onOpenMedia: (messageId: Long) -> Unit,
    openFile: Context.(path: String, mimeType: String?, chooserTitle: String) -> ExternalFileResult =
        Context::viewTdlibFile,
    copyText: Context.(text: String) -> Unit = Context::copyPlainText,
    composerFocus: ComposerFocusState = rememberComposerFocusState(),
    onLaunchCamera: (path: String) -> Boolean = { false },
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(effects) {
        var snackbar: Job? = null
        effects.collect { effect ->
            val message =
                when (effect) {
                    is ChatEffect.ShowError -> {
                        errorMessage(resources, effect.error, showErrorDetails)
                    }

                    is ChatEffect.OpenFile -> {
                        context.openedFileNotice(effect, resources, openFile)
                    }

                    ChatEffect.PlaybackFailed, ChatEffect.MessageUnavailable, ChatEffect.MessageNotLoaded -> {
                        resources.getString(effect.noticeText())
                    }

                    is ChatEffect.ScrollToMessage -> {
                        jumpState.jumpTo(effect.messageId)
                        ""
                    }

                    is ChatEffect.OpenLocation -> {
                        if (context.openMap(effect)) "" else resources.getString(R.string.content_no_maps_app)
                    }

                    is ChatEffect.OpenMedia -> {
                        onOpenMedia(effect.messageId)
                        ""
                    }

                    is ChatEffect.MessageScheduled -> {
                        resources.getString(R.string.composer_scheduled, context.scheduledTime(effect.at))
                    }

                    is ChatEffect.CopyText -> {
                        context.copyText(effect.text)
                        copiedNotice(resources)
                    }

                    ChatEffect.ActionUnavailable -> {
                        resources.getString(R.string.message_action_unavailable)
                    }

                    ChatEffect.FocusComposer -> {
                        composerFocus.request()
                        ""
                    }

                    is ChatEffect.LaunchCamera -> {
                        val launched = onLaunchCamera(effect.path)
                        resources.getString(R.string.attach_no_camera_app).takeUnless { launched }.orEmpty()
                    }
                }
            if (message.isNotEmpty()) {
                // Cancelling showSnackbar dismisses its snackbar.
                snackbar?.cancel()
                snackbar = launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }
}

/**
 * Opens the downloaded document of [effect] in another app through [openFile], with the chooser's title; the snackbar
 * when no app takes it or the file is gone, else nothing.
 */
private fun Context.openedFileNotice(
    effect: ChatEffect.OpenFile,
    resources: Resources,
    openFile: Context.(path: String, mimeType: String?, chooserTitle: String) -> ExternalFileResult,
): String {
    val chooserTitle = resources.getString(R.string.content_open_with)
    return when (openFile(effect.path, effect.mimeType, chooserTitle)) {
        ExternalFileResult.Started -> ""
        ExternalFileResult.NoApp -> resources.getString(R.string.content_no_app_for_file)
        ExternalFileResult.Unavailable -> resources.getString(R.string.content_file_unavailable)
    }
}

/** The snackbar after a copy: none from Android 13 on, which confirms copies itself. */
private fun copiedNotice(resources: Resources): String =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) resources.getString(R.string.message_copied) else ""

/** The text of a snackbar that only tells something. */
private fun ChatEffect.noticeText(): Int =
    when (this) {
        ChatEffect.PlaybackFailed -> R.string.content_playback_failed
        ChatEffect.MessageUnavailable -> R.string.chat_message_unavailable
        else -> R.string.chat_message_not_loaded
    }

/**
 * When a message scheduled for [epochSeconds] goes out, in the device's time zone, language and 12- or 24-hour clock:
 * "Oct 19, 9:41 AM", with the year when it is not this year's.
 */
internal fun Context.scheduledTime(
    epochSeconds: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val locale = resources.configuration.locales[0]
    val time = Instant.ofEpochSecond(epochSeconds).atZone(zone)
    val date = if (time.year == ZonedDateTime.now(zone).year) "MMMd" else "yMMMd"
    val clock = if (DateFormat.is24HourFormat(this)) "Hm" else "hm"
    val pattern = DateFormat.getBestDateTimePattern(locale, date + clock)
    return DateTimeFormatter.ofPattern(pattern, locale).format(time)
}

/** Puts [text] on the clipboard as plain text, labelled for the system's clipboard preview. */
internal fun Context.copyPlainText(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.message_copy_label), text))
}

/** Opens [location] in a maps app with a `geo:` link, false if no app on the device handles one. */
private fun Context.openMap(location: ChatEffect.OpenLocation): Boolean =
    try {
        startActivity(Intent(Intent.ACTION_VIEW, geoUri(location).toUri()))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

/**
 * `geo:lat,long?q=lat,long(label)`, which maps apps show as a pin named [ChatEffect.OpenLocation.label]; coordinates
 * with a dot whatever the language.
 */
internal fun geoUri(location: ChatEffect.OpenLocation): String {
    val point = String.format(Locale.ROOT, "%.6f,%.6f", location.latitude, location.longitude)
    val label = location.label?.let { "(${uriEncoded(it)})" }.orEmpty()
    return "geo:$point?q=$point$label"
}

/** [text] percent-encoded for a URI. `URLEncoder` writes spaces as "+", which maps apps would show as such. */
private fun uriEncoded(text: String): String = URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")
