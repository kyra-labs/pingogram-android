package dev.kyralabs.pingogram.feature.settings.ui.privacy.exceptions

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptionSide
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.PickerAvatarRow
import dev.kyralabs.pingogram.feature.settings.ui.PickerDone
import dev.kyralabs.pingogram.feature.settings.ui.PickerSearchField
import dev.kyralabs.pingogram.feature.settings.ui.PickerTitle
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import dev.kyralabs.pingogram.feature.settings.ui.titleRes
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/** One side of a privacy setting's exceptions: hoists [PrivacyExceptionsViewModel] and shows its errors. */
@Composable
internal fun PrivacyExceptionsRoute(
    setting: PrivacySetting,
    side: PrivacyExceptionSide,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivacyExceptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker by viewModel.pickerState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PrivacyExceptionsEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    PrivacyExceptionsScreen(setting, side, state, viewModel::onEvent, onBack, modifier, snackbarHostState)
    picker?.let { PrivacyExceptionsPicker(it, viewModel::onEvent) }
}

/**
 * The users and groups a privacy setting always or never allows (`docs/DESIGN_SYSTEM.md` §6, "Privacy exceptions"): a
 * settings page titled by the side, a group headed by the setting's name with "Add users or groups" first, then each
 * user or group (40 dp avatar, name as user content, "Group" below a group) with a "Remove" [TextButton] named with it,
 * and what the side means as the footer. Changes are saved at once; the rows wait meanwhile.
 */
@Composable
internal fun PrivacyExceptionsScreen(
    setting: PrivacySetting,
    side: PrivacyExceptionSide,
    state: PrivacyExceptionsUiState,
    onEvent: (PrivacyExceptionsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(side.titleRes()),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            PrivacyExceptionsUiState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_privacy_exceptions_loading))
            }

            is PrivacyExceptionsUiState.Error -> {
                SettingsError(state.error, onRetry = { onEvent(PrivacyExceptionsEvent.Retry) })
            }

            is PrivacyExceptionsUiState.Content -> {
                ExceptionsGroup(setting, side, state, onEvent)
            }
        }
    }
}

@Composable
private fun ExceptionsGroup(
    setting: PrivacySetting,
    side: PrivacyExceptionSide,
    state: PrivacyExceptionsUiState.Content,
    onEvent: (PrivacyExceptionsEvent) -> Unit,
) {
    SettingsGroup(
        title = stringResource(setting.titleRes()),
        footer = stringResource(side.noteRes()),
    ) {
        item(key = ADD) { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_privacy_exceptions_add),
                shapes = shapes,
                icon = UiR.drawable.ic_add,
                enabled = !state.isSaving,
                onClick = { onEvent(PrivacyExceptionsEvent.OpenPicker) },
            )
        }
        state.entries.forEach { entry ->
            item(key = entry.key.itemKey) { shapes ->
                val name = entry.title.resolve()
                ExceptionRow(entry, name, shapes, enabled = !state.isSaving) {
                    onEvent(PrivacyExceptionsEvent.Remove(entry.key))
                }
            }
        }
    }
}

/** A user or group: its avatar, its [name] as user content, "Group" below a group, and "Remove" named with it. */
@Composable
private fun ExceptionRow(
    entry: ExceptionEntry,
    name: String,
    shapes: ListItemShapes,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val removeLabel = stringResource(R.string.settings_privacy_exceptions_remove_label, name.bidiIsolated())
    SegmentedListItem(
        shapes = shapes,
        colors = settingsRowColors(),
        leadingContent = {
            Avatar(photo = entry.photo, name = name, id = entry.key.id, size = MaterialTheme.sizes.leadingIconContainer)
        },
        supportingContent =
            if (entry.key.isGroup) {
                { Text(stringResource(R.string.settings_privacy_exceptions_group)) }
            } else {
                null
            },
        trailingContent = {
            TextButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = removeLabel },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.settings_privacy_exceptions_remove))
            }
        },
    ) {
        Text(name, style = LocalTextStyle.current.userContent, maxLines = 2)
    }
}

/**
 * The picker: a `ModalBottomSheet` with its title, the search bar's field, the people and groups of the chat list as
 * toggleable rows (a picked one `large` in `secondaryContainer`, its avatar turned into a check) and "Done", which
 * saves, as dismissing does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyExceptionsPicker(
    picker: ExceptionPickerUiState,
    onEvent: (PrivacyExceptionsEvent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(PrivacyExceptionsEvent.ClosePicker) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        PrivacyExceptionsPickerContent(picker, onEvent)
    }
}

@Composable
internal fun PrivacyExceptionsPickerContent(
    picker: ExceptionPickerUiState,
    onEvent: (PrivacyExceptionsEvent) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = TITLE) { PickerTitle(stringResource(R.string.settings_privacy_exceptions_add)) }
        item(key = SEARCH) {
            PickerSearchField(
                query = picker.query,
                placeholder = stringResource(R.string.settings_privacy_exceptions_search),
            ) { onEvent(PrivacyExceptionsEvent.PickerQueryChanged(it)) }
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
        items(picker.candidates, key = { it.entry.key.itemKey }) { candidate ->
            PickerAvatarRow(
                id = candidate.entry.key.id,
                title = candidate.entry.title.resolve(),
                photo = candidate.entry.photo,
                selected = candidate.selected,
                supporting =
                    if (candidate.isGroup) {
                        stringResource(
                            R.string.settings_privacy_exceptions_group,
                        )
                    } else {
                        null
                    },
            ) { onEvent(PrivacyExceptionsEvent.TogglePicked(candidate.entry.key)) }
        }
        item(key = DONE) {
            PickerDone(stringResource(R.string.settings_chat_folder_picker_done)) {
                onEvent(PrivacyExceptionsEvent.ClosePicker)
            }
        }
    }
}

internal fun PrivacyExceptionSide.titleRes(): Int =
    when (this) {
        PrivacyExceptionSide.Always -> R.string.settings_privacy_always_allowed
        PrivacyExceptionSide.Never -> R.string.settings_privacy_never_allowed
    }

private fun PrivacyExceptionSide.noteRes(): Int =
    when (this) {
        PrivacyExceptionSide.Always -> R.string.settings_privacy_exceptions_always_note
        PrivacyExceptionSide.Never -> R.string.settings_privacy_exceptions_never_note
    }

private const val ADD = "add"
private const val TITLE = "title"
private const val SEARCH = "search"
private const val NO_RESULTS = "noResults"
private const val DONE = "done"
