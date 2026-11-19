package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.feature.chats.ui.SearchMessageItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The feed's search: the [query] searched (empty before one is typed), the posts found in the channels shown as global
 * search's message rows ([results]) with the query's words to [highlight], whether a page is being read ([isLoading])
 * or there is another to read ([canLoadMore]), and why the last page failed ([error]).
 */
@Immutable
data class FeedSearchUiState(
    val query: String = "",
    val results: ImmutableList<SearchMessageItem> = persistentListOf(),
    val highlight: MatchHighlight = MatchHighlight.None,
    val isLoading: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: DomainError? = null,
) {
    /** The query found no posts in the channels shown. */
    val isEmpty: Boolean
        get() = query.isNotEmpty() && results.isEmpty() && !isLoading && !canLoadMore && error == null
}
