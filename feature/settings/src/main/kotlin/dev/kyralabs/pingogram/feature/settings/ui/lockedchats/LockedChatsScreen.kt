package dev.kyralabs.pingogram.feature.settings.ui.lockedchats

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.LocalChatLauncher
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.PickerAvatarRow
import dev.kyralabs.pingogram.feature.settings.ui.PickerDone
import dev.kyralabs.pingogram.feature.settings.ui.PickerSearchField
import dev.kyralabs.pingogram.feature.settings.ui.PickerTitle
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Locked chats (`docs/adr/0029-locked-chats.md`): hoists [LockedChatsViewModel]. Behind the passcode entry until the
 * chats are open; [onOpenPasscodeLock] opens the passcode lock's page while it is off. A tapped chat opens through
 * `core:ui`'s `LocalChatLauncher`.
 */
@Composable
internal fun LockedChatsRoute(
    onOpenPasscodeLock: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LockedChatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker by viewModel.pickerState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LockedChatsEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    if (state == LockedChatsUiState.Locked) {
        ChatUnlockRoute(title = stringResource(R.string.locked_chats_title), modifier = modifier)
    } else {
        LockedChatsScreen(
            state = state,
            onEvent = viewModel::onEvent,
            onOpenChat = LocalChatLauncher.current,
            onOpenPasscodeLock = onOpenPasscodeLock,
            onBack = onBack,
            modifier = modifier,
            snackbarHostState = snackbarHostState,
        )
        picker?.let { LockedChatsPicker(it, viewModel::onEvent) }
    }
}

/**
 * The Locked chats page (`docs/DESIGN_SYSTEM.md` §6, "Locked chats"): a settings page with "Lock a chat" first, then
 * each locked chat (40 dp avatar, title as user content, its unread messages) with an "Unlock" [TextButton] named with
 * it, and a footer saying what locking does and that it stays on this device. A tap opens the chat ([onOpenChat]).
 * While the passcode lock is off, one row opening it instead.
 */
@Composable
internal fun LockedChatsScreen(
    state: LockedChatsUiState,
    onEvent: (LockedChatsEvent) -> Unit,
    onOpenChat: ((Long) -> Unit)?,
    onOpenPasscodeLock: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.locked_chats_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            LockedChatsUiState.Loading, LockedChatsUiState.Locked -> {
                SettingsLoading(stringResource(R.string.locked_chats_loading))
            }

            LockedChatsUiState.NoPasscode -> {
                SettingsGroup(footer = stringResource(R.string.locked_chats_footer)) {
                    item { shapes ->
                        SettingsRow(
                            title = stringResource(R.string.settings_passcode_lock),
                            shapes = shapes,
                            icon = R.drawable.ic_pin,
                            supportingContent = { Text(stringResource(R.string.locked_chats_needs_passcode)) },
                            onClick = onOpenPasscodeLock,
                        )
                    }
                }
            }

            is LockedChatsUiState.Content -> {
                LockedChatsGroup(state, onEvent, onOpenChat)
            }
        }
    }
}

@Composable
private fun LockedChatsGroup(
    state: LockedChatsUiState.Content,
    onEvent: (LockedChatsEvent) -> Unit,
    onOpenChat: ((Long) -> Unit)?,
) {
    SettingsGroup(footer = stringResource(R.string.locked_chats_footer)) {
        item(key = ADD) { shapes ->
            SettingsRow(
                title = stringResource(R.string.locked_chats_add),
                shapes = shapes,
                icon = UiR.drawable.ic_add,
                enabled = !state.isSaving,
                onClick = { onEvent(LockedChatsEvent.OpenPicker) },
            )
        }
        state.chats.forEach { chat ->
            item(key = chat.id) { shapes ->
                LockedChatItem(
                    chat = chat,
                    shapes = shapes,
                    enabled = !state.isSaving,
                    onOpen = onOpenChat?.let { open -> { open(chat.id) } },
                    onUnlock = { onEvent(LockedChatsEvent.Unlock(chat.id)) },
                )
            }
        }
    }
}

/** A locked chat: its avatar, its title as user content, its unread messages, and "Unlock" named with it. */
@Composable
private fun LockedChatItem(
    chat: LockedChatRow,
    shapes: ListItemShapes,
    enabled: Boolean,
    onOpen: (() -> Unit)?,
    onUnlock: () -> Unit,
) {
    val unlockLabel = stringResource(R.string.locked_chats_unlock_label, chat.title.bidiIsolated())
    val leading: @Composable () -> Unit = {
        Avatar(photo = chat.photo, name = chat.title, id = chat.id, size = MaterialTheme.sizes.leadingIconContainer)
    }
    val supporting: (@Composable () -> Unit)? =
        if (chat.unreadCount > 0) {
            { Text(pluralStringResource(R.plurals.locked_chats_unread, chat.unreadCount, chat.unreadCount)) }
        } else {
            null
        }
    val trailing: @Composable () -> Unit = {
        TextButton(
            onClick = onUnlock,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = unlockLabel },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.locked_chats_unlock))
        }
    }
    val title: @Composable () -> Unit = {
        Text(chat.title, style = LocalTextStyle.current.userContent, maxLines = 2)
    }
    if (onOpen != null) {
        SegmentedListItem(
            onClick = onOpen,
            shapes = shapes,
            colors = settingsRowColors(),
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            content = title,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            colors = settingsRowColors(),
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            content = title,
        )
    }
}

/**
 * The picker of chats to lock: a `ModalBottomSheet` with its title, the search bar's field, the chats as toggleable
 * rows (a picked one `large` in `secondaryContainer`, its avatar turned into a check) and "Done", which locks them, as
 * dismissing does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockedChatsPicker(
    picker: LockedChatsPickerUiState,
    onEvent: (LockedChatsEvent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(LockedChatsEvent.ClosePicker) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LockedChatsPickerContent(picker, onEvent)
    }
}

@Composable
internal fun LockedChatsPickerContent(
    picker: LockedChatsPickerUiState,
    onEvent: (LockedChatsEvent) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = TITLE) { PickerTitle(stringResource(R.string.locked_chats_add)) }
        item(key = SEARCH) {
            PickerSearchField(
                query = picker.query,
                placeholder = stringResource(R.string.settings_privacy_exceptions_search),
            ) { onEvent(LockedChatsEvent.PickerQueryChanged(it)) }
        }
        if (picker.candidates.isEmpty()) {
            item(key = NO_RESULTS) {
                Text(
                    text = stringResource(R.string.settings_chat_folder_picker_no_results),
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(picker.candidates, key = { it.id }) { candidate ->
            PickerAvatarRow(
                id = candidate.id,
                title = candidate.title,
                photo = candidate.photo,
                selected = candidate.selected,
            ) { onEvent(LockedChatsEvent.TogglePicked(candidate.id)) }
        }
        item(key = DONE) {
            PickerDone(stringResource(R.string.settings_chat_folder_picker_done)) {
                onEvent(LockedChatsEvent.ClosePicker)
            }
        }
    }
}

private const val ADD = "add"
private const val TITLE = "title"
private const val SEARCH = "search"
private const val NO_RESULTS = "noResults"
private const val DONE = "done"
