package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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

// "Log out" at the end of Settings and its confirmation (REQUIREMENTS.md FR-A8).

/** Destructive, so in the error role; disabled with progress while logging out. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LogOutItem(
    isLoggingOut: Boolean,
    onClick: () -> Unit,
) {
    val loggingOut = stringResource(R.string.settings_logging_out)
    ListItem(
        onClick = onClick,
        enabled = !isLoggingOut,
        leadingContent = { Icon(painter = painterResource(R.drawable.ic_logout), contentDescription = null) },
        trailingContent =
            if (isLoggingOut) {
                { LoadingIndicator(Modifier.semantics { contentDescription = loggingOut }) }
            } else {
                null
            },
        supportingContent = { Text(stringResource(R.string.settings_log_out_summary)) },
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.error,
                leadingContentColor = MaterialTheme.colorScheme.error,
            ),
    ) {
        Text(stringResource(R.string.settings_log_out))
    }
}

/** The destructive confirmation (`docs/DESIGN_SYSTEM.md` §6: `AlertDialog` for destructive confirmations only). */
@Composable
internal fun LogOutDialog(
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
                    text = stringResource(R.string.settings_log_out_dialog_confirm),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.settings_log_out_dialog_dismiss),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        icon = { Icon(painter = painterResource(R.drawable.ic_logout), contentDescription = null) },
        title = { Text(stringResource(R.string.settings_log_out_dialog_title)) },
        text = { Text(stringResource(R.string.settings_log_out_dialog_text)) },
    )
}
