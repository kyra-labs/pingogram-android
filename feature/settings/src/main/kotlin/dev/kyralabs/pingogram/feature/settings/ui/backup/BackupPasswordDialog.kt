package dev.kyralabs.pingogram.feature.settings.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R

/**
 * The backup's password (`docs/DESIGN_SYSTEM.md` §6 "Backup & restore"): set at the folder's first backup
 * ([PasswordDialog.New], typed twice, with why it matters), asked for after ([PasswordDialog.Existing]). "Back up" is
 * enabled once the password has [MIN_PASSWORD_LENGTH] characters and, when set, is typed alike twice. What is typed
 * stays in this dialog only (not saved across process death) until [onConfirm] takes it.
 */
@Composable
internal fun BackupPasswordDialog(
    dialog: PasswordDialog,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val password = rememberTextFieldState()
    val repeated = rememberTextFieldState()
    val typed = password.text.toString()
    val mismatch = dialog == PasswordDialog.New && repeated.text.isNotEmpty() && repeated.text.toString() != typed
    val canConfirm =
        typed.length >= MIN_PASSWORD_LENGTH && (dialog == PasswordDialog.Existing || repeated.text.toString() == typed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (dialog ==
                        PasswordDialog.New
                    ) {
                        R.string.backup_password_new
                    } else {
                        R.string.backup_password
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                if (dialog == PasswordDialog.New) {
                    Text(stringResource(R.string.backup_password_new_body), style = MaterialTheme.typography.bodyMedium)
                }
                PasswordField(password, stringResource(R.string.backup_password_field), dialog == PasswordDialog.New)
                if (dialog == PasswordDialog.New) {
                    PasswordField(
                        state = repeated,
                        label = stringResource(R.string.backup_password_repeat),
                        isNew = true,
                        error = stringResource(R.string.backup_password_mismatch).takeIf { mismatch },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(typed) }, enabled = canConfirm) {
                Text(stringResource(R.string.backup_password_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun PasswordField(
    state: TextFieldState,
    label: String,
    isNew: Boolean,
    error: String? = null,
) {
    SecureTextField(
        state = state,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentType = if (isNew) ContentType.NewPassword else ContentType.Password },
        label = { Text(label) },
        supportingText = error?.let { { Text(it) } },
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
}

@PingogramPreviews
@Composable
private fun NewPasswordPreview() {
    PingogramPreview { BackupPasswordDialog(PasswordDialog.New, onConfirm = {}, onDismiss = {}) }
}

@PingogramPreviews
@Composable
private fun PasswordPreview() {
    PingogramPreview { BackupPasswordDialog(PasswordDialog.Existing, onConfirm = {}, onDismiss = {}) }
}
