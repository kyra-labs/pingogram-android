package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The bar above the composer's text field while it replies or edits (`REQUIREMENTS.md` FR-C5, FR-C6): the reply or
 * edit icon (a quote mark for a quote reply) and a title in `primary` (the replied message's sender, "Reply to Ada in
 * Team chat" for a message of another chat, or "Editing"), one line of the message (of the quoted part) in
 * `onSurfaceVariant` below, and a close button. Tapping the bar shows the message in the list ([onShow]), except one
 * of another chat; the close button ends the reply or edit ([onClose]). While editing a message whose media can be
 * replaced, a "Replace" button with a tooltip comes before it ([onReplace]), which opens the picker.
 */
@Composable
internal fun ComposerBarRow(
    bar: ComposerBar,
    onShow: (messageId: Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onReplace: () -> Unit = {},
) {
    val spacing = MaterialTheme.spacing
    val primary = MaterialTheme.colorScheme.primary
    val (icon, title, closeLabel) =
        when (bar) {
            is ComposerBar.Reply -> {
                Triple(
                    if (bar.isQuote) R.drawable.ic_format_quote else UiR.drawable.ic_reply,
                    bar.sender?.resolve(),
                    R.string.composer_reply_cancel,
                )
            }

            is ComposerBar.Edit -> {
                Triple(
                    UiR.drawable.ic_edit,
                    stringResource(R.string.composer_edit_title),
                    R.string.composer_edit_cancel,
                )
            }
        }
    val opens = !(bar is ComposerBar.Reply && bar.isElsewhere)
    val quoteLabel = stringResource(R.string.quote_label).takeIf { bar is ComposerBar.Reply && bar.isQuote }
    Row(
        modifier = modifier.fillMaxWidth().padding(start = spacing.large, end = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val showLabel = stringResource(R.string.composer_bar_show)
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .then(
                        if (opens) {
                            Modifier.clickable(onClickLabel = showLabel, role = Role.Button) { onShow(bar.messageId) }
                        } else {
                            Modifier
                        },
                    ).padding(vertical = spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painter = painterResource(icon), contentDescription = quoteLabel, tint = primary)
            BarTexts(title = title, preview = bar.preview.resolve())
        }
        (bar as? ComposerBar.Edit)?.replace?.let { replace -> ReplaceButton(replace, onReplace) }
        IconButton(onClick = onClose) {
            Icon(painter = painterResource(UiR.drawable.ic_close), contentDescription = stringResource(closeLabel))
        }
    }
}

/** The bar's title in `primary` over its one-line preview. */
@Composable
private fun BarTexts(
    title: String?,
    preview: String,
) {
    Column {
        title?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.userContent,
            )
        }
        Text(
            text = preview,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.userContent,
        )
    }
}

/** "Replace photo or video" or "Replace file", an icon button with its tooltip, for the edited message's media. */
@OptIn(ExperimentalMaterial3Api::class) // TooltipBox
@Composable
private fun ReplaceButton(
    replace: ReplaceMedia,
    onClick: () -> Unit,
) {
    val label =
        stringResource(
            when (replace) {
                ReplaceMedia.Media -> R.string.composer_replace_media
                ReplaceMedia.Document -> R.string.composer_replace_file
            },
        )
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(painter = painterResource(R.drawable.ic_swap_horiz), contentDescription = label)
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
private fun ComposerQuoteBarPreview() {
    PingogramPreview {
        ComposerBarRow(
            ComposerBar.Reply(1, UiText.Plain("Ada Lovelace"), UiText.Plain("still on for tonight"), isQuote = true),
            onShow = {},
            onClose = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun ComposerReplyElsewhereBarPreview() {
    PingogramPreview {
        ComposerBarRow(
            ComposerBar.Reply(
                1,
                UiText.Plain("Reply to Ada Lovelace in Compose Developers"),
                UiText.Plain("Is the meetup still on for tonight?"),
                isElsewhere = true,
            ),
            onShow = {},
            onClose = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun ComposerReplaceBarPreview() {
    PingogramPreview {
        ComposerBarRow(
            ComposerBar.Edit(1, UiText.resource(UiR.string.preview_photo), canBeEmpty = true, ReplaceMedia.Media),
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
