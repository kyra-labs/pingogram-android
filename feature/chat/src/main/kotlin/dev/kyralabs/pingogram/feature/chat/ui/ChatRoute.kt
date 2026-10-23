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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.ui.component.SecureWindow
import dev.kyralabs.pingogram.core.ui.file.ExternalFileResult
import dev.kyralabs.pingogram.core.ui.file.viewTdlibFile
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachLaunchers
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.attach.rememberAttachInputs
import dev.kyralabs.pingogram.feature.chat.ui.attach.rememberAttachLaunchers
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerFocusState
import dev.kyralabs.pingogram.feature.chat.ui.composer.rememberComposerFocusState
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.location.ShareLocationViewModel
import dev.kyralabs.pingogram.feature.chat.ui.location.rememberLocationInputs
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelViewModel
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedMessagesSheet
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.translation.failureNotice
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
 * The conversation of chat [chatId] (of its forum topic or message thread [topic], if not null; [ChatRoute] decides
 * what a chat opens to), pushed above the Chats tab or in its detail pane, opened at [messageId] when search found it
 * there (the view model then anchors the list there instead of the unread divider): hoists its
 * [ChatViewModel], and
 * tells it when the screen is started and stopped, which opens and closes the chat in TDLib (`docs/PERFORMANCE.md`
 * §3). The effect follows the screen's lifecycle rather than the view model's: the screen stops when another screen
 * covers it or the app goes to the background, and stops and disposes when it is left, or when the pane shows another
 * chat, whose view model replaces this one. A rotation stops and starts it again with the same view model, so the chat
 * ends up open; the composer's text lives in the view model too. [onBack] leaves it; null shows no back button. The
 * view model's [ChatEffect]s show snackbars, open a downloaded document in another app or a location in a maps app, or
 * open a photo, video or GIF in the media viewer through [onOpenMedia], which the app navigates with (features do not
 * know each other). The attach menu's options open the system's pickers and the camera app ([AttachLaunchers]); a
 * snackbar says when no app opens; the "Location" sheet has its own view model ([ShareLocationViewModel], keyed by
 * the chat like this one), which finds the location the conversation then sends; so does the emoji and sticker panel
 * ([EmojiPanelViewModel]), whose stickers the conversation sends. The forward sheet
 * ([ForwardBottomSheet]) shows over the screen while the view model has one; the snackbar after forwarding to one other
 * chat opens it through [onOpenChat]. So does the send-as picker ([SendAsBottomSheet]), a secret chat's timer and
 * key ([SecretChatBottomSheet]), the bots' commands (`BotCommandsBottomSheet`), and the members whose messages the
 * search in the chat finds (`SearchSenderSheet`). While a secret chat is open, the
 * window is secure ([SecureWindow]): no screenshots.
 * Profiles (the title of a private or secret chat, a sender's avatar or name, a mention) open through
 * [onOpenProfile]; the title of a group or channel opens its info page through [onOpenChatInfo]; without them the
 * title opens nothing. A bubble's "12 comments" opens that thread's conversation through [onOpenThread], which the app
 * navigates with. With [focusComposer] (a chat opened from the list with a hardware keyboard) the composer's text field
 * takes the focus once it shows.
 */
@Suppress("LongParameterList") // The chat and where in it, what it opens elsewhere, and its view models.
@Composable
internal fun ConversationRoute(
    chatId: Long,
    onBack: (() -> Unit)?,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenChat: (chatId: Long) -> Unit = {},
    messageId: Long? = null,
    topic: MessageTopic? = null,
    onOpenThread: (chatId: Long, topic: MessageTopic) -> Unit = { _, _ -> },
    onOpenProfile: ((userId: Long) -> Unit)? = null,
    onOpenChatInfo: ((chatId: Long) -> Unit)? = null,
    focusComposer: Boolean = false,
    viewModel: ChatViewModel =
        hiltViewModel<ChatViewModel, ChatViewModel.Factory>(key = "chat-$chatId-$topic") {
            it.create(chatId, messageId, topic)
        },
    locationViewModel: ShareLocationViewModel = hiltViewModel(key = "location-$chatId"),
    panelViewModel: EmojiPanelViewModel = hiltViewModel(key = "emoji-panel-$chatId"),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatStartedEffect(viewModel)
    val snackbarHostState = remember { SnackbarHostState() }
    val jumpState = rememberMessageJumpState()
    val composerFocus = rememberComposerFocusState(requestAtStart = focusComposer)
    val currentOnOpenMedia by rememberUpdatedState(onOpenMedia)
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    val currentOnOpenProfile by rememberUpdatedState(onOpenProfile)
    val currentOnOpenThread by rememberUpdatedState(onOpenThread)
    val launchers = rememberAttachLaunchers(viewModel::onEvent)
    val attach =
        rememberAttachInputs(
            launchers = launchers,
            onEvent = viewModel::onEvent,
            caption = viewModel.attachCaption,
            pollEditor = viewModel.pollEditor,
            snackbarHostState = snackbarHostState,
            location = rememberLocationInputs(locationViewModel, snackbarHostState),
        )
    ChatEffects(
        effects = viewModel.effects,
        snackbarHostState = snackbarHostState,
        onOpenMedia = { currentOnOpenMedia(chatId, it) },
        onOpenChat = { currentOnOpenChat(it) },
        onOpenProfile = { currentOnOpenProfile?.invoke(it) },
        onOpenThread = { chat, thread -> currentOnOpenThread(chat, thread) },
        composerFocus = composerFocus,
        onEvent = viewModel::onEvent,
        onShareLocation = { attach.onPick(AttachOption.Location) },
        onLaunchCamera = { path ->
            launchers.takePhoto(path).also { if (!it) viewModel.onEvent(ChatEvent.PhotoTaken(taken = false)) }
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
        recording = viewModel.voiceRecording.collectAsStateWithLifecycle().value,
        reportComment = viewModel.reportComment,
        emojiPanel = panelViewModel.uiState.collectAsStateWithLifecycle().value,
        onEmojiPanelEvent = panelViewModel::onEvent,
        onOpenProfile = onOpenProfile,
        onOpenChatInfo = onOpenChatInfo,
        commandSuggestions = viewModel.commandSuggestions.collectAsStateWithLifecycle().value,
        inlineResults = viewModel.inlineResults.collectAsStateWithLifecycle().value,
        search = viewModel.searchState.collectAsStateWithLifecycle().value,
        searchQuery = viewModel.searchQuery,
        botReply = viewModel.botReply.collectAsStateWithLifecycle().value,
    )
    ChatSheets(viewModel)
    // No screenshots, screen recordings or recent-apps thumbnails of a secret chat (REQUIREMENTS.md FR-E1).
    SecureWindow(secure = state.header?.secret != null)
}

/**
 * Handles [effects]: a snackbar for errors, for a file that cannot be played, for a replied or found message that is
 * gone, for a scheduled message's time ([scheduledTime]), for an action
 * that is not allowed and for what is not there yet, the newest replacing the one shown; a downloaded document opens
 * in another app through [openFile] (the app's `FileProvider`, [viewTdlibFile]) and a location in a maps app, or a
 * snackbar says that no app can; a copied text goes to the clipboard through [copyText], with a snackbar before
 * Android 13, which confirms copies itself; a focus request goes to [composerFocus]; media to open goes to
 * [onOpenMedia], a profile to [onOpenProfile], a chat a mention named to [onOpenChat] and a message's thread to
 * [onOpenThread]; the camera app opens through [onLaunchCamera], or a snackbar
 * says that no camera app can; a bot's keyboard button that asks for the location opens the "Location" sheet through
 * [onShareLocation]. After forwarding a snackbar says to how many chats, or to which one, with an action
 * that opens it through [onOpenChat] if it is another chat; or for how many it failed, and why. A sponsored message's
 * link opens through [openUri] (the platform's handler), or a snackbar says that no app can; what came of reporting
 * one is a snackbar too. Selecting, reporting and saving messages ([SelectionEffect], [selectionNotice]) copy several
 * texts at once, thank for a report, and say how saving to Downloads went, with an action that opens the one file
 * saved; the storage permission that saving needs on Android 8–9 is asked for here, after a snackbar that explains it
 * where the system wants that, and its answer goes to [onEvent]. The voice recording's snackbars explain the
 * microphone permission with an action that asks for it (its answer goes to [onEvent] too), offer the app's settings
 * once it is refused, and say to hold the button or that recording failed.
 * A message that failed to send while the screen is shown gets a `Reject` haptic on Android 14 and later
 * (`docs/DESIGN_SYSTEM.md` §5).
 */
@Suppress(
    // One branch per effect, all in one place: the compiler sees none is missed.
    "CyclomaticComplexMethod",
    "LongMethod",
)
@Composable
internal fun ChatEffects(
    effects: Flow<ChatEffect>,
    snackbarHostState: SnackbarHostState,
    onOpenMedia: (messageId: Long) -> Unit,
    onOpenChat: (chatId: Long) -> Unit = {},
    onOpenProfile: (userId: Long) -> Unit = {},
    onOpenThread: (chatId: Long, topic: MessageTopic) -> Unit = { _, _ -> },
    openFile: Context.(path: String, mimeType: String?, chooserTitle: String) -> ExternalFileResult =
        Context::viewTdlibFile,
    copyText: Context.(text: String) -> Unit = Context::copyPlainText,
    composerFocus: ComposerFocusState = rememberComposerFocusState(),
    onLaunchCamera: (path: String) -> Boolean = { false },
    openUri: (uri: String) -> Boolean = rememberUriOpener(),
    onEvent: (ChatEvent) -> Unit = {},
    onShareLocation: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val noticeActions = rememberNoticeActions(onOpenChat, NoticeFiles(openFile, copyText), showErrorDetails, onEvent)
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(effects) {
        var snackbar: Job? = null
        effects.collect { effect ->
            val message =
                when (effect) {
                    is ChatEffect.ShowError, is TranslationEffect -> {
                        failureNotice(resources, effect, showErrorDetails)
                    }

                    is ChatEffect.OpenFile -> {
                        context.openedFileNotice(effect, resources, openFile)
                    }

                    ChatEffect.PlaybackFailed, ChatEffect.MessageUnavailable, ChatEffect.ActionUnavailable,
                    ChatEffect.JoinRequestSent, ChatEffect.JoinRefused, ChatEffect.PinnedHidden,
                    ChatEffect.MiniAppsUnsupported, ChatEffect.GamesUnsupported, ChatEffect.NoMessagesForDay,
                    is ChatEffect.SponsoredNotice,
                    is VoiceEffect,
                    -> {
                        resources.getString(effect.noticeText())
                    }

                    is ChatEffect.OpenLocation -> {
                        context.openMap(effect, resources)
                    }

                    is ChatEffect.MessageScheduled -> {
                        resources.getString(R.string.composer_scheduled, context.scheduledTime(effect.at))
                    }

                    is ChatEffect.CopyText -> {
                        context.copyText(effect.text)
                        copiedNotice(resources)
                    }

                    is ChatEffect.OpenProfile, is ChatEffect.OpenChat, is ChatEffect.OpenThread -> {
                        openNamed(effect, onOpenProfile, onOpenChat, onOpenThread)
                    }

                    is ChatEffect.OpenMedia, ChatEffect.FocusComposer, is ChatEffect.LaunchCamera,
                    is BotKeyboardEffect,
                    -> {
                        handOver(effect, onOpenMedia, composerFocus, onLaunchCamera, resources, onShareLocation)
                    }

                    is ChatEffect.OpenUrl -> {
                        if (openUri(effect.url)) "" else resources.getString(R.string.sponsored_no_app)
                    }

                    is ChatEffect.Forwarded, is ChatEffect.ForwardFailed, is SelectionEffect, is BotButtonEffect -> {
                        noticeActions.noticeOf(effect)
                    }

                    ChatEffect.SendFailed -> {
                        haptics.rejectSend()
                    }
                }
            if (message.isNotEmpty()) {
                // Cancelling showSnackbar dismisses its snackbar.
                snackbar?.cancel()
                snackbar = launch { snackbarHostState.showNotice(message, effect, resources, noticeActions::onAction) }
            }
        }
    }
}

/**
 * Hands [effect] to the screen: media to the viewer through [onOpenMedia], the focus to the composer through
 * [composerFocus], a photo to take to the camera app through [onLaunchCamera], a bot's request for the location to
 * the "Location" sheet through [onShareLocation], which asks the user first; the snackbar when no camera app opens,
 * else nothing.
 */
private fun handOver(
    effect: ChatEffect,
    onOpenMedia: (messageId: Long) -> Unit,
    composerFocus: ComposerFocusState,
    onLaunchCamera: (path: String) -> Boolean,
    resources: Resources,
    onShareLocation: () -> Unit = {},
): String =
    when (effect) {
        BotKeyboardEffect.ShareLocation -> {
            onShareLocation()
            ""
        }

        is ChatEffect.OpenMedia -> {
            onOpenMedia(effect.messageId)
            ""
        }

        ChatEffect.FocusComposer -> {
            composerFocus.request()
            ""
        }

        is ChatEffect.LaunchCamera -> {
            val launched = onLaunchCamera(effect.path)
            resources.getString(R.string.attach_no_camera_app).takeUnless { launched }.orEmpty()
        }

        else -> {
            ""
        }
    }

/**
 * Opens the downloaded document of [effect] in another app through [openFile], with the chooser's title; the snackbar
 * when no app takes it or the file is gone, else nothing.
 */
internal fun Context.openedFileNotice(
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
        ChatEffect.JoinRequestSent -> R.string.composer_join_request_sent
        ChatEffect.JoinRefused -> R.string.composer_join_refused
        ChatEffect.PinnedHidden -> R.string.pinned_hidden
        ChatEffect.MiniAppsUnsupported -> R.string.bot_mini_apps_unsupported
        ChatEffect.GamesUnsupported -> R.string.inline_games_unsupported
        ChatEffect.NoMessagesForDay -> R.string.search_no_messages_for_day
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
