package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * Each community's entry ([CommunityEntryRow]) before the row of [items] its [CommunityEntry.before] names, and its
 * chats below it while it is expanded, as rows of its group inset at the start ([chatRow], told whether it ends the
 * group); the [items] themselves through [chatRows], from the index of their first. Each group keeps [groupGap] from
 * the rows around it, as the board sets it apart.
 */
@Suppress("LongParameterList") // The rows, the entries, their state and events, and how the list draws each kind.
internal fun LazyListScope.chatsWithCommunities(
    items: List<ChatListItemModel>,
    communities: List<CommunityEntry>,
    enabled: Boolean,
    onEvent: (ChatListEvent) -> Unit,
    rowModifier: LazyItemScope.() -> Modifier,
    chatRows: LazyListScope.(rows: List<ChatListItemModel>, firstIndex: Int) -> Unit,
    chatRow: @Composable LazyItemScope.(item: ChatListItemModel, segment: RowSegment, isLast: Boolean) -> Unit,
) {
    var start = 0
    communities.forEach { entry ->
        val before = entry.before.coerceIn(start, items.size)
        chatRows(items.subList(start, before), start)
        start = before
        item(key = communityKey(entry.id), contentType = COMMUNITY_ROW) {
            CommunityEntryRow(entry, enabled, onEvent, rowModifier().groupGap(top = true, bottom = !entry.isExpanded))
        }
        if (entry.isExpanded) {
            val count = entry.chats.size + 1
            itemsIndexed(entry.chats, key = { _, chat -> chat.id }, contentType = { _, _ -> COMMUNITY_CHAT_ROW }) {
                index,
                chat,
                ->
                chatRow(chat, RowSegment(index + 1, count), index == entry.chats.lastIndex)
            }
        }
    }
    chatRows(items.subList(start, items.size), start)
}

/**
 * The index of the row keyed [key] (a chat's id, or a community entry's [communityKey]) among the rows
 * [chatsWithCommunities] shows, from the first chat; null where it shows none.
 */
internal fun ChatListContent.Content.rowIndexOf(key: Any): Int? = rowKeys().indexOf(key).takeIf { it >= 0 }

/** The keys of the rows [chatsWithCommunities] shows, in their order. */
private fun ChatListContent.Content.rowKeys(): List<Any> =
    buildList {
        var start = 0
        communities.forEach { entry ->
            val before = entry.before.coerceIn(start, items.size)
            items.subList(start, before).mapTo(this) { it.id }
            start = before
            add(communityKey(entry.id))
            if (entry.isExpanded) entry.chats.mapTo(this) { it.id }
        }
        items.subList(start, items.size).mapTo(this) { it.id }
    }

/** The inset of a community's chats below its entry (the board's 20 px), at the start. */
@Composable
internal fun Modifier.communityChatInset(): Modifier = padding(start = MaterialTheme.spacing.large)

/** The small step of space above ([top]) and below ([bottom]) a community's group, apart from the rows around it. */
@Composable
internal fun Modifier.groupGap(
    top: Boolean,
    bottom: Boolean,
): Modifier =
    padding(
        top = if (top) MaterialTheme.spacing.small else 0.dp,
        bottom = if (bottom) MaterialTheme.spacing.small else 0.dp,
    )

/** A community entry's key in the list. */
internal fun communityKey(id: Long): String = "community-$id"

private const val COMMUNITY_ROW = "community"
private const val COMMUNITY_CHAT_ROW = "communityChat"
