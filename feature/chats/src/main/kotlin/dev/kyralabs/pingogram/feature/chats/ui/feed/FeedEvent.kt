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

/** What the user does to a channel's sponsored message in the feed, as in the conversation. */
sealed interface FeedAdEvent : FeedEvent {
    /** The whole card of ad [id] of channel [chatId] is on screen: TDLib hears it was viewed, once. */
    data class Viewed(
        val chatId: Long,
        val id: Long,
    ) : FeedAdEvent

    /** The user opened ad [id]: its button, or its media ([isMediaClick]); the sponsor's link opens. */
    data class Open(
        val chatId: Long,
        val id: Long,
        val isMediaClick: Boolean = false,
    ) : FeedAdEvent

    /** Open "About this ad" of ad [id]. */
    data class ShowInfo(
        val chatId: Long,
        val id: Long,
    ) : FeedAdEvent

    /** Close "About this ad". */
    data object DismissInfo : FeedAdEvent

    /** Report the ad "About this ad" shows. */
    data object Report : FeedAdEvent

    /** Report it again with the option at [index] of the report step. */
    data class ChooseReportOption(
        val index: Int,
    ) : FeedAdEvent

    /** Back from the report step to the ad's information. */
    data object BackToInfo : FeedAdEvent
}

/** What a report of a sponsored message came to ([FeedEffect.AdNotice]), as the conversation says it. */
enum class FeedAdNotice {
    Reported,
    AdsHidden,
    PremiumRequired,
    ReportFailed,
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

    /** Opens a sponsor's link. */
    data class OpenUrl(
        val url: String,
    ) : FeedEffect {
        override fun toString() = "OpenUrl(…)"
    }

    /** Says what a report of a sponsored message came to. */
    data class AdNotice(
        val notice: FeedAdNotice,
    ) : FeedEffect

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
