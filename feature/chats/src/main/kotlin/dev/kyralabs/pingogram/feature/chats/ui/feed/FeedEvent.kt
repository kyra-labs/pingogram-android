package dev.kyralabs.pingogram.feature.chats.ui.feed

import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/** What the user does in the Channel feed, sent to [FeedViewModel.onEvent]. */
sealed interface FeedEvent {
    /** Post [messageIds] of channel [chatId] has been 60% on screen for 1 second: it is read. */
    data class Seen(
        val chatId: Long,
        val messageIds: ImmutableList<Long>,
    ) : FeedEvent

    /** "Try again" after reading the newest posts failed. */
    data object Retry : FeedEvent

    /** "Show read posts" while there is nothing new. */
    data object ShowReadPosts : FeedEvent

    /** A card or its "Open in channel": the channel opens at message [messageId]. */
    data class Open(
        val chatId: Long,
        val messageId: Long,
    ) : FeedEvent

    /** An item of post [key]'s menu, or the same custom action of a screen reader: [action]. */
    data class Act(
        val key: String,
        val action: FeedPostAction,
    ) : FeedEvent

    /** The filter sheet's "Mark all as read": every unread post of the [chosen] channels (null for all) is read. */
    data class MarkAllAsRead(
        val chosen: ImmutableSet<Long>?,
    ) : FeedEvent

    /** The filter sheet's "Show N posts": the feed shows what [filter] lets through. */
    data class SetFilter(
        val filter: FeedFilter,
    ) : FeedEvent
}

/**
 * What a post's menu offers (`docs/DESIGN_SYSTEM.md` §6 "Channel feed", frame 6): [MarkAsRead] for an unread post,
 * [MarkChannelUnread] for a read one, whose channel then shows as unread in the chat list.
 */
enum class FeedPostAction {
    Open,
    Forward,
    CopyLink,
    Bookmark,
    MarkAsRead,
    MarkChannelUnread,
}

/** One-off effects of [FeedViewModel], handled by the screen once each. */
sealed interface FeedEffect {
    /** Opens the forward sheet for messages [messageIds] of channel [chatId]. */
    data class Forward(
        val chatId: Long,
        val messageIds: ImmutableList<Long>,
    ) : FeedEffect

    /** Puts [link] on the clipboard. */
    data class CopyLink(
        val link: String,
    ) : FeedEffect

    /** Says the post was bookmarked. */
    data object Bookmarked : FeedEffect

    /** Says an action failed with [error]. */
    data class ShowError(
        val error: DomainError,
    ) : FeedEffect

    /** Opens channel [chatId] at message [messageId]. */
    data class OpenPost(
        val chatId: Long,
        val messageId: Long,
    ) : FeedEffect
}
