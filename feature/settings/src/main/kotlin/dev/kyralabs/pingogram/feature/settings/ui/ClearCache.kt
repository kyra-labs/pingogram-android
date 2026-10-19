package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.feature.settings.R
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
