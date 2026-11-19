package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChannelFeed
import kotlinx.coroutines.flow.Flow

/**
 * The Channel feed (`REQUIREMENTS.md` FR-X5): the followed channels' newest posts as one timeline. Nothing is stored
 * beyond what TDLib keeps, and nothing polls: TDLib keeps each channel's last post and read state current, a
 * channel's newer posts are read when its last post changes, and the posts read follow whatever updates TDLib sends
 * for them (reactions and views, edits, deletions). The channels are never opened, so TDLib may send none: [refresh]
 * reads posts again when asked (owner decision 2026-11-19).
 *
 * Failures are `DomainError.Network` (no answer in time), `DomainError.FloodWait` and others as TDLib reports them.
 */
interface ChannelFeedRepository {
    /**
     * The feed, then every change: the posts read so far and every channel's last post, with their channels as they
     * are now (read states included). Before [load] it holds the channels' last posts alone.
     */
    val feed: Flow<ChannelFeed>

    /**
     * Reads the newest posts of every channel of the main list that TDLib has loaded, a page each; again to retry. A
     * channel that cannot be read keeps what it had; it fails only when no channel could be read.
     */
    suspend fun load(): Outcome<Unit>

    /**
     * Reads posts again, [posts] being their ids by channel, one `getMessages` per channel: a post TDLib returns takes
     * its place as it is now, and one it no longer returns leaves the feed. A channel that cannot be read keeps what it
     * had; it fails only when none could be read.
     */
    suspend fun refresh(posts: Map<Long, List<Long>>): Outcome<Unit>

    /**
     * Tells TDLib that posts [messageIds] of channel [chatId] were seen in the feed, though the channel is not open:
     * they are marked read there and their views counted. A channel's sponsored message is reported seen the same way.
     */
    suspend fun view(
        chatId: Long,
        messageIds: List<Long>,
    ): Outcome<Unit>
}
