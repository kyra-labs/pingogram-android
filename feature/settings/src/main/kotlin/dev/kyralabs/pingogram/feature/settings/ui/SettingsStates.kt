package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R

// What settings pages show while something loads, after it failed, and before something destructive.

/** Something loading: a [LoadingIndicator] that screen readers announce as [description]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsLoading(
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

/** Why something could not be loaded, announced politely to screen readers, and a button to try again. */
@Composable
internal fun SettingsError(
    error: DomainError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Text(
            text = errorText(error),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        FilledTonalButton(onClick = onRetry) {
            Text(
                text = stringResource(R.string.settings_retry),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

/** What a [DestructiveDialog] says: its [title], what will happen ([text]), and the label of its [confirm] button. */
@Immutable
internal data class DestructiveDialogTexts(
    val title: String,
    val text: String,
    val confirm: String,
)

/**
 * A destructive confirmation (`docs/DESIGN_SYSTEM.md` §6: `AlertDialog` for destructive confirmations only): [icon],
 * if any, and [texts], with the confirm button in the error role next to "Cancel".
 */
@Composable
internal fun DestructiveDialog(
    @DrawableRes icon: Int?,
    texts: DestructiveDialogTexts,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismiss = stringResource(R.string.settings_dialog_dismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(text = texts.confirm, style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismiss, style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
        icon = icon?.let { { Icon(painter = painterResource(it), contentDescription = null) } },
        title = { Text(texts.title) },
        text = { Text(texts.text) },
    )
}
