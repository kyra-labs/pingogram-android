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
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.voice.openAppSettings
import dev.kyralabs.pingogram.feature.chat.ui.voice.rememberMicrophoneRequest
import dev.kyralabs.pingogram.feature.chat.ui.voice.voiceNoticeAction
import dev.kyralabs.pingogram.feature.chat.ui.voice.voiceNoticeText
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
 * search found it there (the view model then anchors the list there instead of the unread divider): hoists its
 * [ChatViewModel], and
 * tells it when the screen is started and stopped, which opens and closes the chat in TDLib (`docs/PERFORMANCE.md`
 * §3). The effect follows the screen's lifecycle rather than the view model's: the screen stops when another screen
 * covers it or the app goes to the background, and stops and disposes when it is left, or when the pane shows another
 * chat, whose view model replaces this one. A rotation stops and starts it again with the same view model, so the chat
 * ends up open; the composer's text lives in the view model too. [onBack] leaves it; null shows no back button. The
 * view model's [ChatEffect]s show snackbars, open a downloaded document in another app or a location in a maps app, or
 * open a photo, video or GIF in the media viewer through [onOpenMedia], which the app navigates with (features do not
 * know each other). The attach menu's options open the system's pickers and the camera app ([AttachLaunchers]); a
 * snackbar says when no app opens. The composer's microphone asks for its permission through the system's request
 * ([rememberMicrophoneRequest]), whose answer goes back to the view model. The forward sheet ([ForwardBottomSheet])
 * shows over the screen while the view model has one; the snackbar after forwarding to one other chat opens it through
 * [onOpenChat].
 */
