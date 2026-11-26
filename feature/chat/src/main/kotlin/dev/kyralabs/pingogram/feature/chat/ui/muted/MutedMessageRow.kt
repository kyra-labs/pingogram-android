package dev.kyralabs.pingogram.feature.chat.ui.muted

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.SenderAvatarColumn
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A message collapsed for a muted word (`REQUIREMENTS.md` FR-X21, `docs/DESIGN_SYSTEM.md` §6 "Muted words"), in place
 * of its bubble: on the incoming side, past the avatar column's space where the group has one, a pill of the service
 * messages' look, the hidden symbol and "Message from Ada · muted word · Tap to show"; a tap ([onShow]) shows the
 * bubble, on a 48 dp target. Its text is never shown, nor read to screen readers, until then.
 */
@Composable
internal fun MutedMessageRow(
    bubble: ChatItem.Bubble,
    onShow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (bubble.hasAvatarColumn) SenderAvatarColumn(avatar = null)
        val shape = MaterialTheme.shapes.extraLarge
        Row(
            modifier =
                Modifier
                    .minimumInteractiveComponentSize()
                    .widthIn(max = MaterialTheme.sizes.bubbleMaxWidth)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(
                        onClickLabel = stringResource(R.string.chat_muted_show),
                        role = Role.Button,
                        onClick = onShow,
                    ).padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_visibility_off),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
            )
            Text(
                text = stringResource(R.string.chat_muted_message, bubble.sender.resolve().bidiIsolated()),
                modifier = Modifier.weight(1f, fill = false),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun MutedMessageRowPreview() {
    val bubble =
        (ChatPreviewData.groupChat.content as ChatContent.Content)
            .items
            .filterIsInstance<ChatItem.Bubble>()
            .first { !it.isOutgoing }
    PingogramPreview { MutedMessageRow(bubble, onShow = {}) }
}
