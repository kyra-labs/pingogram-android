package dev.kyralabs.pingogram.feature.settings.ui.shownfirst

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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.repository.ChatsShownFirstRepository
import dev.kyralabs.pingogram.core.ui.component.Avatar
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

/** Chats shown first: hoists [ChatsShownFirstViewModel]. */
@Composable
internal fun ChatsShownFirstRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsShownFirstViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker by viewModel.pickerState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatsShownFirstEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    ChatsShownFirstScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
    picker?.let { ShownFirstPicker(it, viewModel::onEvent) }
}

/**
 * The Chats shown first page (`docs/DESIGN_SYSTEM.md` §6 "Chats shown first"): a settings page with "Add a chat" first
 * (disabled once [ChatsShownFirstRepository.MAX] are there), then each chat in its order (40 dp avatar, title as user
 * content) with a "Remove" [TextButton] named with it, "Move up" and "Move down" for screen readers, and a footer
 * saying where they show.
 */
@Composable
internal fun ChatsShownFirstScreen(
    state: ChatsShownFirstUiState,
    onEvent: (ChatsShownFirstEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.shown_first_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            ChatsShownFirstUiState.Loading -> SettingsLoading(stringResource(R.string.shown_first_loading))
            is ChatsShownFirstUiState.Content -> ShownFirstGroup(state, onEvent)
        }
    }
}

@Composable
private fun ShownFirstGroup(
    state: ChatsShownFirstUiState.Content,
    onEvent: (ChatsShownFirstEvent) -> Unit,
) {
    val max = ChatsShownFirstRepository.MAX
    SettingsGroup(footer = pluralStringResource(R.plurals.shown_first_footer, max, max)) {
        item(key = ADD) { shapes ->
            SettingsRow(
                title = stringResource(R.string.shown_first_add),
                shapes = shapes,
                icon = UiR.drawable.ic_add,
                enabled = state.canAdd && !state.isSaving,
                onClick = { onEvent(ChatsShownFirstEvent.OpenPicker) },
            )
        }
        state.chats.forEachIndexed { index, chat ->
            item(key = chat.id) { shapes ->
                ShownFirstItem(
                    chat = chat,
                    shapes = shapes,
                    enabled = !state.isSaving,
                    isFirst = index == 0,
                    isLast = index == state.chats.lastIndex,
                    onEvent = onEvent,
                )
            }
        }
    }
}

/**
 * A chat shown first: its avatar, its title as user content, "Remove" named with it, and moving it for screen readers.
 */
@Suppress("LongParameterList") // The chat, its place among the others, the row's look and its events.
@Composable
private fun ShownFirstItem(
    chat: ShownFirstRow,
    shapes: ListItemShapes,
    enabled: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onEvent: (ChatsShownFirstEvent) -> Unit,
) {
    val removeLabel = stringResource(R.string.shown_first_remove_label, chat.title.bidiIsolated())
    val moveUp = stringResource(R.string.settings_chat_folders_move_up)
    val moveDown = stringResource(R.string.settings_chat_folders_move_down)
    SegmentedListItem(
        modifier =
            Modifier.semantics {
                customActions =
                    listOfNotNull(
                        CustomAccessibilityAction(moveUp) {
                            onEvent(ChatsShownFirstEvent.Move(chat.id, up = true))
                            true
                        }.takeIf { !isFirst && enabled },
                        CustomAccessibilityAction(moveDown) {
                            onEvent(ChatsShownFirstEvent.Move(chat.id, up = false))
                            true
                        }.takeIf { !isLast && enabled },
                    )
            },
        shapes = shapes,
        colors = settingsRowColors(),
        leadingContent = {
            Avatar(photo = chat.photo, name = chat.title, id = chat.id, size = MaterialTheme.sizes.leadingIconContainer)
        },
        trailingContent = {
            TextButton(
                onClick = { onEvent(ChatsShownFirstEvent.Remove(chat.id)) },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = removeLabel },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.shown_first_remove))
            }
        },
    ) {
        Text(
            chat.title,
            style = LocalTextStyle.current.userContent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The picker of chats to show first: a `ModalBottomSheet` with its title ("Choose up to 3 chats"), the search bar's
 * field, the main list's chats as toggleable rows (a picked one `large` in `secondaryContainer`, its avatar turned
 * into a check) and "Done", which adds them, as dismissing does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShownFirstPicker(
    picker: ShownFirstPickerUiState,
    onEvent: (ChatsShownFirstEvent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(ChatsShownFirstEvent.ClosePicker) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ShownFirstPickerContent(picker, onEvent)
    }
}

@Composable
internal fun ShownFirstPickerContent(
    picker: ShownFirstPickerUiState,
    onEvent: (ChatsShownFirstEvent) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = TITLE) {
            PickerTitle(pluralStringResource(R.plurals.shown_first_picker_title, picker.slots, picker.slots))
        }
        item(key = SEARCH) {
            PickerSearchField(
                query = picker.query,
                placeholder = stringResource(R.string.settings_privacy_exceptions_search),
            ) { onEvent(ChatsShownFirstEvent.PickerQueryChanged(it)) }
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
            ) { onEvent(ChatsShownFirstEvent.TogglePicked(candidate.id)) }
        }
        item(key = DONE) {
            PickerDone(stringResource(R.string.settings_chat_folder_picker_done)) {
                onEvent(ChatsShownFirstEvent.ClosePicker)
            }
        }
    }
}

private const val ADD = "add"
private const val TITLE = "title"
private const val SEARCH = "search"
private const val NO_RESULTS = "noResults"
private const val DONE = "done"
