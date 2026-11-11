package dev.kyralabs.pingogram.feature.chat.ui.directmessages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.DirectMessagesTopic
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.DirectMessagesTopicRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.text.shortName
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatTimeFormatter
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

/**
 * A channel's direct messages as its administrators see them (the owner's board 7b; td_api.tl
 * `directMessagesChatTopic`): one row per person who wrote, by TDLib's order.
 *
 * - **Loading**: the first page as the screen collects, the next on [DirectMessagesEvent.LoadMore] while there are
 *   more, one at a time; a failed first page shows its error, which [DirectMessagesEvent.Retry] asks again.
 * - **Filtering**: All, Unread (unread messages or marked unread), Paid (senders who pay per message), and a search of
 *   the senders' names on the device; both in the saved state.
 * - **Rows**: the sender's name and avatar (a user, or a chat writing as itself), the last message's preview and time
 *   in the zone, language and clock setting, the unread count.
 * - **Marking** a conversation unread or read is [DirectMessagesTopicRepository.setMarkedAsUnread]; a refusal is
 *   [effects]' snackbar.
 */
@Suppress("LongParameterList") // The channel, its topics and people, the clock and formats that tests replace.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = DirectMessagesViewModel.Factory::class)
class DirectMessagesViewModel internal constructor(
    private val chatId: Long,
    chatRepository: ChatRepository,
    private val repository: DirectMessagesTopicRepository,
    userRepository: UserRepository,
    private val savedState: SavedStateHandle,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
) : ViewModel() {
    @AssistedInject
    constructor(
        @Assisted chatId: Long,
        chatRepository: ChatRepository,
        repository: DirectMessagesTopicRepository,
        userRepository: UserRepository,
        savedState: SavedStateHandle,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        chatId,
        chatRepository,
        repository,
        userRepository,
        savedState,
        clock,
        timePatterns,
        dispatcher,
        Locale::getDefault,
    )

    /** Creates the view model of the direct messages of chat `chatId`. */
    @AssistedFactory
    interface Factory {
        fun create(chatId: Long): DirectMessagesViewModel
    }

    private val load = MutableStateFlow(DirectLoad())

    private val effectChannel = Channel<DomainError>(Channel.BUFFERED)

    /** Refusals to show as snackbars. */
    val effects: Flow<DomainError> = effectChannel.receiveAsFlow()

    private var timeFormatter: ChatTimeFormatter? = null

    private val topics = repository.topics(chatId)

    /** The chats that write as themselves, by id, looked up for their names and photos. */
    private val senderChats: Flow<Map<Long, Chat>> =
        topics
            .map { list -> list.mapNotNull { (it.sender as? MessageSender.Chat)?.chatId }.distinct() }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(ids.map(chatRepository::chat)) { chats -> chats.filterNotNull().associateBy { it.id } }
                }
            }

    private val people =
        combine(userRepository.users, userRepository.myUserId, senderChats, ::DirectPeople)

    private val filters =
        combine(
            savedState.getStateFlow(KEY_FILTER, DirectFilter.All.name).map(DirectFilter::of),
            savedState.getStateFlow(KEY_QUERY, ""),
            ::Pair,
        )

    val uiState: StateFlow<DirectMessagesUiState> =
        combine(chatRepository.chat(chatId), topics, people, filters, combine(load, clock.minutes, ::Pair)) {
            chat,
            topics,
            people,
            (filter, query),
            (load, now),
            ->
            toUiState(chat, topics, people, filter, query, load, now)
        }.onStart { startLoading() }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DirectMessagesUiState())

    fun onEvent(event: DirectMessagesEvent) {
        when (event) {
            is DirectMessagesEvent.FilterChanged -> savedState[KEY_FILTER] = event.filter.name
            is DirectMessagesEvent.QueryChanged -> savedState[KEY_QUERY] = event.query
            is DirectMessagesEvent.MarkUnread -> markUnread(event.topicId, event.unread)
            DirectMessagesEvent.LoadMore -> if (load.value.canLoadMore) loadPage()
            DirectMessagesEvent.Retry -> loadPage()
        }
    }

    /** The first page, once per view model. */
    private fun startLoading() {
        if (!load.value.started) loadPage()
    }

    private fun loadPage() {
        if (load.value.isLoading) return
        load.update { it.copy(started = true, isLoading = true, error = null) }
        viewModelScope.launch {
            val answer = repository.loadMore(chatId)
            load.update {
                when (answer) {
                    is Outcome.Success -> it.copy(isLoading = false, hasMore = answer.value)
                    is Outcome.Failure -> it.copy(isLoading = false, error = answer.error)
                }
            }
        }
    }

    private fun markUnread(
        topicId: Long,
        unread: Boolean,
    ) {
        viewModelScope.launch {
            val answer = repository.setMarkedAsUnread(chatId, topicId, unread)
            if (answer is Outcome.Failure) effectChannel.trySend(answer.error)
        }
    }

    /** Runs on the dispatcher: the header and the rows under the filter and the search. */
    @Suppress("LongParameterList") // What the list combines.
    private fun toUiState(
        chat: Chat?,
        topics: List<DirectMessagesTopic>,
        people: DirectPeople,
        filter: DirectFilter,
        query: String,
        load: DirectLoad,
        now: Instant,
    ): DirectMessagesUiState {
        val unread = topics.count { it.isUnread }
        val formatter = timeFormatter()
        val locale = locale()
        val words = query.trim().lowercase(locale)
        val rows =
            topics
                .filter { filter.keeps(it) }
                .map { it.toRow(people, formatter, now) }
                .filter { words.isEmpty() || it.searchName.lowercase(locale).contains(words) }
        val content =
            when {
                rows.isNotEmpty() -> DirectContent.Rows(rows.map { it.row }.toImmutableList(), load.isLoading)
                load.error != null && topics.isEmpty() -> DirectContent.Failed(load.error)
                !load.started || (load.isLoading && topics.isEmpty()) -> DirectContent.Loading
                else -> DirectContent.Empty(isFiltered = topics.isNotEmpty())
            }
        return DirectMessagesUiState(
            chat?.let { headerOf(it, topics.size, unread) },
            filter,
            topics.size,
            unread,
            content,
        )
    }

    /** The formatter for the current time zone, language and clock setting, which can change while the app runs. */
    private fun timeFormatter(): ChatTimeFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return timeFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatTimeFormatter(zone, locale, is24HourClock, timePatterns).also { timeFormatter = it }
    }

    internal companion object {
        const val KEY_FILTER = "direct.filter"
        const val KEY_QUERY = "direct.query"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Where loading the topics stands: [started], a page [isLoading], whether [hasMore] may follow, the last [error]. */
private data class DirectLoad(
    val started: Boolean = false,
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val error: DomainError? = null,
) {
    val canLoadMore: Boolean get() = started && hasMore && !isLoading && error == null
}

/** Who the rows name: the [users], the current user ([myUserId]) and the chats writing as themselves ([chats]). */
private data class DirectPeople(
    val users: Map<Long, User>,
    val myUserId: Long?,
    val chats: Map<Long, Chat>,
)

/** A row and the name its search matches, which the row keeps as [UiText]. */
private class NamedRow(
    val row: DirectRow,
    val searchName: String,
)

private fun DirectFilter.keeps(topic: DirectMessagesTopic): Boolean =
    when (this) {
        DirectFilter.All -> true
        DirectFilter.Unread -> topic.isUnread
        DirectFilter.Paid -> topic.isPaid
    }

private fun DirectMessagesTopic.toRow(
    people: DirectPeople,
    formatter: ChatTimeFormatter,
    now: Instant,
): NamedRow {
    val user = (sender as? MessageSender.User)?.let { people.users[it.userId] }
    val chat = (sender as? MessageSender.Chat)?.let { people.chats[it.chatId] }
    val name =
        user?.fullName() ?: chat?.title?.let(UiText::Plain) ?: UiText.resource(R.string.direct_sender_unknown)
    val searchName = listOfNotNull(user?.firstName, user?.lastName, chat?.title).joinToString(" ")
    val row =
        DirectRow(
            topicId = id,
            name = name,
            photo = user?.profilePhoto ?: chat?.photo,
            avatarId = (sender as? MessageSender.User)?.userId ?: (sender as MessageSender.Chat).chatId,
            avatarShape = if (chat != null) AvatarShape.Channel else AvatarShape.Circle,
            preview =
                lastMessage?.let { last ->
                    messagePreviewText(last, people.myUserId) { who ->
                        when (who) {
                            is MessageSender.User -> people.users[who.userId]?.shortName()
                            is MessageSender.Chat -> people.chats[who.chatId]?.title?.let(UiText::Plain)
                        }
                    }
                },
            time = lastMessage?.let { formatter.listTime(it.date, now) },
            unreadCount = unreadCount.toInt(),
            isUnread = isUnread,
            isPaid = isPaid,
        )
    return NamedRow(row, searchName)
}

/** The channel's avatar and name over "12 people wrote · 4 unread". */
private fun headerOf(
    chat: Chat,
    total: Int,
    unread: Int,
): ChatHeader {
    val wrote = UiText.plural(R.plurals.direct_subtitle_people, total, total)
    val subtitle = if (unread > 0) UiText.plural(R.plurals.direct_subtitle_unread, unread, wrote, unread) else wrote
    return ChatHeader(
        chatId = chat.id,
        title = UiText.Plain(chat.title),
        photo = chat.photo,
        placeholderIcon = null,
        subtitle = ChatSubtitle.Status(subtitle),
        avatarShape = AvatarShape.Channel,
    )
}
