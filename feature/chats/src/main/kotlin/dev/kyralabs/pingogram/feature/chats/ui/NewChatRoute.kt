package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The New chat screen, pushed above the Chats tab: hoists its [NewChatViewModel]. A contact calls [onOpenChat] with its
 * private chat; [onBack] leaves. New group, New channel and New contact show a snackbar until their screens exist
 * (`docs/ROADMAP.md` M4), the newest replacing the one shown.
 */
@Composable
internal fun NewChatRoute(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    LaunchedEffect(viewModel) {
        var snackbar: Job? = null
        viewModel.effects.collect { effect ->
            when (effect) {
                is NewChatEffect.OpenChat -> {
                    currentOnOpenChat(effect.chatId)
                }

                NewChatEffect.NotAvailableYet -> {
                    // Cancelling showSnackbar dismisses its snackbar.
                    snackbar?.cancel()
                    val message = resources.getString(R.string.new_chat_not_available)
                    snackbar = launch { snackbarHostState.showSnackbar(message) }
                }
            }
        }
    }
    NewChatScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
