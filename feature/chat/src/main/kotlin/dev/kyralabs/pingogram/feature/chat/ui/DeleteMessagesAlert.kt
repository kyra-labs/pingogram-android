package dev.kyralabs.pingogram.feature.chat.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The confirmation before messages are deleted (`REQUIREMENTS.md` FR-C6; `docs/DESIGN_SYSTEM.md` §6: an
 * [AlertDialog], for destructive confirmations only): "Delete message?" ("Delete 4 messages?" for an album), for whom
 * it deletes them, and the confirm button in the `error` color. Where the user may also delete them for the others, a
 * check box offers it ("Also delete for Ada", "Delete for everyone"), unchecked at first and kept across rotation.
 *
 * @param onConfirm called with whether the check box is checked.
 */
@Composable
internal fun DeleteMessagesAlert(
    dialog: DeleteMessagesDialog,
    onConfirm: (forEveryone: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var forEveryone by rememberSaveable(dialog) { mutableStateOf(false) }
    val count = dialog.messageIds.size
    val title =
        if (count == 1) {
            stringResource(R.string.message_delete_title)
        } else {
            LocalResources.current.getQuantityString(R.plurals.message_delete_title_many, count, count)
        }
    val scope = dialog.scope
    val body =
        when (scope) {
            DeleteScope.ForMe -> R.string.message_delete_for_me_body
            DeleteScope.ForEveryone -> R.string.message_delete_for_everyone_body
            is DeleteScope.Choice -> R.string.message_delete_choice_body
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(forEveryone) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                // For `benchmark/`'s journeys, which delete what they sent.
                modifier = Modifier.testTag("delete_confirm"),
            ) {
                Text(
                    stringResource(R.string.message_delete_confirm),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.message_delete_cancel),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        icon = { Icon(painter = painterResource(UiR.drawable.ic_delete), contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Text(stringResource(body))
                if (scope is DeleteScope.Choice) {
                    ForEveryoneCheckbox(
                        label = scope.option.resolve(),
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

@PingogramPreviews
@Composable
private fun DeleteForBothPreview() {
    PingogramPreview { DeleteMessagesAlert(ChatPreviewData.deleteForBoth, onConfirm = {}, onDismiss = {}) }
}

@PingogramPreviews
@Composable
private fun DeleteAlbumPreview() {
    PingogramPreview { DeleteMessagesAlert(ChatPreviewData.deleteAlbumForEveryone, onConfirm = {}, onDismiss = {}) }
}
