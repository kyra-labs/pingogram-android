package dev.kyralabs.pingogram.feature.profile.chatinfo

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import dev.kyralabs.pingogram.core.domain.model.OwnershipTransferCheck
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Transferring the ownership (`docs/DESIGN_SYSTEM.md` §6, "Member rights and group permissions"): a destructive
 * `AlertDialog` that says what changes, with the 2-step verification password in a filled `SecureTextField`, why the
 * last try failed below it (a polite live region, with a `Reject` haptic), a `LinearWavyProgressIndicator` while
 * Telegram answers, "Cancel" and "Transfer" in `error`.
 */
@Composable
internal fun TransferOwnershipDialog(
    dialog: TransferDialog,
    password: TextFieldState,
    onEvent: (MemberRightsEvent) -> Unit,
) {
    // Isolated, so a name in another script keeps its direction inside the sentence (`docs/DESIGN_SYSTEM.md` §9).
    val name = dialog.name.resolve().bidiIsolated()
    val haptics = LocalHapticFeedback.current
    // The password is what the dialog asks for: its field takes the focus, and the keyboard comes up.
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    LaunchedEffect(dialog.error) {
        if (dialog.error != null) haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }
    AlertDialog(
        onDismissRequest = { if (!dialog.isTransferring) onEvent(MemberRightsEvent.DismissTransfer) },
        icon = { Icon(painterResource(UiR.drawable.ic_key), contentDescription = null) },
        title = { Text(stringResource(R.string.chat_info_transfer_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                val text =
                    if (dialog.isChannel) R.string.chat_info_transfer_text_channel else R.string.chat_info_transfer_text
                Text(stringResource(text, name))
                SecureTextField(
                    state = password,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    enabled = !dialog.isTransferring,
                    label = { Text(stringResource(R.string.chat_info_transfer_password)) },
                    isError = dialog.error != null,
                    supportingText =
                        dialog.error?.let { error ->
                            {
                                Text(
                                    errorText(error),
                                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (dialog.isTransferring) SavingIndicator()
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(MemberRightsEvent.ConfirmTransfer) },
                enabled = !dialog.isTransferring && password.text.isNotEmpty(),
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.chat_info_transfer_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(MemberRightsEvent.DismissTransfer) },
                enabled = !dialog.isTransferring,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.profile_dialog_dismiss))
            }
        },
    )
}

/** Why the ownership cannot be transferred now, for a snackbar. */
internal fun transferUnavailableText(
    resources: Resources,
    check: OwnershipTransferCheck,
): String =
    when (check) {
        OwnershipTransferCheck.Allowed, OwnershipTransferCheck.PasswordNeeded -> {
            resources.getString(R.string.chat_info_transfer_password_needed)
        }

        is OwnershipTransferCheck.PasswordTooNew -> {
            val wait = durationText(resources, check.retryAfterSeconds.toLong())
            resources.getString(R.string.chat_info_transfer_password_too_new, wait)
        }

        is OwnershipTransferCheck.SessionTooNew -> {
            val wait = durationText(resources, check.retryAfterSeconds.toLong())
            resources.getString(R.string.chat_info_transfer_session_too_new, wait)
        }
    }
