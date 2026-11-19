package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chats.ui.ChatListTimeFormatter
import dev.kyralabs.pingogram.feature.chats.ui.MessagePages
import dev.kyralabs.pingogram.feature.chats.ui.SearchRowSources
import dev.kyralabs.pingogram.feature.chats.ui.toSearchItem
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * The Channel feed's search (`REQUIREMENTS.md` FR-X5, owner decision 2026-11-19) as [uiState]: the posts in the user's
 * channels that match the query ([SearchRepository.searchMessages] in channels only), newest first, page by page,
 * leaving out channels the feed's channel filter leaves out while it is on ([FeedSearchEvent.ChannelsChosen]). A query
 * is searched once it has not changed for [DEBOUNCE_MILLIS]; a new one cancels the old one's pages. Rows are global
 * search's message rows ([toSearchItem]), mapped on the dispatcher. Tests replace the clock, time patterns,
 * dispatcher and language ([locale]).
 */
@HiltViewModel
class FeedSearchViewModel internal constructor(
    private val searchRepository: SearchRepository,
    userRepository: UserRepository,
    groupRepository: GroupRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale = Locale::getDefault,
) : ViewModel() {
    @Inject
    constructor(
        searchRepository: SearchRepository,
        userRepository: UserRepository,
        groupRepository: GroupRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(searchRepository, userRepository, groupRepository, clock, timePatterns, dispatcher, Locale::getDefault)

    private val search = MutableStateFlow(Search("", MessagePages.None))
    private val chosen = MutableStateFlow<Set<Long>?>(null)
    private var searching: Job? = null
    private var timeFormatter: ChatListTimeFormatter? = null

    private val sources =
        combine(
            userRepository.users,
            groupRepository.supergroups,
            groupRepository.basicGroups,
            userRepository.myUserId,
            clock.minutes,
        ) { users, supergroups, basicGroups, myUserId, now ->
            SearchRowSources(users, supergroups, basicGroups, myUserId, now, timeFormatter())
        }

    val uiState: StateFlow<FeedSearchUiState> =
        combine(search, chosen, sources, ::toUiState)
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FeedSearchUiState())

    fun onEvent(event: FeedSearchEvent) {
        when (event) {
            is FeedSearchEvent.QueryChanged -> search(event.query)
            is FeedSearchEvent.ChannelsChosen -> chosen.value = event.chosen
            FeedSearchEvent.LoadMore -> if (search.value.pages.canLoadMore) loadPage()
            FeedSearchEvent.Retry -> if (search.value.pages.error != null) loadPage()
        }
    }

    /** Searches [query] after [DEBOUNCE_MILLIS] without a change; a blank one shows nothing at once. */
    private fun search(query: String) {
        val text = query.trim()
        if (text == search.value.query) return
        searching?.cancel()
        search.value = Search(text, if (text.isEmpty()) MessagePages.None else MessagePages())
        if (text.isEmpty()) return
        searching =
            viewModelScope.launch {
                delay(DEBOUNCE_MILLIS)
                readPage(text, offset = null)
            }
    }

    /** Reads the page after the ones found, or again the page that failed. */
    private fun loadPage() {
        val current = search.value
        search.value = current.copy(pages = current.pages.copy(isLoading = true, error = null))
        searching = viewModelScope.launch { readPage(current.query, current.pages.nextOffset) }
    }

    private suspend fun readPage(
        query: String,
        offset: String?,
    ) {
        val page = searchRepository.searchMessages(query, offset, inChannels = true)
        search.update { current ->
            if (current.query != query) return@update current
            val pages =
                when (page) {
                    is Outcome.Success -> current.pages.adding(page.value)
                    is Outcome.Failure -> current.pages.copy(isLoading = false, error = page.error)
                }
            current.copy(pages = pages)
        }
    }

    /** Runs on the dispatcher: the rows of the posts found in the [chosen] channels (all for null). */
    private fun toUiState(
        search: Search,
        chosen: Set<Long>?,
        sources: SearchRowSources,
    ): FeedSearchUiState {
        val highlight = MatchHighlight.of(search.query)
        val pages = search.pages
        return FeedSearchUiState(
            query = search.query,
            results =
                pages.found
                    .filter { chosen == null || it.chat.id in chosen }
                    .map { it.toSearchItem(sources, highlight) }
                    .toImmutableList(),
            highlight = highlight,
            isLoading = pages.isLoading,
            canLoadMore = pages.canLoadMore,
            error = pages.error,
        )
    }

    /** The formatter for the current time zone, language and clock setting, which can change while the app runs. */
    private fun timeFormatter(): ChatListTimeFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return timeFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatListTimeFormatter(zone, locale, is24HourClock, timePatterns).also { timeFormatter = it }
    }

    /** The [query] searched and the [pages] of posts it found. */
    private data class Search(
        val query: String,
        val pages: MessagePages,
    )

    internal companion object {
        /** How long a query stays unchanged before it is searched. */
        const val DEBOUNCE_MILLIS = 300L

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** There is another page, and none is being read or failed. */
private val MessagePages.canLoadMore: Boolean get() = hasNext && !isLoading && error == null
