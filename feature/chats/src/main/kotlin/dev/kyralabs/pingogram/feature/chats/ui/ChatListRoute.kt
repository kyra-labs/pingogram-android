package dev.kyralabs.pingogram.feature.chats.ui

import android.content.res.Resources
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.navigation.ChatListHeaderActions
import dev.kyralabs.pingogram.feature.chats.ui.focus.ChatListFocus
import dev.kyralabs.pingogram.feature.chats.ui.focus.FocusSwitchEffects
import dev.kyralabs.pingogram.feature.chats.ui.focus.FocusSwitchEvent
import dev.kyralabs.pingogram.feature.chats.ui.focus.FocusSwitchViewModel
import dev.kyralabs.pingogram.feature.chats.ui.focus.FocusSwitcherHost
import dev.kyralabs.pingogram.feature.chats.ui.focus.LocalChatListFocus
import dev.kyralabs.pingogram.feature.chats.ui.restore.RestorePromptHost
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsEvent
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsRow
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsUiState
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsViewModel
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryUploadSheet
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryUploadSheetEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The Chats tab: hoists its [ChatListViewModel], and reports the app fully drawn once the chat list shows its chats or
 * that there are none, for the startup benchmark's `timeToFullDisplay` (`docs/PERFORMANCE.md` §1–2). Tapping a row
 * calls [onOpenChat] with its chat; [onOpenArchive] opens the archive, and the "New chat" buttons [onOpenNewChat].
 * [openChatId] is the chat whose conversation shows beside the list, on expanded windows. Above the list, the
 * request to allow notifications ([NotificationPromptHost]), on Android 13+ until answered, and the offer to restore a
 * backup ([RestorePromptHost], whose "Restore" opens Backup & restore through [headerActions]). The list's first row
 * holds the story rings ([StoryRingsRow] of [storyRings]) while there are any; a ring calls [onOpenStory] with its
 * chat, and the user's own ring [onNewStory] to post one (`docs/adr/0036-posting-stories.md`). The route reads only
 * whether there are rings, so the rings changing recomposes the row alone. A folder tab's menu calls
 * [onEditFolder] with its folder (null for "All chats"), and offers "Edit folder" only where it is given. The header's
 * search and account buttons do what [headerActions] says; its status line reads [ChatListViewModel.summary]. A row
 * of the "Keyword alerts" tab opens its chat at its match through [onOpenMessage], or as any row without it. A topic a
 * forum's row names opens through [onOpenTopic], or the forum without it.
 */
