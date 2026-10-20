package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.model.FoundMessages
import dev.kyralabs.pingogram.core.domain.model.MessageSearchFilter
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
    ) : SearchState {
        override fun toString() = "Results(chats=$chats, contacts=$contacts, global=$global, messages=$messages, …)"
    }
}

/**
 * The pages of messages found so far ([found], every page in order), what the next page starts from ([nextOffset],
 * null once there is none), whether the first page arrived ([hasFirstPage]), and the state of the page asked for last:
 * [isLoading], or failed with [error].
 */
internal data class MessagePages(
    val found: PersistentList<FoundMessage> = persistentListOf(),
    val nextOffset: String? = null,
    val hasFirstPage: Boolean = false,
    val isLoading: Boolean = true,
    val error: DomainError? = null,
) {
    /** There is a page after the ones found. */
    val hasNext: Boolean get() = hasFirstPage && nextOffset != null

    /** These pages with [page] after them; a message found again is not repeated, as rows are keyed by message. */
    fun adding(page: FoundMessages): MessagePages {
        val known = found.mapTo(HashSet()) { it.chat.id to it.message.id }
        val new = page.messages.filter { (it.chat.id to it.message.id) !in known }
        return copy(found = found.addingAll(new), nextOffset = page.nextOffset, hasFirstPage = true, isLoading = false)
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
    }

    /** Searches the failed sections again, and loads the failed page of messages again. */
    fun retry() {
        val current = mutableState.value
        if (current.chats is SearchSection.Failed) searchChats()
        if (current.contacts is SearchSection.Failed) searchContacts()
        if (current.global is SearchSection.Failed) searchGlobal()
        if (current.messages.error != null) loadMessages()
    }

    /** Loads the next page of messages, unless one loads, none is left, or the last one failed. */
    fun loadMore() {
        val pages = mutableState.value.messages
        if (pages.hasNext && !pages.isLoading && pages.error == null) loadMessages()
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
}

private fun Outcome<ImmutableList<Chat>>.toSection(): SearchSection<Chat> =
    when (this) {
        is Outcome.Success -> SearchSection.Items(value.distinctBy { it.id }.toPersistentList())
        is Outcome.Failure -> SearchSection.Failed(error)
    }
