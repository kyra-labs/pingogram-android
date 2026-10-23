package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsBody
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageFrame
import dev.kyralabs.pingogram.feature.settings.ui.preview.ChatFoldersPreviewStates
import kotlinx.coroutines.launch

/**
 * The Chat folders page: hoists [ChatFoldersViewModel] and shows its failures in snackbars. [onOpenFolder] opens a
 * folder's editor (null: a new folder); [onBack] leaves the page.
 */
@Composable
internal fun ChatFoldersRoute(
    onOpenFolder: (Int?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatFoldersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatFoldersEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    ChatFoldersScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenFolder = onOpenFolder,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Chat folders (`REQUIREMENTS.md` FR-L3): "New folder", the user's folders as the chat list's tabs show them, each with
 * its icon, name and what it includes, moved by dragging its handle or after a long press (screen readers: "Move up",
 * "Move down"), opened by a tap; then the folders Telegram suggests, each with "Add".
 */
@Suppress("LongParameterList") // A page's state, its events, its two ways out, and its hoisted states.
@Composable
internal fun ChatFoldersScreen(
    state: ChatFoldersUiState,
    onEvent: (ChatFoldersEvent) -> Unit,
    onOpenFolder: (Int?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    SettingsPageFrame(
        title = stringResource(R.string.settings_chat_folders),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        SettingsBody(innerPadding) { contentPadding ->
            when (state) {
                ChatFoldersUiState.Loading -> {
                    SettingsLoading(
                        description = stringResource(R.string.settings_chat_folders_loading),
                        modifier = Modifier.padding(contentPadding),
                    )
                }

                is ChatFoldersUiState.Error -> {
                    SettingsError(
                        error = state.error,
                        onRetry = { onEvent(ChatFoldersEvent.Retry) },
                        modifier = Modifier.padding(contentPadding),
                    )
                }

                is ChatFoldersUiState.Content -> {
                    ChatFoldersList(state, onEvent, onOpenFolder, listState, contentPadding)
                }
            }
        }
    }
}

@PingogramPreviews
@Composable
private fun ChatFoldersScreenPreview() {
    PingogramPreview {
        ChatFoldersScreen(state = ChatFoldersPreviewStates.content, onEvent = {}, onOpenFolder = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun ChatFoldersScreenNoFoldersPreview() {
    PingogramPreview {
        ChatFoldersScreen(state = ChatFoldersPreviewStates.noFolders, onEvent = {}, onOpenFolder = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun ChatFoldersScreenLoadingPreview() {
    PingogramPreview {
        ChatFoldersScreen(state = ChatFoldersUiState.Loading, onEvent = {}, onOpenFolder = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun ChatFoldersScreenErrorPreview() {
    PingogramPreview {
        ChatFoldersScreen(
            state = ChatFoldersUiState.Error(DomainError.Network),
            onEvent = {},
            onOpenFolder = {},
            onBack = {},
        )
    }
}
