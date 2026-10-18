package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Global search, pushed above the tabs: hoists its [SearchViewModel] and the search field's text (saved, so the query
 * survives rotation and process death, as the view model's copy does), and focuses the field once, on entry, which
 * opens the keyboard; back from a conversation it opened, the keyboard stays closed. A chat result calls
 * [onOpenChat], a message result [onOpenMessage]; [onBack] leaves.
 */
@Composable
internal fun SearchRoute(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val textFieldState = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }
    var focusedOnEntry by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(focusRequester) {
        if (!focusedOnEntry) {
            focusRequester.requestFocus()
            focusedOnEntry = true
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    val currentOnOpenMessage by rememberUpdatedState(onOpenMessage)
    LaunchedEffect(viewModel) {
        var snackbar: Job? = null
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchEffect.OpenChat -> {
                    currentOnOpenChat(effect.chatId)
                }

                is SearchEffect.OpenMessage -> {
                    currentOnOpenMessage(effect.chatId, effect.messageId)
                }

                is SearchEffect.ShowError -> {
                    val message = errorMessage(resources, effect.error, showErrorDetails)
                    // Cancelling showSnackbar dismisses its snackbar.
                    snackbar?.cancel()
                    if (message.isNotEmpty()) snackbar = launch { snackbarHostState.showSnackbar(message) }
                }
            }
        }
    }
    SearchScreen(
        state = state,
        textFieldState = textFieldState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        focusRequester = focusRequester,
        snackbarHostState = snackbarHostState,
    )
}
