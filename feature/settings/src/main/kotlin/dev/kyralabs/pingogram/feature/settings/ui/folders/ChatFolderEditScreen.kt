package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.preview.ChatFolderEditPreviewStates
import kotlinx.coroutines.launch

/**
 * The folder editor: hoists [ChatFolderEditViewModel], closes once the folder is saved or deleted ([onBack], which
 * also leaves it), buzzes when "Save" is refused, and shows Telegram's refusals in snackbars.
 */
@Composable
internal fun ChatFolderEditRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatFolderEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ChatFolderEditEffect.Done -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onBack()
                }

                ChatFolderEditEffect.Rejected -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                }

                is ChatFolderEditEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    ChatFolderEditScreen(
        state = state,
        picker = picker,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Editing a chat folder (`docs/DESIGN_SYSTEM.md` §6, "Chat folders"), a settings page: the name in the rounded tonal
 * field with its count; the icon, "Automatic" or one of the symbols; the included chats and kinds of chats with "Add
 * chats", and the excluded ones with "Exclude chats", each removable; "Delete folder" in the error role at the start,
 * behind an `AlertDialog`, and "Save" (or "Create") at the end. [picker] is the chat picker's sheet while it shows.
 */
@Suppress("LongParameterList") // A page's two states, its events, the way out and its snackbars.
@Composable
internal fun ChatFolderEditScreen(
    state: ChatFolderEditUiState,
    picker: FolderPickerUiState?,
    onEvent: (ChatFolderEditEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val isNew = (state as? ChatFolderEditUiState.Content)?.isNew ?: false
    SettingsPageScaffold(
        title =
            stringResource(
                if (isNew) R.string.settings_chat_folder_new_title else R.string.settings_chat_folder_edit_title,
            ),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) {
        when (state) {
            ChatFolderEditUiState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_chat_folder_loading))
            }

            is ChatFolderEditUiState.Error -> {
                SettingsError(state.error, onRetry = { onEvent(ChatFolderEditEvent.Retry) })
            }

            is ChatFolderEditUiState.Content -> {
                FolderNameField(state, onEvent)
                FolderIconGroup(state, onEvent)
                FolderChatsGroup(state, FolderPickerSide.Included, onEvent)
                FolderChatsGroup(state, FolderPickerSide.Excluded, onEvent)
                FolderEditActions(state, onEvent)
            }
        }
    }
    if (picker != null) FolderPickerSheet(picker, onEvent)
}

@PingogramPreviews
@Composable
private fun ChatFolderEditScreenPreview() {
    PingogramPreview {
        ChatFolderEditScreen(state = ChatFolderEditPreviewStates.editing, picker = null, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun ChatFolderEditScreenNewPreview() {
    PingogramPreview {
        ChatFolderEditScreen(state = ChatFolderEditPreviewStates.new, picker = null, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun ChatFolderEditScreenRefusedPreview() {
    PingogramPreview {
        ChatFolderEditScreen(state = ChatFolderEditPreviewStates.refused, picker = null, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun ChatFolderEditScreenLoadingPreview() {
    PingogramPreview {
        ChatFolderEditScreen(state = ChatFolderEditUiState.Loading, picker = null, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun ChatFolderEditScreenErrorPreview() {
    PingogramPreview {
        ChatFolderEditScreen(
            state = ChatFolderEditUiState.Error(DomainError.Network),
            picker = null,
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun ChatFolderEditScreenPickerPreview() {
    PingogramPreview {
        FolderPickerContent(ChatFolderEditPreviewStates.picker, onEvent = {})
    }
}
