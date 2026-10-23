package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The confirmation before chats are deleted or left (`docs/DESIGN_SYSTEM.md` §6: an [AlertDialog] for destructive
 * confirmations only): "Delete chat?", "Leave group?", "Leave channel?" or "Delete 3 chats?", what that does, and the
 * confirm button in the `error` color. Where one chat may also be deleted for everyone, a check box offers it,
 * unchecked at first and kept across rotation; checking it on a group or channel turns "Leave" into "Delete".
 *
 * @param onConfirm called with whether the check box is checked.
 */
@Composable
internal fun DeleteChatDialog(
    dialog: DeleteDialog,
    onConfirm: (forEveryone: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var forEveryone by rememberSaveable(dialog) { mutableStateOf(false) }
    val texts = deleteDialogTexts(dialog)
    val single = dialog as? DeleteDialog.Single
    val forEveryoneLabel = single?.let { forEveryoneLabel(it) }
    val leaves = single?.kind == DeleteKind.LeaveGroup || single?.kind == DeleteKind.LeaveChannel
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(forEveryone) },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    text =
                        stringResource(
                            if (leaves &&
                                !forEveryone
                            ) {
                                R.string.chat_list_leave_confirm
                            } else {
                                R.string.chat_list_delete_confirm
                            },
                        ),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(
                    text = stringResource(R.string.chat_list_cancel),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        icon = { Icon(painter = painterResource(UiR.drawable.ic_delete), contentDescription = null) },
        title = { Text(texts.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Text(texts.body)
                if (forEveryoneLabel != null) {
                    ForEveryoneCheckbox(
                        label = forEveryoneLabel,
                        checked = forEveryone,
                        onCheckedChange = { forEveryone = it },
                    )
                }
            }
        },
    )
}

/** The check box with its label, toggled as one, which screen readers read as one check box. */
@Composable
private fun ForEveryoneCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

private class DeleteDialogTexts(
    val title: String,
    val body: String,
)

@Composable
@ReadOnlyComposable
private fun deleteDialogTexts(dialog: DeleteDialog): DeleteDialogTexts =
    when (dialog) {
        is DeleteDialog.Single -> {
            val name = dialog.title.resolve().bidiIsolated()
            when (dialog.kind) {
                DeleteKind.DeleteChat -> {
                    DeleteDialogTexts(
                        stringResource(R.string.chat_list_delete_title),
                        stringResource(R.string.chat_list_delete_body, name).bidiIsolated(),
                    )
                }

                DeleteKind.LeaveGroup -> {
                    DeleteDialogTexts(
                        stringResource(R.string.chat_list_leave_group_title),
                        stringResource(R.string.chat_list_leave_body, name).bidiIsolated(),
                    )
                }

                DeleteKind.LeaveChannel -> {
                    DeleteDialogTexts(
                        stringResource(R.string.chat_list_leave_channel_title),
                        stringResource(R.string.chat_list_leave_body, name).bidiIsolated(),
                    )
                }
            }
        }

        is DeleteDialog.Bulk -> {
            val count = dialog.chatIds.size
            DeleteDialogTexts(
                LocalResources.current.getQuantityString(R.plurals.chat_list_delete_chats_title, count, count),
                stringResource(R.string.chat_list_delete_chats_body),
            )
        }
    }

/** "Also delete for Ada", "Delete for all members" or "Delete for all subscribers"; null without the choice. */
@Composable
@ReadOnlyComposable
private fun forEveryoneLabel(dialog: DeleteDialog.Single): String? =
    when (dialog.forEveryone) {
        DeleteForEveryone.OtherUser -> {
            stringResource(R.string.chat_list_delete_for_user, dialog.title.resolve().bidiIsolated()).bidiIsolated()
        }

        DeleteForEveryone.AllMembers -> {
            stringResource(R.string.chat_list_delete_for_members)
        }

        DeleteForEveryone.AllSubscribers -> {
            stringResource(R.string.chat_list_delete_for_subscribers)
        }

        null -> {
            null
        }
    }

@PingogramPreviews
@Composable
private fun DeleteChatDialogPreview() {
    PingogramPreview {
        DeleteChatDialog(
            dialog = ChatListPreviewData.deletePrivateChat,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun LeaveGroupDialogPreview() {
    PingogramPreview { DeleteChatDialog(dialog = ChatListPreviewData.leaveGroup, onConfirm = {}, onDismiss = {}) }
}

@PingogramPreviews
@Composable
private fun DeleteChatsDialogPreview() {
    PingogramPreview { DeleteChatDialog(dialog = ChatListPreviewData.deleteChats, onConfirm = {}, onDismiss = {}) }
}
