package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.feature.settings.R

// "Log out" at the end of Settings and its confirmation (REQUIREMENTS.md FR-A8); "Leave demo" in its place while the
// demo shows (docs/adr/0034-demo-mode-for-store-review.md).

/** Destructive, so in the error role; disabled with progress while logging out. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LogOutItem(
    isLoggingOut: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    isDemo: Boolean = false,
) {
    val loggingOut = stringResource(R.string.settings_logging_out)
    SettingsRow(
        title = stringResource(if (isDemo) R.string.settings_leave_demo else R.string.settings_log_out),
        shapes = shapes,
        icon = R.drawable.ic_logout,
        supportingContent = {
            Text(
                stringResource(if (isDemo) R.string.settings_leave_demo_summary else R.string.settings_log_out_summary),
            )
        },
        trailingContent =
            if (isLoggingOut) {
                { LoadingIndicator(Modifier.semantics { contentDescription = loggingOut }) }
            } else {
                null
            },
        onClick = onClick,
        enabled = !isLoggingOut,
        colors = destructiveItemColors(),
        iconTone = SettingsIconTone.Error,
    )
}

/**
 * The colors of a destructive row in a [SettingsGroup]: its text in the error role, on its container; its icon, if
 * any, takes [SettingsIconTone.Error]. The container takes a tint of the error role too (`docs/DESIGN_SYSTEM.md`
 * §13.10), so the row reads as a warning before its text is read.
 */
@Composable
internal fun destructiveItemColors(): ListItemColors {
    val error = MaterialTheme.colorScheme.error
    val container = settingsRowContainerColor()
    return settingsRowColors(
        contentColor = error,
        leadingContentColor = error,
        containerColor = error.copy(alpha = DESTRUCTIVE_TINT).compositeOver(container),
    )
}

/** How much of the error role tints a destructive row's container. */
private const val DESTRUCTIVE_TINT = 0.12f

/** The destructive confirmation ([DestructiveDialog]). */
@Composable
internal fun LogOutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDemo: Boolean = false,
) {
    DestructiveDialog(
        icon = R.drawable.ic_logout,
        texts =
            if (isDemo) {
                DestructiveDialogTexts(
                    title = stringResource(R.string.settings_leave_demo_dialog_title),
                    text = stringResource(R.string.settings_leave_demo_dialog_text),
                    confirm = stringResource(R.string.settings_leave_demo_dialog_confirm),
                )
            } else {
                DestructiveDialogTexts(
                    title = stringResource(R.string.settings_log_out_dialog_title),
                    text = stringResource(R.string.settings_log_out_dialog_text),
                    confirm = stringResource(R.string.settings_log_out_dialog_confirm),
                )
            },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
