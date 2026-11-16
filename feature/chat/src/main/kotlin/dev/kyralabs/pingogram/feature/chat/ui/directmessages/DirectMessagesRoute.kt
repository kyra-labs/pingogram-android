package dev.kyralabs.pingogram.feature.chat.ui.directmessages

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.chat.ui.topics.showError
import kotlinx.coroutines.launch

/**
 * The direct messages of channel chat [chatId] as its administrators see them, where such a chat opens: hoists its
 * [DirectMessagesViewModel], keyed by the chat like the conversation's. A tapped row opens the person's conversation
 * through [onOpenTopic]; refusals are snackbars. [onBack] leaves, null shows no back button.
 */
@Composable
internal fun DirectMessagesRoute(
    chatId: Long,
    onBack: (() -> Unit)?,
    onOpenTopic: (topicId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DirectMessagesViewModel =
        hiltViewModel<DirectMessagesViewModel, DirectMessagesViewModel.Factory>(key = "direct-$chatId") {
            it.create(chatId)
        },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { error ->
            launch { snackbarHostState.showError(errorMessage(resources, error, showDetails), error) }
        }
    }
    DirectMessagesScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenTopic = onOpenTopic,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
