package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The conversation screen, pushed above the Chats tab: hoists its [ChatViewModel], and tells it when the screen is
 * started and stopped, which opens and closes the chat in TDLib (`docs/PERFORMANCE.md` §3). The effect follows the
 * screen's lifecycle rather than the view model's: the screen stops when another screen covers it or the app goes to
 * the background, and stops and disposes when it is left. A rotation stops and starts it again with the same view
 * model, so the chat ends up open. [onBack] leaves it.
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
    ChatScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}
