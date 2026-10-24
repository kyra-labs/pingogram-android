package dev.kyralabs.pingogram.feature.chat.ui.poll

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.PollEvent

/**
 * The question before a poll is stopped for everyone (`docs/DESIGN_SYSTEM.md` §6 "Polls": an [AlertDialog], for
 * destructive confirmations only): "Stop poll?", that no one can vote any more and it cannot be opened again, and
 * "Stop poll" in the `error` color ([PollEvent.ConfirmStop]); "Cancel", the scrim and back dismiss it
 * ([PollEvent.DismissStop]).
 */
@Composable
internal fun StopPollAlert(onEvent: (ChatEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(PollEvent.DismissStop) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(PollEvent.ConfirmStop) },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.poll_stop_confirm), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(PollEvent.DismissStop) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.poll_stop_cancel), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
        icon = { Icon(painter = painterResource(R.drawable.ic_block), contentDescription = null) },
        title = { Text(stringResource(R.string.poll_stop_title)) },
        text = { Text(stringResource(R.string.poll_stop_text)) },
    )
}

@PingogramPreviews
@Composable
private fun StopPollAlertPreview() {
    PingogramPreview { StopPollAlert(onEvent = {}) }
}
