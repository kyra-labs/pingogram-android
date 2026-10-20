package dev.kyralabs.pingogram.feature.chats.ui

import android.content.res.Resources
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The Chats tab: hoists its [ChatListViewModel], and reports the app fully drawn once the chat list shows its chats or
 * that there are none, for the startup benchmark's `timeToFullDisplay` (`docs/PERFORMANCE.md` §1–2). Tapping a row
 * calls [onOpenChat] with its chat; [onOpenArchive] opens the archive, and the "New chat" buttons [onOpenNewChat].
 * [openChatId] is the chat whose conversation shows beside the list, on expanded windows. Above the list, the
 * request to allow notifications ([NotificationPromptHost]), on Android 13+ until answered.
 */
@Composable
internal fun ChatListRoute(
    onOpenArchive: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    openChatId: Long? = null,
    viewModel: ChatListViewModel =
        hiltViewModel<ChatListViewModel, ChatListViewModel.Factory> { it.create(ChatListKind.Chats) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportDrawnWhen { state.content is ChatListContent.Content || state.content is ChatListContent.Empty }
    ChatListDestination(
        state = state,
        viewModel = viewModel,
        navigation = ChatListNavigation(onOpenArchive, onOpenChat, onOpenNewChat, onBack = {}),
        modifier = modifier,
        openChatId = openChatId,
        header = { NotificationPromptHost() },
    )
}

/**
 * The archived chats (`REQUIREMENTS.md` FR-L9), pushed above the Chats tab; [onBack] returns to it, and tapping a row
 * calls [onOpenChat].
 */
@Composable
internal fun ArchivedChatsRoute(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel =
        hiltViewModel<ChatListViewModel, ChatListViewModel.Factory> { it.create(ChatListKind.Archive) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatListDestination(
        state = state,
        viewModel = viewModel,
        navigation = ChatListNavigation(onOpenArchive = {}, onOpenChat, onOpenNewChat = {}, onBack = onBack),
        modifier = modifier,
    )
}

/** Where a chat list leads: the archive, a chat, the New chat screen, and back. */
private class ChatListNavigation(
    val onOpenArchive: () -> Unit,
    val onOpenChat: (chatId: Long) -> Unit,
    val onOpenNewChat: () -> Unit,
    val onBack: () -> Unit,
)

@Composable
private fun ChatListDestination(
    state: ChatListUiState,
    viewModel: ChatListViewModel,
    navigation: ChatListNavigation,
    modifier: Modifier = Modifier,
    openChatId: Long? = null,
    header: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    ChatListEffects(viewModel.effects, snackbarHostState, navigation, onEvent = viewModel::onEvent)
    ChatListScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = navigation.onBack,
        openChatId = openChatId,
        header = header,
    )
}

/**
 * Handles [effects]: snackbars, the newest replacing the one shown (archiving chat after chat keeps one "Undo", for
 * the last), and opening the archive, a chat or the New chat screen ([navigation]). A snackbar with "Undo" stays long,
 * so there is time to reach it.
 */
@Composable
private fun ChatListEffects(
    effects: Flow<ChatListEffect>,
    snackbarHostState: SnackbarHostState,
    navigation: ChatListNavigation,
    onEvent: (ChatListEvent) -> Unit,
) {
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val currentNavigation by rememberUpdatedState(navigation)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(effects) {
        var snackbar: Job? = null
        effects.collect { effect ->
            val message: String
            var undo: ChatListEvent? = null
            when (effect) {
                is ChatListEffect.ChatsArchived -> {
                    message = archivedMessage(resources, effect.chatIds.size)
                    undo = ChatListEvent.UndoArchive(effect.chatIds)
                }

                is ChatListEffect.ShowError -> {
                    message = errorMessage(resources, effect.error, showErrorDetails)
                }

                ChatListEffect.OpenArchive -> {
                    message = ""
                    currentNavigation.onOpenArchive()
                }

                ChatListEffect.OpenNewChat -> {
                    message = ""
                    currentNavigation.onOpenNewChat()
                }

                is ChatListEffect.OpenChat -> {
                    message = ""
                    currentNavigation.onOpenChat(effect.chatId)
                }
            }
            if (message.isNotEmpty()) {
                // Cancelling showSnackbar dismisses its snackbar; snackbars never hold up the other effects.
                snackbar?.cancel()
                snackbar =
                    launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                message = message,
                                actionLabel = undo?.let { resources.getString(R.string.chat_list_undo) },
                                duration = if (undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
                            )
                        if (result == SnackbarResult.ActionPerformed && undo != null) currentOnEvent(undo)
                    }
            }
        }
    }
}

/** "Chat archived", or "3 chats archived". */
private fun archivedMessage(
    resources: Resources,
    count: Int,
): String =
    if (count == 1) {
        resources.getString(R.string.chat_list_archived_one)
    } else {
        resources.getQuantityString(R.plurals.chat_list_archived_many, count, count)
    }
