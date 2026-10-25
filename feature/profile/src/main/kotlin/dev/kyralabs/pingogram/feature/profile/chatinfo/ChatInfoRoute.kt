package dev.kyralabs.pingogram.feature.profile.chatinfo

import android.content.Context
import android.os.Build
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaActions
import dev.kyralabs.pingogram.feature.profile.ui.copyPlainText
import dev.kyralabs.pingogram.feature.profile.ui.shareText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The info page of the route's group or channel: hoists its [ChatInfoViewModel] and handles its [ChatInfoEffect]s
 * through [navigation]. Errors and what was done are snackbars; copied text goes to the clipboard (with a snackbar
 * before Android 13, which confirms copies itself), a link to the system's share sheet. The dialogs and the QR code's
 * sheet show over the page while the view model has one.
 */
@Composable
internal fun ChatInfoRoute(
    navigation: ChatInfoNavigation,
    modifier: Modifier = Modifier,
    viewModel: ChatInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val media by viewModel.media.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    val currentNavigation by rememberUpdatedState(navigation)
    val mediaActions =
        remember(uriHandler) {
            SharedMediaActions(
                onOpenMedia = { chatId, messageId -> currentNavigation.onOpenMedia(chatId, messageId) },
                onOpenMessage = { chatId, messageId -> currentNavigation.onOpenMessage(chatId, messageId) },
                onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
            )
        }
    ChatInfoEffects(viewModel.effects, snackbarHostState, navigation)
    ChatInfoScreen(
        state = state,
        media = media,
        onEvent = viewModel::onEvent,
        onMediaEvent = viewModel::onMediaEvent,
        onBack = navigation.onBack,
        mediaActions = mediaActions,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
    (state as? ChatInfoUiState.Content)?.let { content ->
        ChatInfoDialogs(content.dialog, content.header.name.resolve(), content.kind, viewModel::onEvent)
    }
}

/**
 * Handles [effects]: navigation through [navigation]; copied text goes to the clipboard through [copyText], a link to
 * the share sheet through [share]; errors and a cleared history are snackbars, the newest replacing the one shown.
 */
@Composable
internal fun ChatInfoEffects(
    effects: Flow<ChatInfoEffect>,
    snackbarHostState: SnackbarHostState,
    navigation: ChatInfoNavigation,
    copyText: Context.(text: String) -> Unit = Context::copyPlainText,
    share: Context.(text: String) -> Unit = Context::shareText,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val currentNavigation by rememberUpdatedState(navigation)
    LaunchedEffect(effects) {
        var snackbar: Job? = null
        effects.collect { effect ->
            val message =
                when {
                    currentNavigation.navigate(effect) -> {
                        ""
                    }

                    effect is ChatInfoEffect.HistoryCleared -> {
                        resources.getString(R.string.chat_info_history_cleared)
                    }

                    effect is ChatInfoEffect.ShowError -> {
                        errorMessage(resources, effect.error, showDetails)
                    }

                    effect is ChatInfoEffect.CopyText -> {
                        context.copyText(effect.text)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ""
                        } else {
                            resources.getString(
                                when (effect.what) {
                                    CopiedChatText.Link -> R.string.chat_info_link_copied
                                    CopiedChatText.Description -> R.string.profile_bio_copied
                                    CopiedChatText.Id -> R.string.profile_id_copied
                                },
                            )
                        }
                    }

                    effect is ChatInfoEffect.Share -> {
                        context.share(effect.text)
                        ""
                    }

                    else -> {
                        ""
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
