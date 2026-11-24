package dev.kyralabs.pingogram.feature.chat.ui.bot

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.BotEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent

/**
 * The question before a bot command picked from the commands' menu or suggestions goes, while Settings → Chats →
 * "Confirm bot commands" is on (`REQUIREMENTS.md` FR-X12): "Send /help?", that it goes to the chat as a message, and
 * "Send" ([BotEvent.ConfirmCommand]) or "Cancel" ([BotEvent.CancelCommand], as dismissing it), in an [AlertDialog].
 */
@Composable
internal fun BotCommandConfirmDialog(
    command: String,
    onEvent: (ChatEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(BotEvent.CancelCommand) },
        confirmButton = {
            TextButton(onClick = { onEvent(BotEvent.ConfirmCommand) }) {
                Text(stringResource(R.string.bot_command_confirm_send), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(BotEvent.CancelCommand) }) {
                Text(stringResource(R.string.bot_command_confirm_cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
        title = { Text(stringResource(R.string.bot_command_confirm_title, command.bidiIsolated())) },
        text = { Text(stringResource(R.string.bot_command_confirm_body)) },
    )
}

@PingogramPreviews
@Composable
private fun BotCommandConfirmDialogPreview() {
    PingogramPreview { BotCommandConfirmDialog("/help", onEvent = {}) }
}
