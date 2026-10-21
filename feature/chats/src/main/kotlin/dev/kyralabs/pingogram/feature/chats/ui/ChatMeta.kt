package dev.kyralabs.pingogram.feature.chats.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The end of a row: the time with the state of the current user's last message, over the unread badges or the pin.
 * Without [showTime] (large font scales, [timeOnPreviewLine]), only the state over the badges, a narrow column that
 * leaves the title its line. Screen readers get the row's description instead.
 */
@Composable
internal fun ChatMeta(
    item: ChatListItemModel,
    showTime: Boolean = true,
) {
    Column(
        modifier = Modifier.clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        horizontalAlignment = Alignment.End,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.outgoingStatus?.let { OutgoingStatusIcon(it) }
            if (showTime) {
                Text(
                    text = item.time,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall.userContent,
                )
            }
        }
        UnreadBadges(item)
    }
}

/**
 * Whether rows show their time at the start of the preview's line instead of above the badges: at font scales of
 * [LARGE_FONT_SCALE] and more, where the time, the widest part of the end column, would take a third of the title's
 * line. The same for every row, so the list stays even.
 */
@Composable
@ReadOnlyComposable
internal fun timeOnPreviewLine(): Boolean = LocalDensity.current.fontScale >= LARGE_FONT_SCALE

/** A clock while sending, an error when it failed, one check once sent, two in `primary` once read. */
@Composable
private fun OutgoingStatusIcon(status: OutgoingStatus) {
    val colors = MaterialTheme.colorScheme
    val (icon, tint) =
        when (status) {
            OutgoingStatus.Pending -> UiR.drawable.ic_schedule to colors.onSurfaceVariant
            OutgoingStatus.Failed -> UiR.drawable.ic_error to colors.error
            OutgoingStatus.Sent -> UiR.drawable.ic_done to colors.onSurfaceVariant
            OutgoingStatus.Read -> UiR.drawable.ic_done_all to colors.primary
        }
    InlineIcon(icon, tint, MaterialTheme.sizes.inlineIconSmall)
}

/**
 * Reactions, mentions and the unread count as badges, in `primary`, or in `surfaceVariant` for a muted chat; a dot
 * for a chat marked as unread; the pin of a pinned chat with nothing unread.
 */
@Composable
private fun UnreadBadges(item: ChatListItemModel) {
    val colors = MaterialTheme.colorScheme
    val container = if (item.isMuted) colors.surfaceVariant else colors.primary
    val content = if (item.isMuted) colors.onSurfaceVariant else colors.onPrimary
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.hasUnreadReactions) {
            Badge(containerColor = container, contentColor = content) { BadgeIcon(UiR.drawable.ic_favorite_filled) }
        }
        if (item.hasUnreadMention) {
            Badge(containerColor = container, contentColor = content) { BadgeIcon(UiR.drawable.ic_alternate_email) }
        }
        when {
            item.unreadCount > 0 -> {
                Badge(containerColor = container, contentColor = content) { Text(unreadCountText(item.unreadCount)) }
            }

            item.isMarkedAsUnread -> {
                Badge(containerColor = container)
            }

            item.isPinned && !item.hasUnreadMention && !item.hasUnreadReactions -> {
                InlineIcon(UiR.drawable.ic_push_pin, colors.onSurfaceVariant, MaterialTheme.sizes.inlineIconSmall)
            }
        }
    }
}

@Composable
private fun BadgeIcon(
    @DrawableRes icon: Int,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(MaterialTheme.sizes.badgeIcon),
    )
}

/** The count, capped at "999+" so the badge stays small. */
@Composable
@ReadOnlyComposable
internal fun unreadCountText(count: Int): String =
    if (count > MAX_UNREAD_SHOWN) {
        stringResource(R.string.chat_list_unread_overflow, MAX_UNREAD_SHOWN)
    } else {
        stringResource(R.string.chat_list_unread_count, count)
    }

private const val MAX_UNREAD_SHOWN = 999

/** 150%: from here on the time moves to the preview's line ([timeOnPreviewLine]). */
private const val LARGE_FONT_SCALE = 1.5f
