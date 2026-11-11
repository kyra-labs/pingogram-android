package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/** A message that a search across chats found (`REQUIREMENTS.md` FR-L6), with the [chat] it is in, for its row. */
data class FoundMessage(
    val message: Message,
    val chat: Chat,
)

/** A page of messages found by a search across chats, from `SearchRepository.searchMessages`. */
data class FoundMessages(
    /** Newest first, as TDLib returns them (by date, then chat and message id); never the same message twice. */
    val messages: ImmutableList<FoundMessage>,
    /** What to pass to the next call for the page after this one; null once there is nothing more to find. */
    val nextOffset: String?,
    /** About how many messages match in all, as TDLib counts them; null while it does not know. */
    val totalCount: Int?,
)

/** Which messages a search across chats finds (`SearchRepository.searchMessages`), as search's filter chips choose. */
enum class MessageSearchFilter {
    /** Every message that matches the query. */
    All,

    /** Photos and videos only; with a blank query, every photo and video. */
    PhotoAndVideo,

    /** Messages with a link only; with a blank query, every message with one. */
    Url,
}

/**
 * How many public post searches Telegram lets the user make (td_api.tl `publicPostSearchLimits`): [dailyFreeQueries]
 * a day, [remainingFreeQueries] of them left, the next free one in [nextFreeQueryInSeconds] once none are left, and
 * whether searching the query asked about costs nothing ([isQueryFree]: one left, or the same query again). Searching
 * beyond them costs [starCount] Telegram Stars, which Pingogram never pays.
 */
data class PublicPostSearchLimits(
    val dailyFreeQueries: Int,
    val remainingFreeQueries: Int,
    val nextFreeQueryInSeconds: Int,
    val starCount: Long,
    val isQueryFree: Boolean,
)

/**
 * A page of posts of public channels found by `SearchRepository.searchPublicPosts`, with the [limits] after it (null
 * when they did not change, as for the same query again); [areLimitsExceeded] when Telegram found nothing because no
 * free search was left.
 */
data class FoundPublicPosts(
    val posts: FoundMessages,
    val limits: PublicPostSearchLimits?,
    val areLimitsExceeded: Boolean,
)
