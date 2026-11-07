package dev.kyralabs.pingogram.feature.chats.ui

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * How many messages TDLib counted for [query], by the chip that shows them ([SearchUiState.counts]): only from the
 * searches already made, never a request of its own. A count for another query is dropped as soon as one for this
 * query comes.
 */
internal data class SearchCounts(
    val query: String = "",
    val counts: PersistentMap<SearchFilter, Int> = persistentMapOf(),
) {
    /** These counts with [count] for [chip], or only it once [query] changed. */
    fun with(
        query: String,
        chip: SearchFilter,
        count: Int,
    ): SearchCounts =
        if (query == this.query) {
            copy(counts = counts.put(chip, count))
        } else {
            SearchCounts(query, persistentMapOf(chip to count))
        }

    /** The counts for [query]; none for another one. */
    fun of(query: String): PersistentMap<SearchFilter, Int> = if (query == this.query) counts else persistentMapOf()

    override fun toString() = "SearchCounts(${counts.size} chips)"
}

/**
 * The chip whose count a search under this filter gives: All searches every message, as Messages does; Media and
 * Links count their own; Chats and Contacts search no messages.
 */
internal val SearchFilter.countedChip: SearchFilter?
    get() =
        when (this) {
            SearchFilter.All, SearchFilter.Messages -> SearchFilter.Messages
            SearchFilter.Media -> SearchFilter.Media
            SearchFilter.Links -> SearchFilter.Links
            SearchFilter.Chats, SearchFilter.Contacts -> null
        }

/** What the query was for [this] state: empty for the suggestions. */
internal val SearchState.query: String
    get() =
        when (this) {
            is SearchState.Suggestions -> ""
            is SearchState.Results -> query
        }
