package dev.kyralabs.pingogram.feature.chats.ui.feed

/** What the feed's search asks of [FeedSearchViewModel]. */
sealed interface FeedSearchEvent {
    /** The search field now holds [query]. */
    data class QueryChanged(
        val query: String,
    ) : FeedSearchEvent

    /** The feed's channel filter shows the [chosen] channels, or all of them for null. */
    data class ChannelsChosen(
        val chosen: Set<Long>?,
    ) : FeedSearchEvent

    /** The end of the results is near: read the next page. */
    data object LoadMore : FeedSearchEvent

    /** Read the page that failed again. */
    data object Retry : FeedSearchEvent
}