@Composable
internal fun ChatListRoute(
    onOpenArchive: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenStory: (chatId: Long) -> Unit = {},
    onNewStory: () -> Unit = {},
    openChatId: Long? = null,
    onEditFolder: ((folderId: Int?) -> Unit)? = null,
    headerActions: ChatListHeaderActions? = null,
    onOpenMessage: ((chatId: Long, messageId: Long) -> Unit)? = null,
    onOpenTopic: ((chatId: Long, topicId: Int) -> Unit)? = null,
    viewModel: ChatListViewModel =
        hiltViewModel<ChatListViewModel, ChatListViewModel.Factory> { it.create(ChatListKind.Chats) },
    storyRings: StoryRingsViewModel = hiltViewModel(),
    focusSwitch: FocusSwitchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Read by the header alone: a count changing recomposes nothing else.
    val summary = viewModel.summary.collectAsStateWithLifecycle()
    ReportDrawnWhen { state.content is ChatListContent.Content || state.content is ChatListContent.Empty }
    val rings = storyRings.uiState.collectAsStateWithLifecycle()
    val hasStories by remember(rings) { derivedStateOf { rings.value.rings.isNotEmpty() } }
    val currentOnOpenStory by rememberUpdatedState(onOpenStory)
    var uploadShown by rememberSaveable { mutableStateOf(false) }
    val stories = rememberStoriesRow(storyRings, rings, onOpenStory, onNewStory) { uploadShown = true }
    if (uploadShown) {
        StoryUploadHost(
            state = rings.value,
            onEvent = storyRings::onEvent,
            onOpenStory = { currentOnOpenStory(it) },
            onClose = { uploadShown = false },
        )
    }
    // Read by the header's pill and the switcher alone.
    val focusState = focusSwitch.uiState.collectAsStateWithLifecycle()
    val focus =
        remember(focusSwitch, focusState) {
            ChatListFocus(pill = { focusState.value.pill }, onOpen = { focusSwitch.onEvent(FocusSwitchEvent.Open) })
        }
    FocusSwitcherHost(focusState, focusSwitch::onEvent, onManage = { headerActions?.openFocus?.invoke() })
    CompositionLocalProvider(LocalChatListFocus provides focus) {
        ChatListDestination(
            state = state,
            viewModel = viewModel,
            navigation =
                ChatListNavigation(
                    onOpenArchive = onOpenArchive,
                    onOpenChat = onOpenChat,
                    onOpenNewChat = onOpenNewChat,
                    onBack = {},
                    onOpenMessage = onOpenMessage ?: { chatId, _ -> onOpenChat(chatId) },
                    onOpenTopic = onOpenTopic ?: { chatId, _ -> onOpenChat(chatId) },
                ),
            modifier = modifier,
            openChatId = openChatId,
            header = {
                Column {
                    NotificationPromptHost()
                    RestorePromptHost(onOpenBackup = { headerActions?.openBackup?.invoke(it) })
                }
            },
            stories = stories.takeIf { hasStories },
            onEditFolder = onEditFolder,
            summary = { summary.value },
            headerActions = headerActions,
            moreEffects = { snackbarHostState -> FocusSwitchEffects(focusSwitch.effects, snackbarHostState) },
        )
    }
}

/**
 * The story rings' row of [storyRings], above the chats: a ring calls [onOpenStory], the user's own [onNewStory], and
 * the upload's ring [onShowUpload]. It reads [rings] in its own composition, not in the caller's.
 */
@Composable
private fun rememberStoriesRow(
    storyRings: StoryRingsViewModel,
    rings: State<StoryRingsUiState>,
    onOpenStory: (chatId: Long) -> Unit,
    onNewStory: () -> Unit,
    onShowUpload: () -> Unit,
): @Composable (Modifier) -> Unit {
    val currentOnOpenStory by rememberUpdatedState(onOpenStory)
    val currentOnNewStory by rememberUpdatedState(onNewStory)
    val currentOnShowUpload by rememberUpdatedState(onShowUpload)
    return remember(storyRings, rings) {
        { rowModifier ->
            StoryRingsRow(
                rings = rings.value.rings,
                onOpen = { currentOnOpenStory(it) },
                onNearEnd = { storyRings.onEvent(StoryRingsEvent.NearEnd) },
                modifier = rowModifier,
                onAddStory = { currentOnNewStory() },
                onShowUpload = { currentOnShowUpload() },
            )
        }
    }
}

/**
 * The archived chats (`REQUIREMENTS.md` FR-L9), pushed above the Chats tab; [onBack] returns to it, tapping a row
 * calls [onOpenChat], a topic a forum's row names [onOpenTopic], and the top bar's settings button [onOpenSettings].
 */
