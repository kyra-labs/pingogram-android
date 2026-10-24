package dev.kyralabs.pingogram.feature.chats.ui

import android.content.res.Resources
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsEvent
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsRow
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The Chats tab: hoists its [ChatListViewModel], and reports the app fully drawn once the chat list shows its chats or
 * that there are none, for the startup benchmark's `timeToFullDisplay` (`docs/PERFORMANCE.md` §1–2). Tapping a row
 * calls [onOpenChat] with its chat; [onOpenArchive] opens the archive, and the "New chat" buttons [onOpenNewChat].
 * [openChatId] is the chat whose conversation shows beside the list, on expanded windows. Above the list, the
 * request to allow notifications ([NotificationPromptHost]), on Android 13+ until answered. The list's first row holds
 * the story rings ([StoryRingsRow] of [storyRings]) while there are any; a ring calls [onOpenStory] with its chat. The
 * route reads only whether there are rings, so the rings changing recomposes the row alone.
 */
@Composable
internal fun ChatListRoute(
    onOpenArchive: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenStory: (chatId: Long) -> Unit = {},
    openChatId: Long? = null,
    viewModel: ChatListViewModel =
        hiltViewModel<ChatListViewModel, ChatListViewModel.Factory> { it.create(ChatListKind.Chats) },
    storyRings: StoryRingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportDrawnWhen { state.content is ChatListContent.Content || state.content is ChatListContent.Empty }
    val rings = storyRings.uiState.collectAsStateWithLifecycle()
    val hasStories by remember(rings) { derivedStateOf { rings.value.rings.isNotEmpty() } }
    val currentOnOpenStory by rememberUpdatedState(onOpenStory)
    // Reads the rings in the row's own composition, not here.
    val stories: @Composable (Modifier) -> Unit =
        remember(storyRings, rings) {
            { rowModifier ->
                StoryRingsRow(
                    rings = rings.value.rings,
                    onOpen = { currentOnOpenStory(it) },
                    onNearEnd = { storyRings.onEvent(StoryRingsEvent.NearEnd) },
                    modifier = rowModifier,
                )
            }
        }
    ChatListDestination(
        state = state,
        viewModel = viewModel,
        navigation = ChatListNavigation(onOpenArchive, onOpenChat, onOpenNewChat, onBack = {}),
        modifier = modifier,
        openChatId = openChatId,
        header = { NotificationPromptHost() },
        stories = stories.takeIf { hasStories },
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
    stories: (@Composable (Modifier) -> Unit)? = null,
    swipesViewModel: ChatSwipesViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val swipes by swipesViewModel.swipes.collectAsStateWithLifecycle()
    ChatListEffects(viewModel.effects, snackbarHostState, navigation, onEvent = viewModel::onEvent)
    CompositionLocalProvider(LocalChatSwipes provides swipes) {
        ChatListScreen(
            state = state,
            onEvent = viewModel::onEvent,
            modifier = modifier,
            snackbarHostState = snackbarHostState,
            onBack = navigation.onBack,
            openChatId = openChatId,
            header = header,
            stories = stories,
        )
    }
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
