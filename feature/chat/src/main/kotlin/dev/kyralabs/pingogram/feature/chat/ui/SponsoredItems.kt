package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toImmutableList

/**
 * A sponsored message placed in the conversation: its [card], after message [afterMessageId], or below every message
 * when that is [ChatSponsoredMessages.AT_END].
 */
internal class PlacedSponsored(
    val card: SponsoredCard,
    val afterMessageId: Long,
)

/** What [ChatSponsoredMessages] adds to the conversation: the [placed] sponsored messages and the open [sheet]. */
internal class SponsoredState(
    val placed: List<PlacedSponsored>,
    val sheet: SponsoredSheet?,
) {
    companion object {
        val None = SponsoredState(emptyList(), sheet = null)
    }
}

/**
 * The state with [ads]: its sheet, and its placed sponsored messages among the items, only where the items reach the
 * chat's newest message ([ChatContent.Content.reachedEnd]): not while an older window shows. The same instance when
 * nothing changes, so the list and the state flow skip it.
 */
internal fun ChatUiState.withSponsored(ads: SponsoredState): ChatUiState {
    val withSheet = if (sponsoredSheet == ads.sheet) this else copy(sponsoredSheet = ads.sheet)
    val content = content as? ChatContent.Content
    val items = content?.takeIf { it.reachedEnd }?.items?.withSponsored(ads.placed)
    return if (content == null || items == null || items === content.items) {
        withSheet
    } else {
        withSheet.copy(content = content.copy(items = items))
    }
}

/**
 * These items (newest first) with the [placed] sponsored messages: one at [ChatSponsoredMessages.AT_END] at index 0,
 * below every message; the others directly below the message they follow, at its index, so above the next day's date
 * separator, with that message's date. One whose message is not among the items does not show. The same instance when
 * none shows.
 */
internal fun ImmutableList<ChatItem>.withSponsored(placed: List<PlacedSponsored>): ImmutableList<ChatItem> {
    val positioned =
        placed.mapNotNull { ad ->
            val index =
                if (ad.afterMessageId == ChatSponsoredMessages.AT_END) {
                    0
                } else {
                    indexOfFirst { it.showsMessage(ad.afterMessageId) }
                }
            getOrNull(index)?.let { index to ChatItem.Sponsored(ad.card, it.date) }
        }
    if (positioned.isEmpty()) return this
    val result = toMutableList()
    // From the oldest place to the newest, so the indexes still to come stay right.
    for ((index, item) in positioned.sortedByDescending { it.first }) result.add(index, item)
    return result.toImmutableList()
}

/**
 * These placements (sponsored message id → the message it is placed below) with the next of [cards] below the newest
 * sent message of [history], if one is left that is not [hidden], and at least [messagesBetween] messages (not service
 * messages) came after the last one placed; else these. Not below a message being sent: its id changes once it is sent,
 * which would take the sponsored message away.
 */
internal fun PersistentMap<Long, Long>.placingNext(
    history: ChatHistory,
    cards: List<SponsoredCard>,
    messagesBetween: Int,
    hidden: Set<Long>,
): PersistentMap<Long, Long> {
    val next = cards.firstOrNull { it.id !in this && it.id !in hidden }
    val newest = history.messages.firstOrNull { it.sendingState == null }
    val last = values.maxOrNull()
    val isDue =
        last == null ||
            history.messages.takeWhile { it.id > last }.count { it.content !is MessageContent.Service } >=
            messagesBetween
    return if (next != null && newest != null && isDue) putting(next.id, newest.id) else this
}