@Composable
internal fun ArchivedChatsRoute(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenTopic: (chatId: Long, topicId: Int) -> Unit = { chatId, _ -> onOpenChat(chatId) },
    viewModel: ChatListViewModel =
        hiltViewModel<ChatListViewModel, ChatListViewModel.Factory> { it.create(ChatListKind.Archive) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatListDestination(
        state = state,
        viewModel = viewModel,
        navigation =
            ChatListNavigation(
                onOpenArchive = {},
                onOpenChat = onOpenChat,
                onOpenNewChat = {},
                onBack = onBack,
                onOpenArchiveSettings = onOpenSettings,
                onOpenTopic = onOpenTopic,
            ),
        modifier = modifier,
    )
}

/**
 * Where a chat list leads: the archive, a chat (at a message: a keyword alert's match, or the chat where [onOpenMessage]
 * is not given; a forum's topic, or the forum where [onOpenTopic] is not given), the New chat screen, back, and the
 * archive's settings.
 */
private class ChatListNavigation(
    val onOpenArchive: () -> Unit,
    val onOpenChat: (chatId: Long) -> Unit,
    val onOpenNewChat: () -> Unit,
    val onBack: () -> Unit,
    val onOpenArchiveSettings: () -> Unit = {},
    val onOpenMessage: (chatId: Long, messageId: Long) -> Unit = { chatId, _ -> onOpenChat(chatId) },
    val onOpenTopic: (chatId: Long, topicId: Int) -> Unit = { chatId, _ -> onOpenChat(chatId) },
) {
    /** Opens [effect]'s chat, at its message or in its topic if it names one. */
    fun open(effect: ChatListEffect.OpenChat) {
        val messageId = effect.messageId
        val topicId = effect.topicId
        when {
            messageId != null -> onOpenMessage(effect.chatId, messageId)
            topicId != null -> onOpenTopic(effect.chatId, topicId)
            else -> onOpenChat(effect.chatId)
        }
    }
}

@Composable
private fun ChatListDestination(
    state: ChatListUiState,
    viewModel: ChatListViewModel,
    navigation: ChatListNavigation,
    modifier: Modifier = Modifier,
    openChatId: Long? = null,
    header: @Composable () -> Unit = {},
    stories: (@Composable (Modifier) -> Unit)? = null,
    onEditFolder: ((folderId: Int?) -> Unit)? = null,
    summary: () -> ChatListSummary = { ChatListSummary() },
    headerActions: ChatListHeaderActions? = null,
    moreEffects: @Composable (SnackbarHostState) -> Unit = {},
    swipesViewModel: ChatSwipesViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    moreEffects(snackbarHostState)
    val swipes by swipesViewModel.swipes.collectAsStateWithLifecycle()
    // The folders beside the one shown, which only a swipe of the list shows: mapped only while that swipe is on.
    val neighbourLists =
        if (swipes.changesFolder) viewModel.neighbourLists.collectAsStateWithLifecycle() else null
    val neighbours = remember(neighbourLists) { { neighbourLists?.value ?: FolderNeighbourLists() } }
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
            onEditFolder = onEditFolder,
            summary = summary,
            headerActions = headerActions,
            neighbours = neighbours,
        )
    }
}

/**
 * Handles [effects]: snackbars, the newest replacing the one shown (archiving chat after chat keeps one "Undo", for
 * the last), and opening the archive, its settings, a chat or the New chat screen ([navigation]). A snackbar with
 * "Undo" stays long, so there is time to reach it.
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

                is ChatListEffect.ArchiveRowHidden -> {
                    message = resources.getString(R.string.chat_list_archive_row_hidden)
                    undo = ChatListEvent.SetArchiveRowStyle(effect.previous)
                }

                ChatListEffect.OpenArchiveSettings -> {
                    message = ""
                    currentNavigation.onOpenArchiveSettings()
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
                    currentNavigation.open(effect)
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

/**
 * The sheet of the user's stories being posted, while there are some ([StoryUploadSheet]); it closes by itself once
 * none are left, as when they were all posted or stopped.
 */
@Composable
private fun StoryUploadHost(
    state: StoryRingsUiState,
    onEvent: (StoryRingsEvent) -> Unit,
    onOpenStory: (chatId: Long) -> Unit,
    onClose: () -> Unit,
) {
    val own = state.rings.firstOrNull { it.isMine }
    val upload = own?.upload
    if (own == null || upload == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    StoryUploadSheet(upload, state.cancel, canView = own.storyCount > 0) { event ->
        when (event) {
            StoryUploadSheetEvent.Stop -> {
                onEvent(StoryRingsEvent.CancelUploads)
            }

            StoryUploadSheetEvent.View -> {
                onClose()
                onOpenStory(own.chatId)
            }

            StoryUploadSheetEvent.Dismiss -> {
                onEvent(StoryRingsEvent.CancelErrorShown)
                onClose()
            }
        }
    }
}
