package dev.kyralabs.pingogram.feature.chats.ui.unanswered

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
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import kotlinx.coroutines.launch

/**
 * The Unanswered page (`REQUIREMENTS.md` FR-X22), pushed above the Chats tab: hoists its [UnansweredViewModel]; a
 * row's Reply opens its chat with the message box focused through [onReply], a dismissal not saved is a snackbar,
 * and [onBack] leaves. The rows follow the chats, so a chat answered from here is gone on the way back.
 */
@Composable
internal fun UnansweredRoute(
    onBack: () -> Unit,
    onReply: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnansweredViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnReply by rememberUpdatedState(onReply)
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UnansweredEffect.Reply -> {
                    currentOnReply(effect.chatId)
                }

                is UnansweredEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    UnansweredScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}
