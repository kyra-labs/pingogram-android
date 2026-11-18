package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * The Channel feed (`REQUIREMENTS.md` FR-X5): the newest posts of the channels the user follows, newest first, each
 * with its channel as TDLib last described it, and how many channels that is.
 */
data class ChannelFeed(
    val posts: ImmutableList<FoundMessage>,
    val channelCount: Int,
)

/** Whether this post is unread in its channel: newer than the last post read there, as TDLib keeps it. */
val FoundMessage.isUnreadPost: Boolean
    get() = !message.isOutgoing && message.id > chat.lastReadInboxMessageId
