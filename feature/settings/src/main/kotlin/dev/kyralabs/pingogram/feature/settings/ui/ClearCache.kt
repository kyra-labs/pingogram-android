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

// "Clear cache" on the Data and storage page and its confirmation.

/** "Clear cache"; disabled with progress while clearing. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ClearCacheItem(
    isClearing: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    val clearing = stringResource(R.string.settings_clearing_cache)
    SettingsRow(
        title = stringResource(R.string.settings_clear_cache),
        shapes = shapes,
        icon = UiR.drawable.ic_delete,
        supportingContent = { Text(stringResource(R.string.settings_clear_cache_summary)) },
        trailingContent =
            if (isClearing) {
                { LoadingIndicator(Modifier.semantics { contentDescription = clearing }) }
            } else {
                null
            },
        onClick = onClick,
        enabled = !isClearing,
    )
}

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
        icon = UiR.drawable.ic_edit,
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

/** "Cache": "Clear cache" and "Delete cloud drafts", each asking first ([CacheDialogs]). */
@Composable
internal fun CacheGroup(
    state: DataStorageUiState,
    options: DataOptionsUiState,
    onEvent: (DataStorageEvent) -> Unit,
    onOptionsEvent: (DataOptionsEvent) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_section_cache), iconTone = SettingsIconTone.Tertiary) {
        item { shapes ->
            ClearCacheItem(
                isClearing = state.isClearingCache,
                shapes = shapes,
                onClick = { onEvent(DataStorageEvent.ClearCache) },
            )
        }
        item { shapes ->
            DeleteDraftsItem(
                isDeleting = options.isDeletingDrafts,
                shapes = shapes,
                onClick = { onOptionsEvent(DataOptionsEvent.DeleteDrafts) },
            )
        }
    }
}

/** The questions before clearing the cache and before deleting the cloud drafts, while either is open. */
@Composable
internal fun CacheDialogs(
    state: DataStorageUiState,
    options: DataOptionsUiState,
    onEvent: (DataStorageEvent) -> Unit,
    onOptionsEvent: (DataOptionsEvent) -> Unit,
) {
    if (state.isClearCacheDialogOpen) {
        ClearCacheDialog(
            onConfirm = { onEvent(DataStorageEvent.ConfirmClearCache) },
            onDismiss = { onEvent(DataStorageEvent.DismissClearCache) },
        )
    }
    if (options.isDeleteDraftsDialogOpen) {
        DeleteDraftsDialog(
            onConfirm = { onOptionsEvent(DataOptionsEvent.ConfirmDeleteDrafts) },
            onDismiss = { onOptionsEvent(DataOptionsEvent.DismissDeleteDrafts) },
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
