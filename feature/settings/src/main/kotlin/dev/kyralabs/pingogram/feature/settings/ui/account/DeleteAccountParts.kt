package dev.kyralabs.pingogram.feature.settings.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialog
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialogTexts
import dev.kyralabs.pingogram.feature.settings.ui.twostep.FloodWaitNote
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepFieldSpec
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepPasswordField
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

// The parts of the Delete account page (DeleteAccountScreen).

/** The optional reason, in the rounded tonal field: the user's own text, in its own direction (§9). */
@Composable
internal fun ReasonField(
    reason: TextFieldState,
    enabled: Boolean,
) {
    TextField(
        state = reason,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(R.string.settings_delete_account_reason)) },
        textStyle = LocalTextStyle.current.userContent,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = REASON_MAX_LINES),
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
}

/** Why the password is asked, its hint, and the password, with Telegram's refusal under it. */
@Composable
internal fun PasswordPart(
    state: DeleteAccountUiState.Content,
    password: TextFieldState,
    onEvent: (DeleteAccountEvent) -> Unit,
) {
    PageText(stringResource(R.string.settings_delete_account_password_text))
    if (state.passwordHint.isNotEmpty()) {
        PageText(stringResource(R.string.settings_two_step_hint_is, state.passwordHint.bidiIsolated()).bidiIsolated())
    }
    TwoStepPasswordField(
        state = password,
        spec =
            TwoStepFieldSpec(
                label = stringResource(R.string.settings_two_step_label_password),
                error = state.passwordError?.let { errorText(it) },
                enabled = !state.isDeleting,
                imeAction = ImeAction.Done,
                onAction = { onEvent(DeleteAccountEvent.RequestDeletion) },
            ),
        isNewPassword = false,
    )
}

/**
 * The `FLOOD_WAIT` countdown, the progress while it deletes, and "Delete account" at the end: a default [Button] in
 * the error role, as "Hang up" is, disabled until the password is typed where one is needed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeleteActions(
    state: DeleteAccountUiState.Content,
    passwordEntered: Boolean,
    onEvent: (DeleteAccountEvent) -> Unit,
) {
    state.floodWaitSeconds?.let { FloodWaitNote(seconds = it, totalSeconds = state.floodWaitTotalSeconds) }
    if (state.isDeleting) {
        val deleting = stringResource(R.string.settings_delete_account_deleting)
        LinearWavyProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = deleting })
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(
            onClick = { onEvent(DeleteAccountEvent.RequestDeletion) },
            enabled = state.canDelete(passwordEntered),
            shapes = ButtonDefaults.shapes(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
        ) {
            Text(
                stringResource(R.string.settings_delete_account),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

/** The destructive confirmation ([DestructiveDialog]). */
@Composable
internal fun DeleteConfirmation(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DestructiveDialog(
        icon = UiR.drawable.ic_delete,
        texts =
            DestructiveDialogTexts(
                title = stringResource(R.string.settings_delete_account_dialog_title),
                text = stringResource(R.string.settings_delete_account_dialog_text),
                confirm = stringResource(R.string.settings_delete_account_dialog_confirm),
            ),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** What the page says: `bodyMedium` in `onSurfaceVariant`, inset like the settings notes. */
@Composable
internal fun PageText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** A period of [days]: in months where it is one of Telegram's choices ("6 months"), in days otherwise. */
@Composable
internal fun ttlText(days: Int): String {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val months = TtlMonths[days]
    return if (months != null) {
        pluralStringResource(R.plurals.settings_delete_account_ttl_months, months, numbers.format(months))
    } else {
        pluralStringResource(R.plurals.settings_delete_account_ttl_days, days, numbers.format(days))
    }
}

/** The months of Telegram's choices, by their days ([DeleteAccountViewModel.TtlChoices]). */
private val TtlMonths = mapOf(30 to 1, 90 to 3, 180 to 6, 365 to 12, 548 to 18, 730 to 24)

private const val REASON_MAX_LINES = 4
