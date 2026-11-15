package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Replying in place (`docs/DESIGN_SYSTEM.md` §6 "Catch-up", the owner's board 32d), in the card's actions' place: the
 * message answered ([replyTo]) as the composer's reply bar shows it (the reply icon in `primary`, "Reply to Meera" in
 * `labelLarge` `primary` over its text in `bodyMedium` `onSurfaceVariant`, two lines) with a close button; the rounded
 * tonal field, which takes the focus; and "Send and go to next", a `FilledIconButton` (a `LoadingIndicator` while
 * Telegram takes the reply); then what sending does in `bodySmall`. Back closes the field. What is typed survives a
 * rotation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CatchUpReplyField(
    replyTo: CatchUpMessage?,
    reply: CatchUpReplyState,
    onEvent: (CatchUpEvent) -> Unit,
) {
    val text = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    val inspection = LocalInspectionMode.current
    BackHandler(enabled = !reply.isSending) { onEvent(CatchUpEvent.CancelReply) }
    LaunchedEffect(Unit) { if (!inspection) focus.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        if (replyTo != null) ReplyQuote(replyTo, onCancel = { onEvent(CatchUpEvent.CancelReply) })
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                state = text,
                modifier = Modifier.weight(1f).focusRequester(focus),
                enabled = !reply.isSending,
                textStyle = MaterialTheme.typography.bodyLarge.userContent,
                placeholder = { Text(stringResource(R.string.catch_up_reply_placeholder)) },
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = FIELD_MAX_LINES),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = TextFieldDefaults.roundedShape,
                colors = TextFieldDefaults.tonalColors(),
            )
            FilledIconButton(
                onClick = { onEvent(CatchUpEvent.SendReply(text.text.toString())) },
                enabled = !reply.isSending && text.text.isNotBlank(),
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.padding(start = MaterialTheme.spacing.extraSmall),
            ) {
                if (reply.isSending) {
                    LoadingIndicator()
                } else {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_send_filled),
                        contentDescription = stringResource(R.string.catch_up_send_and_next),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.catch_up_reply_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** "Reply to Meera" over the message answered, as the composer's reply bar shows it, with "Cancel reply". */
@Composable
private fun ReplyQuote(
    message: CatchUpMessage,
    onCancel: () -> Unit,
) {
    val resources = LocalResources.current
    val sender = message.sender?.resolve(resources)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)
                .padding(start = MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_reply),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f).padding(vertical = MaterialTheme.spacing.small)) {
            Text(
                text =
                    if (sender != null) {
                        stringResource(R.string.catch_up_reply_to, sender)
                    } else {
                        stringResource(R.string.catch_up_reply_to_message)
                    },
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = message.text.resolve(resources),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = QUOTE_LINES,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
        }
        IconButton(onClick = onCancel, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(UiR.drawable.ic_close), stringResource(R.string.catch_up_reply_cancel))
        }
    }
}

private const val FIELD_MAX_LINES = 4
private const val QUOTE_LINES = 2
