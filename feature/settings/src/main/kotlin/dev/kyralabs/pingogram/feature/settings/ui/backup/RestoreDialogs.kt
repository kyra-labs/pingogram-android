package dev.kyralabs.pingogram.feature.settings.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Restoring a backup (`docs/DESIGN_SYSTEM.md` §6 "Backup & restore"): its password ([RestoreStep.Password], "Wrong
 * password" under the field after one that did not open it, a loading indicator while it is tried), then what it holds
 * ([RestoreStep.Summary]) with "Restore".
 */
@Composable
internal fun RestoreDialog(
    step: RestoreStep,
    onEvent: (BackupEvent) -> Unit,
) {
    when (step) {
        is RestoreStep.Password -> RestorePasswordDialog(step, onEvent)
        is RestoreStep.Summary -> RestoreSummaryDialog(step, onEvent)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RestorePasswordDialog(
    step: RestoreStep.Password,
    onEvent: (BackupEvent) -> Unit,
) {
    val password = rememberTextFieldState()
    val error = stringResource(R.string.backup_restore_wrong_password).takeIf { step.wrongPassword }
    AlertDialog(
        onDismissRequest = { onEvent(BackupEvent.DismissRestore) },
        title = { Text(stringResource(R.string.backup_restore_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                Text(stringResource(R.string.backup_restore_password_body), style = MaterialTheme.typography.bodyMedium)
                SecureTextField(
                    state = password,
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                    enabled = !step.opening,
                    label = { Text(stringResource(R.string.backup_password_field)) },
                    supportingText = error?.let { { Text(it) } },
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    shape = TextFieldDefaults.roundedShape,
                    colors = TextFieldDefaults.tonalColors(),
                )
                if (step.opening) LoadingIndicator()
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(BackupEvent.OpenBackup(password.text.toString())) },
                enabled = !step.opening && password.text.isNotEmpty(),
            ) {
                Text(stringResource(R.string.backup_restore_open))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(BackupEvent.DismissRestore) },
            ) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RestoreSummaryDialog(
    step: RestoreStep.Summary,
    onEvent: (BackupEvent) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val date =
        DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(step.createdAt.atZone(ZoneId.systemDefault()))
    AlertDialog(
        onDismissRequest = { if (!step.restoring) onEvent(BackupEvent.DismissRestore) },
        title = { Text(stringResource(R.string.backup_restore_summary_title, date)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                if (step.settings) Text(stringResource(R.string.backup_restore_settings))
                step.accounts.forEach { Text(accountText(it)) }
                if (step.restoring) LoadingIndicator()
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(BackupEvent.ConfirmRestore) }, enabled = !step.restoring) {
                Text(stringResource(R.string.backup_restore_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(BackupEvent.DismissRestore) }, enabled = !step.restoring) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/** "Ada: 42 bookmarks, 5 reminders, 3 keyword alerts, 2 snippets", or what stays in the backup for another account. */
@Composable
private fun accountText(account: AccountSummary): String {
    val counts =
        listOf(
            pluralStringResource(R.plurals.backup_restore_bookmarks, account.bookmarks, account.bookmarks),
            pluralStringResource(R.plurals.backup_restore_reminders, account.reminders, account.reminders),
            pluralStringResource(R.plurals.backup_restore_keyword_hits, account.keywordHits, account.keywordHits),
            pluralStringResource(R.plurals.backup_restore_snippets, account.snippets, account.snippets),
        ).joinToString(", ")
    return account.name?.let { stringResource(R.string.backup_restore_account, it, counts) }
        ?: stringResource(R.string.backup_restore_other_account, counts)
}

@PingogramPreviews
@Composable
private fun RestorePasswordPreview() {
    PingogramPreview { RestoreDialog(RestoreStep.Password(wrongPassword = true), onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun RestoreSummaryPreview() {
    PingogramPreview {
        RestoreDialog(
            RestoreStep.Summary(
                createdAt = Instant.parse("2026-11-12T09:00:00Z"),
                settings = true,
                accounts =
                    persistentListOf(
                        AccountSummary("Ada Lovelace", bookmarks = 42, reminders = 5, keywordHits = 3, snippets = 2),
                        AccountSummary(null, bookmarks = 7, reminders = 0, keywordHits = 0, snippets = 1),
                    ),
            ),
            onEvent = {},
        )
    }
}
