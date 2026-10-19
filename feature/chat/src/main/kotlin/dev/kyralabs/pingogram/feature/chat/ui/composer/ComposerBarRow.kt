package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The bar above the composer's text field while it replies or edits (`REQUIREMENTS.md` FR-C5, FR-C6): the reply or
 * edit icon and a title in `primary` (the replied message's sender, or "Editing"), one line of the message in
 * `onSurfaceVariant` below, and a close button. Tapping the bar shows the message in the list ([onShow]); the close
 * button ends the reply or edit ([onClose]).
 */
@Composable
internal fun ComposerBarRow(
    bar: ComposerBar,
    onShow: (messageId: Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val primary = MaterialTheme.colorScheme.primary
    val (icon, title, closeLabel) =
        when (bar) {
            is ComposerBar.Reply -> {
                Triple(UiR.drawable.ic_reply, bar.sender?.resolve(), R.string.composer_reply_cancel)
            }

            is ComposerBar.Edit -> {
                Triple(
                    UiR.drawable.ic_edit,
                    stringResource(R.string.composer_edit_title),
                    R.string.composer_edit_cancel,
                )
            }
        }
    Row(
        modifier = modifier.fillMaxWidth().padding(start = spacing.large, end = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.composer_bar_show), role = Role.Button) {
                        onShow(bar.messageId)
                    }.padding(vertical = spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = primary)
            Column {
                title?.let {
                    Text(
                        text = it,
                        color = primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = bar.preview.resolve(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(painter = painterResource(UiR.drawable.ic_close), contentDescription = stringResource(closeLabel))
        }
    }
}

@PingogramPreviews
@Composable
private fun ComposerReplyBarPreview() {
    PingogramPreview {
        ComposerBarRow(
            ComposerBar.Reply(1, UiText.Plain("Ada Lovelace"), UiText.Plain("Is the meetup still on for tonight?")),
            onShow = {},
            onClose = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun ComposerEditBarPreview() {
    PingogramPreview {
        ComposerBarRow(
            ComposerBar.Edit(1, UiText.Plain("Morning! I finished the draft of the talk."), canBeEmpty = false),
            onShow = {},
            onClose = {},
        )
    }
}
