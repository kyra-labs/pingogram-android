package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
        colors = destructiveItemColors(),
    ) {
        Text(stringResource(R.string.settings_log_out))
    }
}

/** The colors of a destructive row in a [SettingsSection]: its text and icon in the error role, on the card. */
@Composable
internal fun destructiveItemColors() =
    ListItemDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.error,
        leadingContentColor = MaterialTheme.colorScheme.error,
    )

/** The destructive confirmation ([DestructiveDialog]). */
@Composable
internal fun LogOutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DestructiveDialog(
        icon = R.drawable.ic_logout,
        texts =
            DestructiveDialogTexts(
                title = stringResource(R.string.settings_log_out_dialog_title),
                text = stringResource(R.string.settings_log_out_dialog_text),
                confirm = stringResource(R.string.settings_log_out_dialog_confirm),
            ),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
