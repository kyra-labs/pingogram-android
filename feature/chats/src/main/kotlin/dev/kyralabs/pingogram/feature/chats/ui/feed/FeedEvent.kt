package dev.kyralabs.pingogram.feature.chats.ui.feed

import kotlinx.collections.immutable.ImmutableList

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

    /** The filter sheet's "Show N posts": the feed shows what [filter] lets through. */
    data class SetFilter(
        val filter: FeedFilter,
    ) : FeedEvent
}

/** One-off effects of [FeedViewModel], handled by the screen once each. */
sealed interface FeedEffect {
    /** Opens channel [chatId] at message [messageId]. */
    data class OpenPost(
        val chatId: Long,
        val messageId: Long,
    ) : FeedEffect
}
