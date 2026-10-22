package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.profile.R

/**
 * The info page's confirmations (`docs/DESIGN_SYSTEM.md` §6 "Dialogs": `AlertDialog`s for destructive confirmations):
 * leaving, deleting for everyone and clearing the history, each naming the chat ([title]) and its confirm button in
 * `error`; and the link's QR code in a sheet ([QrCodeSheet]). "Cancel" closes a dialog.
 */
@Composable
internal fun ChatInfoDialogs(
    dialog: ChatInfoDialog?,
    title: String,
    kind: GroupKind,
    onEvent: (ChatInfoEvent) -> Unit,
) {
    val isChannel = kind == GroupKind.Channel
    // The title, isolated: each text keeps its language's direction around it.
    val name = title.bidiIsolated()
    when (dialog) {
        ChatInfoDialog.Leave -> {
            DestructiveDialog(
                title =
                    stringResource(
                        if (isChannel) R.string.chat_info_leave_channel_title else R.string.chat_info_leave_group_title,
                    ),
                text = stringResource(R.string.chat_info_leave_text, name).bidiIsolated(),
                confirm = stringResource(R.string.chat_info_action_leave),
                onEvent = onEvent,
            )
        }

        ChatInfoDialog.Delete -> {
            DestructiveDialog(
                title =
                    stringResource(
                        if (isChannel) {
                            R.string.chat_info_delete_channel_title
                        } else {
                            R.string.chat_info_delete_group_title
                        },
                    ),
                text = stringResource(R.string.chat_info_delete_text, name).bidiIsolated(),
                confirm = stringResource(R.string.chat_info_delete_confirm),
                onEvent = onEvent,
            )
        }

        ChatInfoDialog.ClearHistory -> {
            DestructiveDialog(
                title = stringResource(R.string.chat_info_clear_history_title),
                text = stringResource(R.string.chat_info_clear_history_text, name).bidiIsolated(),
                confirm = stringResource(R.string.chat_info_clear_history_confirm),
                onEvent = onEvent,
            )
        }

        is ChatInfoDialog.QrCode -> {
            QrCodeSheet(
                url = dialog.url,
                onShare = { onEvent(ChatInfoEvent.ShareLink) },
                onDismiss = { onEvent(ChatInfoEvent.DismissDialog) },
            )
        }

        null -> {}
    }
}

/**
 * A destructive confirmation: [title], [text], "Cancel", and [confirm] in `error`, which sends [ChatInfoEvent.Confirm].
 */
@Composable
internal fun DestructiveDialog(
    title: String,
    text: String,
    confirm: String,
    onEvent: (ChatInfoEvent) -> Unit,
) {
    ConfirmationDialog(
        title = title,
        text = text,
        confirm = confirm,
        onConfirm = { onEvent(ChatInfoEvent.Confirm) },
        onDismiss = { onEvent(ChatInfoEvent.DismissDialog) },
    )
}

/**
 * An `AlertDialog` that confirms a destructive action, shared by the info page and the pages it opens: [title],
 * [text], "Cancel" ([onDismiss]) and [confirm] in `error` ([onConfirm]), both text buttons with the press morph.
 */
@Composable
internal fun ConfirmationDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.profile_dialog_dismiss))
            }
        },
        title = { Text(title) },
        text = { Text(text) },
    )
}
