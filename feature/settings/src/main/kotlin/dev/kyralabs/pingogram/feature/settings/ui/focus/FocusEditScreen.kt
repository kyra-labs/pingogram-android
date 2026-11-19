package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialog
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialogTexts
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsIconTone
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.destructiveItemColors
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Focus profile editor: hoists [FocusEditViewModel], names a profile started from [template] after it, closes once
 * the profile is saved or deleted ([onBack], which also leaves it), buzzes when "Save" is refused, and shows why a
 * change was not saved in a snackbar.
 */
@Composable
internal fun FocusEditRoute(
    template: FocusTemplate?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FocusEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val chatPicker by viewModel.chatPicker.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val showErrorDetails = isDebuggable()
    val templateName = template?.let { stringResource(it.title()) }
    LaunchedEffect(viewModel) {
        templateName?.let { viewModel.onEvent(FocusEditEvent.Prefill(it)) }
        viewModel.effects.collect { effect ->
            when (effect) {
                FocusEditEffect.Done -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onBack()
                }

                FocusEditEffect.Rejected -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                }

                is FocusEditEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    FocusEditScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
    chatPicker?.let { FocusChatPicker(it, viewModel::onEvent) }
}

/**
 * Editing a Focus profile (`docs/DESIGN_SYSTEM.md` §13.13, board 31), a settings page with "Save" in its top bar: the
 * name, the icon, "Allow folders", "Also allow" chats, when it turns on (on a schedule of days and times, or by
 * hand), "Allow calls from contacts", and for a saved profile "Delete profile", behind an `AlertDialog`.
 */
@Composable
internal fun FocusEditScreen(
    state: FocusEditUiState,
    onEvent: (FocusEditEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title =
            stringResource(if (state.isNew) R.string.settings_focus_new_title else R.string.settings_focus_edit),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = {
            Button(
                onClick = { onEvent(FocusEditEvent.Save) },
                enabled = state.draft != null && !state.isBusy,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    stringResource(R.string.settings_focus_save),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) {
        val draft = state.draft ?: return@SettingsPageScaffold
        val enabled = !state.isBusy
        FocusNameField(state, draft, onEvent)
        FocusIconGroup(draft, enabled, onEvent)
        FocusFoldersGroup(state, draft, onEvent)
        FocusChatsGroup(state, onEvent)
        FocusScheduleGroup(draft, enabled, onEvent)
        FocusCallsGroup(draft, onEvent)
        if (!state.isNew) FocusDeleteRow(enabled, onEvent)
        state.picking?.let { end ->
            FocusTimeDialog(end, if (end == FocusTimeEnd.Start) draft.startMinute else draft.endMinute, onEvent)
        }
    }
    if (state.askDelete) FocusDeleteDialog(onEvent)
}

/** "Delete profile", a destructive row. */
@Composable
private fun FocusDeleteRow(
    enabled: Boolean,
    onEvent: (FocusEditEvent) -> Unit,
) {
    SettingsGroup {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_focus_delete),
                shapes = shapes,
                icon = UiR.drawable.ic_delete_filled,
                iconTone = SettingsIconTone.Error,
                colors = destructiveItemColors(),
                onClick = { onEvent(FocusEditEvent.Delete) },
                enabled = enabled,
            )
        }
    }
}

/** The question before deleting the profile. */
@Composable
private fun FocusDeleteDialog(onEvent: (FocusEditEvent) -> Unit) {
    DestructiveDialog(
        icon = UiR.drawable.ic_delete,
        texts =
            DestructiveDialogTexts(
                title = stringResource(R.string.settings_focus_delete_title),
                text = stringResource(R.string.settings_focus_delete_text),
                confirm = stringResource(R.string.settings_focus_delete_confirm),
            ),
        onConfirm = { onEvent(FocusEditEvent.DeleteConfirmed) },
        onDismiss = { onEvent(FocusEditEvent.DeleteDismissed) },
    )
}
