package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
    onClick: () -> Unit,
) {
    val clearing = stringResource(R.string.settings_clearing_cache)
    ListItem(
        onClick = onClick,
        enabled = !isClearing,
        leadingContent = { SettingsItemIcon(UiR.drawable.ic_delete) },
        trailingContent =
            if (isClearing) {
                { LoadingIndicator(Modifier.semantics { contentDescription = clearing }) }
            } else {
                null
            },
        supportingContent = { Text(stringResource(R.string.settings_clear_cache_summary)) },
        colors = settingsItemColors(),
    ) {
        Text(stringResource(R.string.settings_clear_cache))
    }
}

/**
 * The destructive confirmation (`docs/DESIGN_SYSTEM.md` §6: `AlertDialog` for destructive confirmations only): the
 * files must download again.
 */
@Composable
internal fun ClearCacheDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    text = stringResource(R.string.settings_clear_cache_dialog_confirm),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.settings_clear_cache_dialog_dismiss),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        icon = { Icon(painter = painterResource(UiR.drawable.ic_delete), contentDescription = null) },
        title = { Text(stringResource(R.string.settings_clear_cache_dialog_title)) },
        text = { Text(stringResource(R.string.settings_clear_cache_dialog_text)) },
    )
}
