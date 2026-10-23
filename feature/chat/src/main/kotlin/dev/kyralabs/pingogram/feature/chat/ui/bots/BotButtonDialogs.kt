package dev.kyralabs.pingogram.feature.chat.ui.bots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.BotButtonEvent
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotButtonsPreviewData

// What shows over the conversation after a bot's button was tapped (docs/DESIGN_SYSTEM.md §6 "Bot buttons").

/**
 * [dialog] over the conversation, its answers going to [onEvent]:
 *
 * - **Alert**: the bot's text in an `AlertDialog` with "OK".
 * - **Link**: "Open this link?" with the link (left to right, selectable) in an `AlertDialog`, "Cancel" and "Open":
 *   the link the button's text hides.
 * - **Login**: "Open example.org?" in an `AlertDialog`, what logging in shares, "Log in as …" and, where the bot asks,
 *   "Allow @bot to send me messages" as checkbox rows (both on at first, the second only while the first is), a
 *   `LinearWavyProgressIndicator` while Telegram makes the link, "Cancel" and "Open".
 * - **Password**: [BotPasswordSheet], with the field's [password].
 *
 * Dialogs are kept for questions the user must answer before something leaves the app (a link, a login), as for
 * destructive ones; errors stay snackbars.
 */
@Composable
internal fun BotButtonDialogHost(
    dialog: BotButtonDialog,
    password: TextFieldState,
    onEvent: (BotButtonEvent) -> Unit,
) {
    val dismiss = { onEvent(BotButtonEvent.DismissDialog) }
    when (dialog) {
        is BotButtonDialog.Alert -> {
            AlertDialog(
                onDismissRequest = dismiss,
                confirmButton = {
                    TextButton(onClick = dismiss, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.bot_alert_ok))
                    }
                },
                text = {
                    Text(
                        text = dialog.text,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium.userContent,
                    )
                },
            )
        }

        is BotButtonDialog.OpenLink -> {
            AlertDialog(
                onDismissRequest = dismiss,
                confirmButton = {
                    TextButton(onClick = { onEvent(BotButtonEvent.OpenLink) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.bot_link_open))
                    }
                },
                dismissButton = { CancelButton(dismiss) },
                title = { Text(stringResource(R.string.bot_link_title)) },
                text = {
                    SelectionContainer {
                        Text(text = dialog.url, style = MaterialTheme.typography.bodyMedium.numericContent)
                    }
                },
            )
        }

        is BotButtonDialog.Login -> {
            LoginDialog(dialog, onEvent)
        }

        is BotButtonDialog.Password -> {
            BotPasswordSheet(dialog, password, onEvent)
        }
    }
}

/** "Open example.org?", whether to log in there and let the bot write, "Cancel" and "Open". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LinearWavyProgressIndicator
@Composable
private fun LoginDialog(
    dialog: BotButtonDialog.Login,
    onEvent: (BotButtonEvent) -> Unit,
) {
    var logIn by rememberSaveable { mutableStateOf(true) }
    var allowWrite by rememberSaveable { mutableStateOf(true) }
    val botName = dialog.botName.resolve()
    AlertDialog(
        onDismissRequest = { onEvent(BotButtonEvent.DismissDialog) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(BotButtonEvent.Login(logIn, allowWrite)) },
                enabled = !dialog.isLoggingIn,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.bot_link_open))
            }
        },
        dismissButton = { CancelButton { onEvent(BotButtonEvent.DismissDialog) } },
        title = { Text(stringResource(R.string.bot_login_title, dialog.domain)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Text(
                    text = stringResource(R.string.bot_login_text, botName, dialog.domain),
                    style = MaterialTheme.typography.bodyMedium,
                )
                CheckboxRow(
                    text = stringResource(R.string.bot_login_as, dialog.myName.resolve()),
                    checked = logIn,
                    enabled = !dialog.isLoggingIn,
                    onCheckedChange = { logIn = it },
                )
                if (dialog.requestWriteAccess) {
                    CheckboxRow(
                        text = stringResource(R.string.bot_login_write, botName),
                        checked = logIn && allowWrite,
                        enabled = logIn && !dialog.isLoggingIn,
                        onCheckedChange = { allowWrite = it },
                    )
                }
                if (dialog.isLoggingIn) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
        },
    )
}

/** A checkbox with its [text], the whole row toggling it, at least 48 dp tall. */
@Composable
private fun CheckboxRow(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(text = text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CancelButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.bot_dialog_cancel)) }
}

/**
 * The 2-step verification password a bot's button needs: a [ModalBottomSheet] with "Enter your password"
 * (`titleLargeEmphasized`), which bot asks (`bodyMedium` `onSurfaceVariant`), the password in a filled
 * `SecureTextField` that takes the focus, why the last try failed below it (with a `Reject` haptic, a polite live
 * region), a `LinearWavyProgressIndicator` while Telegram checks it, "Cancel" at the start and a default "Confirm"
 * button at the end, both with `ButtonDefaults.shapes()`. The keyboard's done key confirms too. Dismissing it in any
 * way cancels the check ([BotButtonEvent.DismissDialog]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class) // ModalBottomSheet, LinearWavy…
@Composable
internal fun BotPasswordSheet(
    dialog: BotButtonDialog.Password,
    password: TextFieldState,
    onEvent: (BotButtonEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(BotButtonEvent.DismissDialog) }, sheetState = sheetState) {
        BotPasswordContent(dialog, password, onEvent)
    }
}

/** What the password sheet shows. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LinearWavyProgressIndicator
@Composable
internal fun BotPasswordContent(
    dialog: BotButtonDialog.Password,
    password: TextFieldState,
    onEvent: (BotButtonEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val haptics = LocalHapticFeedback.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    LaunchedEffect(dialog.error) {
        if (dialog.error != null) haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(
            text = stringResource(R.string.bot_password_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Text(
            text = stringResource(R.string.bot_password_text, dialog.botName.resolve()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        val error = dialog.error?.let { errorText(it) }
        SecureTextField(
            state = password,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .semantics { contentType = ContentType.Password },
            enabled = !dialog.isChecking,
            label = { Text(stringResource(R.string.bot_password_label)) },
            supportingText =
                error?.let {
                    { Text(text = it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                },
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            onKeyboardAction = { onEvent(BotButtonEvent.SubmitPassword) },
        )
        if (dialog.isChecking) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        PasswordActions(canConfirm = !dialog.isChecking && password.text.isNotEmpty(), onEvent = onEvent)
    }
}

/** "Cancel" at the start and "Confirm" at the end, enabled where [canConfirm]. */
@Composable
private fun PasswordActions(
    canConfirm: Boolean,
    onEvent: (BotButtonEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CancelButton { onEvent(BotButtonEvent.DismissDialog) }
        Button(
            onClick = { onEvent(BotButtonEvent.SubmitPassword) },
            enabled = canConfirm,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.bot_password_confirm), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

@PingogramPreviews
@Composable
private fun BotPasswordPreview() {
    PingogramPreview {
        BotPasswordContent(BotButtonsPreviewData.passwordWrong, remember { TextFieldState("hunter2") }, onEvent = {})
    }
}
