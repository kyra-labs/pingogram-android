package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.model.FoundMessages
import dev.kyralabs.pingogram.core.domain.model.MessageSearchFilter
import dev.kyralabs.pingogram.core.domain.model.PublicPostSearchLimits
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** What search found, before [SearchViewModel] maps it to rows. */
internal sealed interface SearchState {
    /** Suggestions for an empty query; see [SearchContent.Suggestions]. */
    data class Suggestions(
        val topPeople: List<Chat> = emptyList(),
        val recents: List<Chat> = emptyList(),
        val isLoading: Boolean = true,
    ) : SearchState

    /** The sections of [query]'s results under [filter]; see [SearchContent.Results]. */
    data class Results(
        val query: String,
        val filter: SearchFilter = SearchFilter.All,
        val chats: SearchSection<Chat> = SearchSection.Loading,
        val contacts: SearchSection<Chat> = SearchSection.Loading,
        val global: SearchSection<Chat> = SearchSection.Loading,
        val messages: MessagePages = MessagePages(),
        val posts: PostGate = PostGate.None,
        val postLimits: PostLimits? = null,
    ) : SearchState {
        override fun toString() = "Results(chats=$chats, contacts=$contacts, global=$global, messages=$messages, …)"
    }
}

/**
 * Where a search of public channels' posts stands ([SearchFilter.Posts]) before its results, which come as the
 * messages' pages: Telegram's limits being read ([Checking]), a free search offered ([Offered]: it runs only on the
 * user's tap, as each spends one of the day's free searches), none left ([Exhausted]), or the limits not read
 * ([Failed]). [None] under the other filters, and once the posts are searched.
 */
internal sealed interface PostGate {
    data object None : PostGate

    data object Checking : PostGate

    data class Offered(
        val limits: PostLimits,
    ) : PostGate

    data class Exhausted(
        val limits: PostLimits,
    ) : PostGate

    data class Failed(
        val error: DomainError,
    ) : PostGate
}

/**
 * Telegram's limits of public post search as read at [readAt] (Unix time): [dailyFree] searches a day, [left] of them
 * left, the next free one at [nextFreeAt] (Unix time) once none are.
 */
internal data class PostLimits(
    val dailyFree: Int,
    val left: Int,
    val nextFreeAt: Long,
)

/**
 * The pages of messages found so far ([found], every page in order), what the next page starts from ([nextOffset],
 * null once there is none), whether the first page arrived ([hasFirstPage]), about how many match in all as TDLib
 * counted them with the last page ([totalCount], null while it does not know), and the state of the page asked for
 * last: [isLoading], or failed with [error].
 */
internal data class MessagePages(
    val found: PersistentList<FoundMessage> = persistentListOf(),
    val nextOffset: String? = null,
    val hasFirstPage: Boolean = false,
    val isLoading: Boolean = true,
    val error: DomainError? = null,
    val totalCount: Int? = null,
) {
    /** There is a page after the ones found. */
    val hasNext: Boolean get() = hasFirstPage && nextOffset != null

    /** These pages with [page] after them; a message found again is not repeated, as rows are keyed by message. */
    fun adding(page: FoundMessages): MessagePages {
        val known = found.mapTo(HashSet()) { it.chat.id to it.message.id }
        val new = page.messages.filter { (it.chat.id to it.message.id) !in known }
        return copy(
            found = found.addingAll(new),
            nextOffset = page.nextOffset,
            hasFirstPage = true,
            isLoading = false,
            totalCount = page.totalCount ?: totalCount,
        )
    }

    override fun toString() = "MessagePages(found=${found.size}, hasFirstPage=$hasFirstPage, isLoading=$isLoading, …)"

    companion object {
        /** No messages, none to load: a search whose filter leaves them out. */
        val None = MessagePages(hasFirstPage = true, isLoading = false)
    }
}

/**
 * The search of one [query] under [filter] (`REQUIREMENTS.md` FR-L6), the sections the filter shows searched at once
 * and each on its own (the others found nothing), in [scope]:
 * the scope of the query's flow, which a new query cancels, and with it every request still waiting (TDLib's late
 * answers are dropped). Its [state] changes as answers arrive.
 *
 * - **Chats** follow [SearchRepository.searchChats]: the chats on the device, then those the server adds.
 * - **Messages** load a page at a time: the first at once, the next on [loadMore] while there is one, never two at a
 *   time, and never after a page failed until [retry].
 * - **Retry** searches the failed sections again, and loads the failed page of messages again.
 */
