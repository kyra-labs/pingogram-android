package dev.kyralabs.pingogram.feature.chats.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/**
 * Global search (`REQUIREMENTS.md` FR-L6) as [uiState]: suggestions for an empty query, the results of the query
 * otherwise. Events come in through [onEvent]; navigation and snackbars go out through [effects].
 *
 * - **Query**: kept in the [SavedStateHandle] (so it survives process death), trimmed, and searched once it has not
 *   changed for [DEBOUNCE_MILLIS]; an empty query shows the suggestions at once. A new query cancels the searches of
 *   the previous one (`flatMapLatest`), waiting TDLib requests included (`docs/PERFORMANCE.md` §3).
 * - **Suggestions**: the top people and the recently found chats ([SearchRepository.recentChats]), read again after
 *   one is added, removed or cleared. A failure to read them shows none; a failure to change them, a snackbar.
 * - **Results** ([QuerySearch]): chats, contacts not among the chats, public chats among neither, and messages, each
 *   section with its own loading, error and empty state; messages page on [SearchEvent.LoadMoreMessages], one page at
 *   a time, never retried by themselves. [SearchEvent.Retry] searches every failed section and page again.
 * - **Rows** are mapped on [dispatcher] as the chat list maps its own ([toSearchItem]), with the users, groups, current
 *   user and minute they show, so names, badges and online dots follow TDLib's updates while search shows them. Times
 *   and counts follow the zone, the language and the 12- or 24-hour setting, checked at every emission.
 * - **Opening** a chat ([SearchEvent.OpenChat]) adds it to the recent chats; a message opens its conversation at it.
 *   Searches run only while the screen collects [uiState], and 5 s after, so a rotation does not restart them.
 *
 * It takes the repositories the rows combine, plus the clock, time patterns, dispatcher and language that tests
 * replace, hence `LongParameterList` is suppressed.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel internal constructor(
    private val savedStateHandle: SavedStateHandle,
    private val searchRepository: SearchRepository,
    userRepository: UserRepository,
    groupRepository: GroupRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        searchRepository: SearchRepository,
        userRepository: UserRepository,
        groupRepository: GroupRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        savedStateHandle,
        searchRepository,
        userRepository,
        groupRepository,
        clock,
        timePatterns,
        dispatcher,
        Locale::getDefault,
    )

    /** What the results of the current query are asked to do; dropped while none are searched. */
    private val commands = MutableSharedFlow<Command>(extraBufferCapacity = COMMAND_BUFFER)

    /** Changes whenever the recent chats changed, which reads them again. */
    private val recentsVersion = MutableStateFlow(0)

    private var timeFormatter: ChatListTimeFormatter? = null

    private val effectChannel = Channel<SearchEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: opening a chat or a message, and snackbars. */
    val effects: Flow<SearchEffect> = effectChannel.receiveAsFlow()

    private val search: Flow<SearchState> =
        savedStateHandle
            .getStateFlow(KEY_QUERY, "")
            .map { it.trim() }
            .distinctUntilChanged()
            .debounce { if (it.isEmpty()) 0L else DEBOUNCE_MILLIS }
            .distinctUntilChanged()
            .flatMapLatest { query -> if (query.isEmpty()) suggestions() else results(query) }

    private val people =
        combine(
            userRepository.users,
            groupRepository.supergroups,
            groupRepository.basicGroups,
            userRepository.myUserId,
            clock.minutes,
        ) { users, supergroups, basicGroups, myUserId, now ->
            SearchRowSources(users, supergroups, basicGroups, myUserId, now, timeFormatter())
        }

    val uiState: StateFlow<SearchUiState> =
        combine(search, people, ::toUiState)
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchUiState())

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> savedStateHandle[KEY_QUERY] = event.query
            SearchEvent.ClearQuery -> savedStateHandle[KEY_QUERY] = ""
            is SearchEvent.OpenChat -> openChat(event.chatId)
            is SearchEvent.OpenMessage -> effectChannel.trySend(SearchEffect.OpenMessage(event.chatId, event.messageId))
            is SearchEvent.RemoveRecent -> changeRecents { searchRepository.removeRecentChat(event.chatId) }
            SearchEvent.ClearRecents -> changeRecents { searchRepository.clearRecentChats() }
            SearchEvent.LoadMoreMessages -> commands.tryEmit(Command.LoadMore)
            SearchEvent.Retry -> commands.tryEmit(Command.Retry)
        }
    }

    /** Opens chat [chatId] at once, and puts it at the top of the recent chats; that failing changes nothing. */
    private fun openChat(chatId: Long) {
        effectChannel.trySend(SearchEffect.OpenChat(chatId))
        viewModelScope.launch {
            if (searchRepository.addRecentChat(chatId) is Outcome.Success) recentsVersion.update { it + 1 }
        }
    }

    /** Changes the recent chats with [change], then reads them again; a failure shows its error. */
    private fun changeRecents(change: suspend () -> Outcome<Unit>) {
        viewModelScope.launch {
            when (val result = change()) {
                is Outcome.Success -> recentsVersion.update { it + 1 }
                is Outcome.Failure -> effectChannel.trySend(SearchEffect.ShowError(result.error))
            }
        }
    }

    /** The top people, read once, and the recent chats, read again whenever they changed. */
    private fun suggestions(): Flow<SearchState> {
        val topPeople = flow { emit(searchRepository.topPeople().getOrNull().orEmpty()) }
        val recents = recentsVersion.mapLatest { searchRepository.recentChats().getOrNull().orEmpty() }
        return combine(topPeople, recents) { top, recent -> SearchState.Suggestions(top, recent, isLoading = false) }
            .onStart { emit(SearchState.Suggestions()) }
    }

    /** The results of [query], which [commands] retry and page while they are shown. */
    private fun results(query: String): Flow<SearchState> =
        channelFlow {
            val search = QuerySearch(query, searchRepository, this)
            search.start()
            launch {
                commands.collect { command ->
                    when (command) {
                        Command.Retry -> search.retry()
                        Command.LoadMore -> search.loadMore()
                    }
                }
            }
            search.state.collect { send(it) }
        }

    /** Runs on [dispatcher]: maps [state] to rows with [sources]. */
    private fun toUiState(
        state: SearchState,
        sources: SearchRowSources,
    ): SearchUiState =
        SearchUiState(
            when (state) {
                is SearchState.Suggestions -> {
                    SearchContent.Suggestions(
                        topPeople = state.topPeople.toItems(sources),
                        recents = state.recents.toItems(sources),
                        isLoading = state.isLoading,
                    )
                }

                is SearchState.Results -> {
                    results(state, sources)
                }
            },
        )

    /** The sections of [state]: contacts not among the chats, public chats among neither. */
    private fun results(
        state: SearchState.Results,
        sources: SearchRowSources,
    ): SearchContent.Results {
        val highlight = MatchHighlight.of(state.query)
        val chatIds = state.chats.ids()
        val contacts = state.contacts.filterItems { it.id !in chatIds }
        val contactIds = contacts.ids()
        val global = state.global.filterItems { it.id !in chatIds && it.id !in contactIds }
        return SearchContent.Results(
            query = state.query,
            highlight = highlight,
            chats = state.chats.mapItems { it.toSearchItem(sources) },
            contacts = contacts.mapItems { it.toSearchItem(sources) },
            global = global.mapItems { it.toSearchItem(sources) },
            messages = state.messages.toSection(sources, highlight),
        )
    }

    private fun List<Chat>.toItems(sources: SearchRowSources): ImmutableList<SearchChatItem> =
        map { it.toSearchItem(sources) }.toImmutableList()

    /** The formatter for the current time zone, language and clock setting, which can change while the app runs. */
    private fun timeFormatter(): ChatListTimeFormatter {
        val zone: ZoneId = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return timeFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatListTimeFormatter(zone, locale, is24HourClock, timePatterns).also { timeFormatter = it }
    }

    private enum class Command {
        Retry,
        LoadMore,
    }

    internal companion object {
        /** Where the query is kept in the [SavedStateHandle]. */
        const val KEY_QUERY = "search.query"

        /** How long the query must stay the same before it is searched: typing pauses about this long. */
        const val DEBOUNCE_MILLIS = 300L

        private const val COMMAND_BUFFER = 8
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The messages section of these pages: loading or failed until the first page came. */
private fun MessagePages.toSection(
    sources: SearchRowSources,
    highlight: MatchHighlight,
): MessageSection {
    val error = error
    return MessageSection(
        first =
            when {
                hasFirstPage -> SearchSection.Items(found.map { it.toSearchItem(sources, highlight) }.toImmutableList())
                error != null -> SearchSection.Failed(error)
                else -> SearchSection.Loading
            },
        canLoadMore = hasNext,
        isLoadingMore = hasFirstPage && isLoading,
        moreError = error.takeIf { hasFirstPage },
    )
}

private fun SearchSection<Chat>.ids(): Set<Long> =
    (this as? SearchSection.Items)
        ?.items
        ?.mapTo(HashSet()) { it.id }
        .orEmpty()

private inline fun <T> SearchSection<T>.filterItems(predicate: (T) -> Boolean): SearchSection<T> =
    if (this is SearchSection.Items) SearchSection.Items(items.filter(predicate).toImmutableList()) else this

private inline fun <T, R> SearchSection<T>.mapItems(transform: (T) -> R): SearchSection<R> =
    when (this) {
        is SearchSection.Items -> SearchSection.Items(items.map(transform).toImmutableList())
        is SearchSection.Failed -> this
        SearchSection.Loading -> SearchSection.Loading
    }
