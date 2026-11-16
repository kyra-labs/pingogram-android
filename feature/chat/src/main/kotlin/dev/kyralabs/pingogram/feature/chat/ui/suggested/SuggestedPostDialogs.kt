package dev.kyralabs.pingogram.feature.chat.ui.suggested

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.SuggestedPostEvent
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The question before answering a suggested post (`docs/DESIGN_SYSTEM.md` §6 "Suggested posts"), an `AlertDialog`:
 * "Publish this post?" (it goes out in the channel when its author asked, or now) with "Publish", or "Decline this
 * post?" with an optional comment for its author in a filled `TextField` and "Decline" in `error`; a
 * `LinearWavyProgressIndicator` while Telegram answers, and why it refused in `error` (a polite live region).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SuggestedPostAnswerDialog(
    dialog: SuggestedPostDialog,
    comment: TextFieldState,
    onEvent: (SuggestedPostEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(SuggestedPostEvent.Dismiss) },
        icon = { Icon(painterResource(UiR.drawable.ic_campaign), contentDescription = null) },
        title = {
            Text(stringResource(dialog.texts.title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                Text(stringResource(dialog.texts.text))
                if (!dialog.approve) {
                    TextField(
                        state = comment,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.suggested_post_comment)) },
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = COMMENT_LINES),
                        textStyle = MaterialTheme.typography.bodyLarge.userContent,
                        enabled = !dialog.isWorking,
                    )
                }
                dialog.error?.let { error ->
                    Text(
                        text = errorText(error),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (dialog.isWorking) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(SuggestedPostEvent.Confirm) }, enabled = !dialog.isWorking) {
                Text(
                    text =
                        stringResource(
                            if (dialog.approve) R.string.suggested_post_publish else R.string.suggested_post_decline,
                        ),
                    color = if (dialog.approve) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SuggestedPostEvent.Dismiss) }, enabled = !dialog.isWorking) {
                Text(stringResource(R.string.suggested_post_cancel))
            }
        },
    )
}

private const val COMMENT_LINES = 4

/** The dialog's title, text and confirming button, for publishing or declining. */
private class DialogTexts(
    @param:StringRes val title: Int,
    @param:StringRes val text: Int,
    @param:StringRes val confirm: Int,
)

private val SuggestedPostDialog.texts: DialogTexts
    get() =
        if (approve) {
            DialogTexts(
                R.string.suggested_post_approve_title,
                R.string.suggested_post_approve_text,
                R.string.suggested_post_publish,
            )
        } else {
            DialogTexts(
                R.string.suggested_post_decline_title,
                R.string.suggested_post_decline_text,
                R.string.suggested_post_decline,
            )
        }