@Composable
internal fun ChatRoute(
    chatId: Long,
    onBack: (() -> Unit)?,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenChat: (chatId: Long) -> Unit = {},
    messageId: Long? = null,
    viewModel: ChatViewModel =
        hiltViewModel<ChatViewModel, ChatViewModel.Factory>(key = "chat-$chatId") { it.create(chatId, messageId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recording by viewModel.voiceRecording.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(ChatEvent.Started)
        onStopOrDispose { viewModel.onEvent(ChatEvent.Stopped) }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val jumpState = rememberMessageJumpState()
    val composerFocus = rememberComposerFocusState()
    val currentOnOpenMedia by rememberUpdatedState(onOpenMedia)
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    val launchers = rememberAttachLaunchers(viewModel::onEvent)
    val requestMicrophone = rememberMicrophoneRequest(viewModel::onEvent)
    val context = LocalContext.current
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
        onOpenMedia = { currentOnOpenMedia(chatId, it) },
        onOpenChat = { currentOnOpenChat(it) },
        composerFocus = composerFocus,
        onLaunchCamera = { path ->
            launchers.takePhoto(path).also { launched ->
                if (!launched) viewModel.onEvent(ChatEvent.PhotoTaken(taken = false))
            }
        },
        onVoiceAction = voiceNoticeAction(requestMicrophone) { context.openAppSettings() },
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
        recording = recording,
    )
    val forwardSheet by viewModel.forwardSheet.collectAsStateWithLifecycle()
    forwardSheet?.let { ForwardBottomSheet(it, viewModel.forwardComment, viewModel::onEvent) }
}

/**
 * Handles [effects]: a snackbar for errors, for a file that cannot be played, for a replied or found message that is
 * gone, for a scheduled message's time ([scheduledTime]), for an action
 * that is not allowed and for what is not there yet, the newest replacing the one shown; a downloaded document opens
 * in another app through [openFile] (the app's `FileProvider`, [viewTdlibFile]) and a location in a maps app, or a
 * snackbar says that no app can; a copied text goes to the clipboard through [copyText], with a snackbar before
 * Android 13, which confirms copies itself; a focus request goes to [composerFocus]; media to open goes to
 * [onOpenMedia]; the camera app opens through [onLaunchCamera], or a snackbar
 * says that no camera app can. A sponsored message's link opens through [openUri] (the platform's handler), or a
 * snackbar says that no app can; what came of reporting one is a snackbar too. After forwarding a snackbar says to
 * how many chats, or to which one, with an action that opens it through [onOpenChat] if it is another chat; or for
 * how many it failed, and why. The voice recording's snackbars explain the microphone permission, offer the app's
 * settings once it is refused, and say to hold the button or that recording failed; their actions (asking for the
 * permission, opening the settings) go to [onVoiceAction].
 */
@Suppress("LongMethod") // One short branch per kind of effect, in one place so a new kind cannot be forgotten.
@Composable
internal fun ChatEffects(
    effects: Flow<ChatEffect>,
    snackbarHostState: SnackbarHostState,
    onOpenMedia: (messageId: Long) -> Unit,
    onOpenChat: (chatId: Long) -> Unit = {},
    openFile: Context.(path: String, mimeType: String?, chooserTitle: String) -> ExternalFileResult =
        Context::viewTdlibFile,
    copyText: Context.(text: String) -> Unit = Context::copyPlainText,
    composerFocus: ComposerFocusState = rememberComposerFocusState(),
    onLaunchCamera: (path: String) -> Boolean = { false },
    openUri: (uri: String) -> Boolean = rememberUriOpener(),
    onVoiceAction: (VoiceEffect) -> Unit = {},
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

                    ChatEffect.PlaybackFailed,
                    ChatEffect.MessageUnavailable,
                    ChatEffect.ActionUnavailable,
                    is ChatEffect.SponsoredNotice,
                    is VoiceEffect,
                    -> {
                        resources.getString(effect.noticeText())
                    }

                    is ChatEffect.OpenLocation -> {
                        context.openMap(effect, resources)
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

                    ChatEffect.FocusComposer -> {
                        composerFocus.request()
                        ""
                    }

                    is ChatEffect.LaunchCamera -> {
                        val launched = onLaunchCamera(effect.path)
                        resources.getString(R.string.attach_no_camera_app).takeUnless { launched }.orEmpty()
                    }

                    is ChatEffect.Forwarded, is ChatEffect.ForwardFailed -> {
                        forwardNotice(resources, effect, showErrorDetails)
                    }

                    is ChatEffect.OpenUrl -> {
                        if (openUri(effect.url)) "" else resources.getString(R.string.sponsored_no_app)
                    }
                }
            if (message.isNotEmpty()) {
                // Cancelling showSnackbar dismisses its snackbar.
                snackbar?.cancel()
                snackbar =
                    launch { snackbarHostState.showNotice(message, effect, resources, onOpenChat, onVoiceAction) }
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

/** The snackbar after reporting a sponsored message. */
private val SponsoredReportNotice.text: Int
    get() =
        when (this) {
            SponsoredReportNotice.Reported -> R.string.sponsored_reported
            SponsoredReportNotice.AdsHidden -> R.string.sponsored_ads_hidden
            SponsoredReportNotice.PremiumRequired -> R.string.sponsored_premium_required
            SponsoredReportNotice.ReportFailed -> R.string.sponsored_report_failed
        }

/** The text of a snackbar that only tells something. */
private fun ChatEffect.noticeText(): Int =
    when (this) {
        ChatEffect.PlaybackFailed -> R.string.content_playback_failed
        ChatEffect.ActionUnavailable -> R.string.message_action_unavailable
        is ChatEffect.SponsoredNotice -> notice.text
        is VoiceEffect -> voiceNoticeText(this)
        else -> R.string.chat_message_unavailable
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

/**
 * Opens [location] in a maps app with a `geo:` link; the snackbar's text if no app on the device handles one, else
 * nothing.
 */
private fun Context.openMap(
    location: ChatEffect.OpenLocation,
    resources: Resources,
): String =
    try {
        startActivity(Intent(Intent.ACTION_VIEW, geoUri(location).toUri()))
        ""
    } catch (_: ActivityNotFoundException) {
        resources.getString(R.string.content_no_maps_app)
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
