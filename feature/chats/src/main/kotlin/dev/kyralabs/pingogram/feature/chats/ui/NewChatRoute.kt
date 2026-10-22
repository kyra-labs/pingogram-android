package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The New chat screen, pushed above the Chats tab: hoists its [NewChatViewModel]. A contact calls [onOpenChat] with its
 * private chat, New group [onNewGroup], New channel [onNewChannel] and New contact [onAddContact]; [onBack] leaves.
 */
@Composable
internal fun NewChatRoute(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onNewGroup: () -> Unit,
    onNewChannel: () -> Unit,
    onAddContact: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    val currentOnNewGroup by rememberUpdatedState(onNewGroup)
    val currentOnNewChannel by rememberUpdatedState(onNewChannel)
    val currentOnAddContact by rememberUpdatedState(onAddContact)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NewChatEffect.OpenChat -> currentOnOpenChat(effect.chatId)
                NewChatEffect.NewGroup -> currentOnNewGroup()
                NewChatEffect.NewChannel -> currentOnNewChannel()
                NewChatEffect.AddContact -> currentOnAddContact()
            }
        }
    }
    NewChatScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}
