package dev.kyralabs.pingogram.feature.chats.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * One chat in a chat list (`REQUIREMENTS.md` FR-L2, `docs/DESIGN_SYSTEM.md` §6): an M3 [ListItem] with the avatar, the
 * name with its badges, a two-line preview ([ChatPreviewText]), and the time with the unread badges at the end
 * ([ChatMeta]). For the main list, the archive and folders alike; swipe actions and selection wrap or extend it.
 *
 * Everything shown is in [item], mapped by the ViewModel: the row resolves texts and builds the preview's styled text,
 * and runs no effects (`docs/PERFORMANCE.md` §4). For screen readers it is one node that reads the whole row, unread
 * count included ([rowDescription], `docs/DESIGN_SYSTEM.md` §9). No fixed heights, so it grows with the font scale.
 *
 * @param onClick opens the chat; null makes the row not clickable (nothing to open yet).
 * @param onLongClick e.g. starts selection; only with [onClick].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatListItem(
    item: ChatListItemModel,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val title = item.title.resolve()
    val preview = previewText(item.preview, isFailed = item.outgoingStatus == OutgoingStatus.Failed)
    val description = rowDescription(item, title, preview.text)
    val leading = @Composable { ChatAvatar(item, title) }
    val trailing = @Composable { ChatMeta(item) }
    val supporting = @Composable { ChatPreviewText(preview) }
    val headline = @Composable { ChatTitle(item, title) }
    if (onClick != null) {
        ListItem(
            onClick = onClick,
            modifier = modifier.semantics { contentDescription = description },
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            onLongClick = onLongClick,
            content = headline,
        )
    } else {
        ListItem(
            modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            content = headline,
        )
    }
}

/** Decorative: the row's description names the chat. */
@Composable
private fun ChatAvatar(
    item: ChatListItemModel,
    title: String,
) {
    Avatar(
        photo = item.photo,
        name = title,
        id = item.id,
        size = MaterialTheme.sizes.avatarMedium,
        isOnline = item.isOnline,
        placeholderIcon = item.placeholderIcon,
    )
}

/** The lock of a secret chat, the name, its badge, and the mute icon. */
@Composable
private fun ChatTitle(
    item: ChatListItemModel,
    title: String,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.isSecret) InlineIcon(UiR.drawable.ic_lock, colors.primary, MaterialTheme.sizes.inlineIcon)
        Text(
            text = title,
            modifier = Modifier.weight(1f, fill = false),
            color = colors.onSurface,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = with(MaterialTheme.typography) { if (item.isUnread) titleMediumEmphasized else titleMedium },
        )
        when (item.badge) {
            TitleBadge.Verified -> {
                InlineIcon(
                    UiR.drawable.ic_verified_filled,
                    colors.primary,
                    MaterialTheme.sizes.inlineIcon,
                )
            }

            TitleBadge.Scam -> {
                WarningLabel(stringResource(R.string.chat_list_badge_scam))
            }

            TitleBadge.Fake -> {
                WarningLabel(stringResource(R.string.chat_list_badge_fake))
            }

            null -> {
                Unit
            }
        }
        if (item.isMuted) {
            InlineIcon(UiR.drawable.ic_notifications_off, colors.onSurfaceVariant, MaterialTheme.sizes.inlineIconSmall)
        }
    }
}

/** "Scam" or "Fake" in the error role, as Telegram's warning badges. */
@Composable
private fun WarningLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        maxLines = 1,
        style = MaterialTheme.typography.labelSmallEmphasized,
    )
}

/** A decorative icon within a line of text: the row's description says what it means. */
@Composable
internal fun InlineIcon(
    @DrawableRes icon: Int,
    tint: Color,
    size: Dp,
) {
    Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(size), tint = tint)
}

@PingogramPreviews
@Composable
private fun ChatListItemPreview() {
    PingogramPreview {
        Column {
            ChatListPreviewData.items.take(PREVIEW_ROWS).forEach { ChatListItem(item = it, onClick = {}) }
        }
    }
}

private const val PREVIEW_ROWS = 4