internal class QuerySearch(
    private val query: String,
    private val filter: SearchFilter,
    private val repository: SearchRepository,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
) {
    private val mutableState = MutableStateFlow(SearchState.Results(query, filter))
    val state: StateFlow<SearchState.Results> = mutableState

    /** Searches every section the filter shows; the others find nothing. */
    fun start() {
        val nothing = SearchSection.Items(persistentListOf<Chat>())
        if (filter.searchesChats) {
            searchChats()
            searchGlobal()
        } else {
            mutableState.update { it.copy(chats = nothing, global = nothing) }
        }
        if (filter.searchesContacts) searchContacts() else mutableState.update { it.copy(contacts = nothing) }
        if (filter.messages != null) loadMessages() else mutableState.update { it.copy(messages = MessagePages.None) }
        if (filter == SearchFilter.Posts) checkPosts()
    }

    /** Searches the posts of public channels, where a free search is offered; spends one of the day's. */
    fun searchPosts() {
        if (mutableState.value.posts !is PostGate.Offered) return
        mutableState.update { it.copy(posts = PostGate.None, messages = MessagePages()) }
        loadPosts()
    }

    /** Searches the failed sections again, and loads the failed page of messages again. */
    fun retry() {
        val current = mutableState.value
        if (current.chats is SearchSection.Failed) searchChats()
        if (current.contacts is SearchSection.Failed) searchContacts()
        if (current.global is SearchSection.Failed) searchGlobal()
        if (current.posts is PostGate.Failed) checkPosts()
        if (current.messages.error != null) {
            if (filter == SearchFilter.Posts) {
                loadPosts()
            } else {
                loadMessages()
            }
        }
    }

    /** Loads the next page of messages, unless one loads, none is left, or the last one failed. */
    fun loadMore() {
        val pages = mutableState.value.messages
        if (!pages.hasNext || pages.isLoading || pages.error != null) return
        if (filter == SearchFilter.Posts) loadPosts() else loadMessages()
    }

    private fun searchChats() {
        mutableState.update { it.copy(chats = SearchSection.Loading) }
        scope.launch {
            repository.searchChats(query).collect { found ->
                mutableState.update { it.copy(chats = found.toSection()) }
            }
        }
    }

    private fun searchContacts() {
        mutableState.update { it.copy(contacts = SearchSection.Loading) }
        scope.launch {
            val found = repository.searchContacts(query)
            mutableState.update { it.copy(contacts = found.toSection()) }
        }
    }

    private fun searchGlobal() {
        mutableState.update { it.copy(global = SearchSection.Loading) }
        scope.launch {
            val found = repository.searchPublicChats(query)
            mutableState.update { it.copy(global = found.toSection()) }
        }
    }

    /** The first page, or the page after the last one loaded. */
    private fun loadMessages() {
        val offset = mutableState.value.messages.nextOffset
        mutableState.update { it.copy(messages = it.messages.copy(isLoading = true, error = null)) }
        scope.launch {
            val page = repository.searchMessages(query, offset, filter.messages ?: MessageSearchFilter.All)
            mutableState.update { state ->
                val pages = state.messages
                val loaded =
                    when (page) {
                        is Outcome.Success -> pages.adding(page.value)
                        is Outcome.Failure -> pages.copy(isLoading = false, error = page.error)
                    }
                state.copy(messages = loaded)
            }
        }
    }

    /** Reads Telegram's limits for the query: a free search offered, or none left. */
    private fun checkPosts() {
        mutableState.update { it.copy(posts = PostGate.Checking) }
        scope.launch {
            val gate =
                when (val limits = repository.publicPostSearchLimits(query)) {
                    is Outcome.Success -> {
                        val read = limits.value.read(now())
                        if (limits.value.isQueryFree) PostGate.Offered(read) else PostGate.Exhausted(read)
                    }

                    is Outcome.Failure -> {
                        PostGate.Failed(limits.error)
                    }
                }
            val read = (gate as? PostGate.Offered)?.limits ?: (gate as? PostGate.Exhausted)?.limits
            mutableState.update { it.copy(posts = gate, postLimits = read ?: it.postLimits) }
        }
    }

    /** The first page of posts, or the page after the last; none left ends them with the limits. */
    private fun loadPosts() {
        val offset = mutableState.value.messages.nextOffset
        mutableState.update { it.copy(messages = it.messages.copy(isLoading = true, error = null)) }
        scope.launch {
            val found = repository.searchPublicPosts(query, offset)
            mutableState.update { state ->
                val pages = state.messages
                when (found) {
                    is Outcome.Failure -> {
                        state.copy(messages = pages.copy(isLoading = false, error = found.error))
                    }

                    is Outcome.Success -> {
                        // Null when they did not change (the same query again).
                        val limits = found.value.limits?.read(now()) ?: state.postLimits
                        if (found.value.areLimitsExceeded && limits != null) {
                            val ended = if (pages.hasFirstPage) pages.ended() else MessagePages.None
                            state.copy(messages = ended, posts = PostGate.Exhausted(limits), postLimits = limits)
                        } else {
                            state.copy(messages = pages.adding(found.value.posts), postLimits = limits)
                        }
                    }
                }
            }
        }
    }
}

/** These limits as read at [now], the next free search's time made absolute. */
private fun PublicPostSearchLimits.read(now: Instant): PostLimits =
    PostLimits(dailyFreeQueries, remainingFreeQueries, now.epochSecond + nextFreeQueryInSeconds)

/** These pages, with no page after them. */
private fun MessagePages.ended() = copy(nextOffset = null, isLoading = false)

private fun Outcome<ImmutableList<Chat>>.toSection(): SearchSection<Chat> =
    when (this) {
        is Outcome.Success -> SearchSection.Items(value.distinctBy { it.id }.toPersistentList())
        is Outcome.Failure -> SearchSection.Failed(error)
    }
