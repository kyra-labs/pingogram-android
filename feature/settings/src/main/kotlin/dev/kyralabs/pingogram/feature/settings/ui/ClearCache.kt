package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.network.DataOptionsEffect
import dev.kyralabs.pingogram.feature.settings.ui.network.DataOptionsEvent
import dev.kyralabs.pingogram.feature.settings.ui.network.DataOptionsUiState
import dev.kyralabs.pingogram.feature.settings.ui.network.DataOptionsViewModel
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

// The confirmations of the storage card's "Clear cache" and of "Delete cloud drafts" (Privacy and security), and
// "Delete cloud drafts" itself.

/** The destructive confirmation ([DestructiveDialog]): the files must download again. */
@Composable
internal fun ClearCacheDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DestructiveDialog(
        icon = UiR.drawable.ic_delete,
        texts =
            DestructiveDialogTexts(
                title = stringResource(R.string.settings_clear_cache_dialog_title),
                text = stringResource(R.string.settings_clear_cache_dialog_text),
                confirm = stringResource(R.string.settings_clear_cache_dialog_confirm),
            ),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * "Delete cloud drafts", a destructive row: the drafts of every chat but secret chats, on all the user's devices; a
 * `LoadingIndicator` at its end while they are deleted.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeleteDraftsItem(
    isDeleting: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    val deleting = stringResource(R.string.settings_delete_drafts_running)
    SettingsRow(
        title = stringResource(R.string.settings_delete_drafts),
        shapes = shapes,
        icon = UiR.drawable.ic_edit_filled,
        supportingContent = { Text(stringResource(R.string.settings_delete_drafts_summary)) },
        trailingContent =
            if (isDeleting) {
                { LoadingIndicator(Modifier.semantics { contentDescription = deleting }) }
            } else {
                null
            },
        onClick = onClick,
        enabled = !isDeleting,
        colors = destructiveItemColors(),
        iconTone = SettingsIconTone.Error,
    )
}

/** The destructive confirmation ([DestructiveDialog]): the drafts cannot come back. */
@Composable
internal fun DeleteDraftsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DestructiveDialog(
        icon = UiR.drawable.ic_delete,
        texts =
            DestructiveDialogTexts(
                title = stringResource(R.string.settings_delete_drafts_dialog_title),
                text = stringResource(R.string.settings_delete_drafts_dialog_text),
                confirm = stringResource(R.string.settings_delete_drafts_dialog_confirm),
            ),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** [DeleteDraftsDialog] while [drafts] says it is open, its answers going to [onEvent]. */
@Composable
internal fun DeleteDraftsDialogHost(
    drafts: DataOptionsUiState,
    onEvent: (DataOptionsEvent) -> Unit,
) {
    if (drafts.isDeleteDraftsDialogOpen) {
        DeleteDraftsDialog(
            onConfirm = { onEvent(DataOptionsEvent.ConfirmDeleteDrafts) },
            onDismiss = { onEvent(DataOptionsEvent.DismissDeleteDrafts) },
        )
    }
}

/** Shows what [viewModel] did in snackbars of [snackbarHostState]: the deleted drafts, or why something failed. */
@Composable
internal fun DataOptionsSnackbars(
    viewModel: DataOptionsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    DataOptionsEffect.DraftsDeleted -> resources.getString(R.string.settings_delete_drafts_done)
                    is DataOptionsEffect.ShowError -> errorMessage(resources, effect.error, showErrorDetails)
                }
            // Snackbars queue up; later effects must not wait for them.
            launch { snackbarHostState.showSnackbar(message) }
        }
    }
}
