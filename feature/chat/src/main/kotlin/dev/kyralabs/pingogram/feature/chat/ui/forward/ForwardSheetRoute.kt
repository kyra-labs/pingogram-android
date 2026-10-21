package dev.kyralabs.pingogram.feature.chat.ui.forward

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ForwardEvent
import dev.kyralabs.pingogram.feature.chat.ui.forwardNotice
import dev.kyralabs.pingogram.feature.chat.ui.showNotice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The forward destination (`ForwardRoute` of `:core:navigation`): the conversation's forward sheet
 * ([ForwardBottomSheet]) for its [ForwardViewModel], over the screen it was opened from. [onClose] leaves the
 * destination once the sheet closed (sent, or dismissed by a drag, the scrim or the back gesture, after its own exit
 * animation) or could not open.
 */
@Composable
internal fun ForwardSheetRoute(
    onClose: () -> Unit,
    viewModel: ForwardViewModel = hiltViewModel(),
) {
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val closed by viewModel.closed.collectAsStateWithLifecycle()
    val onEvent =
        remember(viewModel) {
            { event: ChatEvent -> if (event is ForwardEvent) viewModel.onEvent(event) }
        }
    sheet?.let { ForwardBottomSheet(it, viewModel.comment, onEvent) }
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(closed) { if (closed) currentOnClose() }
}

/**
 * The snackbars of what came of the forward destination's sheets ([ForwardResults]), in [snackbarHostState] of the
 * screen below it, as the conversation shows its own: "Forwarded to Ada" with an action that opens that chat through
 * [onOpenChat] when it is another chat, "Forwarded to 3 chats", for how many it failed and why ([forwardNotice]); or
 * why the sheet could not open. The newest replaces the one shown.
 */
@Composable
internal fun ForwardResultNotices(
    snackbarHostState: SnackbarHostState,
    onOpenChat: (chatId: Long) -> Unit,
    viewModel: ForwardResultsViewModel = hiltViewModel(),
) {
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    LaunchedEffect(viewModel, snackbarHostState) {
        var shown: Job? = null
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    is ChatEffect.ShowError -> errorMessage(resources, effect.error, showErrorDetails)
                    ChatEffect.ActionUnavailable -> resources.getString(R.string.message_action_unavailable)
                    else -> forwardNotice(resources, effect, showErrorDetails)
                }
            if (message.isNotEmpty()) {
                // Cancelling showSnackbar dismisses its snackbar.
                shown?.cancel()
                shown =
                    launch {
                        snackbarHostState.showNotice(message, effect, resources) { done ->
                            (done as? ChatEffect.Forwarded)?.openChatId?.let(currentOnOpenChat)
                            ""
                        }
                    }
            }
        }
    }
}

/** [ForwardResults] for [ForwardResultNotices], kept by the screen that shows them. */
@HiltViewModel
class ForwardResultsViewModel
    @Inject
    constructor(
        results: ForwardResults,
    ) : ViewModel() {
        /** What came of forwarding, as it comes. */
        internal val effects: Flow<ChatEffect> = results.effects
    }
