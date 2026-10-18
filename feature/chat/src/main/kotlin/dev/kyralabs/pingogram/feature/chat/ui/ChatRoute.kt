package dev.kyralabs.pingogram.feature.chat.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Locale

/**
 * The conversation screen, pushed above the Chats tab: hoists its [ChatViewModel], and tells it when the screen is
 * started and stopped, which opens and closes the chat in TDLib (`docs/PERFORMANCE.md` §3). The effect follows the
 * screen's lifecycle rather than the view model's: the screen stops when another screen covers it or the app goes to
 * the background, and stops and disposes when it is left. A rotation stops and starts it again with the same view
 * model, so the chat ends up open. [onBack] leaves it. The view model's [ChatEffect]s show snackbars, open a
 * location in a maps app, or jump to a message ([MessageJumpState]).
 */
@Composable
internal fun ChatRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(ChatEvent.Started)
        onStopOrDispose { viewModel.onEvent(ChatEvent.Stopped) }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val jumpState = rememberMessageJumpState()
    ChatEffects(viewModel.effects, snackbarHostState, jumpState)
    ChatScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        jumpState = jumpState,
    )
}

/**
 * Handles [effects]: a snackbar for errors, for a file that cannot be played, for a replied message that is gone and
 * for what is not there yet, the newest replacing the one shown; a location opens in a maps app, or a snackbar says
 * that no app can show it; a jump to a message goes to [jumpState].
 */
@Composable
private fun ChatEffects(
    effects: Flow<ChatEffect>,
    snackbarHostState: SnackbarHostState,
    jumpState: MessageJumpState,
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

                    ChatEffect.FileOpeningUnavailable -> {
                        resources.getString(R.string.content_open_file_later)
                    }

                    ChatEffect.PlaybackFailed -> {
                        resources.getString(R.string.content_playback_failed)
                    }

                    ChatEffect.MessageUnavailable -> {
                        resources.getString(R.string.chat_message_unavailable)
                    }

                    is ChatEffect.ScrollToMessage -> {
                        jumpState.jumpTo(effect.messageId)
                        ""
                    }

                    is ChatEffect.OpenLocation -> {
                        if (context.openMap(effect)) "" else resources.getString(R.string.content_no_maps_app)
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
