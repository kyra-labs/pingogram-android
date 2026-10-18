package dev.kyralabs.pingogram.feature.chats.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import dev.kyralabs.pingogram.feature.chats.R

/**
 * What screen readers say for a row (`docs/DESIGN_SYSTEM.md` §9): "Ada Lovelace, Verified, Online, Muted, Pinned,
 * Ada: see you, 9:41 AM, Read, 3 unread messages, Unread mention". Its [title] and [preview] as the row shows them.
 */
@Composable
@ReadOnlyComposable
internal fun rowDescription(
    item: ChatListItemModel,
    title: String,
    preview: String,
): String {
    val resources = LocalResources.current
    return buildList {
        add(title)
        addAll(chatStates(resources, item))
        if (preview.isNotEmpty()) add(preview)
        if (item.time.isNotEmpty()) add(item.time)
        addAll(messageStates(resources, item))
    }.joinToString(", ")
}

/** What the title, its badges and the avatar show: verification, encryption, online, mute and pin. */
private fun chatStates(
    resources: Resources,
    item: ChatListItemModel,
): List<String> =
    buildList {
        when (item.badge) {
            TitleBadge.Verified -> add(resources.getString(R.string.chat_list_a11y_verified))
            TitleBadge.Scam -> add(resources.getString(R.string.chat_list_badge_scam))
            TitleBadge.Fake -> add(resources.getString(R.string.chat_list_badge_fake))
            null -> Unit
        }
        if (item.isSecret) add(resources.getString(R.string.chat_list_a11y_secret))
        if (item.isOnline) add(resources.getString(R.string.chat_list_a11y_online))
        if (item.isMuted) add(resources.getString(R.string.chat_list_a11y_muted))
        if (item.isPinned) add(resources.getString(R.string.chat_list_a11y_pinned))
    }

/** What the end of the row shows: the last message's state and what is unread. */
private fun messageStates(
    resources: Resources,
    item: ChatListItemModel,
): List<String> =
    buildList {
        when (item.outgoingStatus) {
            OutgoingStatus.Pending -> add(resources.getString(R.string.chat_list_a11y_pending))
            OutgoingStatus.Failed -> add(resources.getString(R.string.chat_list_a11y_failed))
            OutgoingStatus.Sent -> add(resources.getString(R.string.chat_list_a11y_sent))
            OutgoingStatus.Read -> add(resources.getString(R.string.chat_list_a11y_read))
            null -> Unit
        }
        if (item.unreadCount > 0) {
            add(resources.getQuantityString(R.plurals.chat_list_a11y_unread, item.unreadCount, item.unreadCount))
        } else if (item.isMarkedAsUnread) {
            add(resources.getString(R.string.chat_list_a11y_marked_unread))
        }
        if (item.hasUnreadMention) add(resources.getString(R.string.chat_list_a11y_mention))
        if (item.hasUnreadReactions) add(resources.getString(R.string.chat_list_a11y_reaction))
    }
