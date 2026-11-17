package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.domain.repository.PinnedMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.PinnedMessagesRoute
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.ChatPeople
import dev.kyralabs.pingogram.feature.chat.ui.ChatRef
import dev.kyralabs.pingogram.feature.chat.ui.ChatTimeFormatter
import dev.kyralabs.pingogram.feature.chat.ui.chatRef
import dev.kyralabs.pingogram.feature.chat.ui.textOrCaption
import kotlinx.collections.immutable.ImmutableList
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/**
 * The pinned messages page of the route's chat (its forum topic or thread, if the route names one): every pinned
 * message
 * newest first under its month ([pinnedMonths]), numbered from the oldest, filtered by kind ([PinnedFilter], the chips
 * counting each) and searched on the device; the filter, the search and its words are kept in the saved state.
 *
 * - **Pins** come from [PinnedMessageRepository.pinnedMessagesOrError] and follow every pin and unpin; a failed load is
 *   the page's error, which [PinnedPageEvent.Retry] loads again.
 * - **Jump to message** hands the message to the conversation below ([PinnedJumps]) and leaves
 * ([PinnedPageEffect.Leave]).
 * - **Copy** sends the message's text to the clipboard ([PinnedPageEffect.CopyText]).
 * - **Unpin** where the user may (TDLib's `can_be_pinned` of the newest pin, [MessageActionRepository.properties]);
 * "Unpin
 *   all" asks first ([PinnedPageUiState.confirmUnpinAll]), for a whole chat only, as td_api.tl's `unpinAllChatMessages`
 *   is. Refusals are snackbars ([PinnedPageEffect.ShowError]).
 *
 * Mapped on [dispatcher], while the screen collects and 5 s after.
 */
@Suppress("LongParameterList") // The chat and where its pins come from and go, and the clock and formats tests replace.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PinnedMessagesViewModel internal constructor(
    private val chatId: Long,
    private val topic: MessageTopic?,
    private val savedState: SavedStateHandle,
    private val repository: PinnedMessageRepository,
    chatRepository: ChatRepository,
    userRepository: UserRepository,
    private val actions: MessageActionRepository,
    private val jumps: PinnedJumps,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: PinnedMessageRepository,
        chatRepository: ChatRepository,
        userRepository: UserRepository,
        actions: MessageActionRepository,
        jumps: PinnedJumps,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        savedStateHandle.toRoute<PinnedMessagesRoute>().chatId,
        savedStateHandle.toRoute<PinnedMessagesRoute>().topic,
        savedStateHandle,
        repository,
        chatRepository,
        userRepository,
        actions,
        jumps,
        clock,
        timePatterns,
        dispatcher,
        Locale::getDefault,
    )

    private val effectChannel = Channel<PinnedPageEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: leaving, the clipboard, snackbars. */
    val effects: Flow<PinnedPageEffect> = effectChannel.receiveAsFlow()

    /** Each retry loads the pins again. */
    private val attempts = MutableStateFlow(0)

    private val confirmUnpinAll = MutableStateFlow(false)

    private var timeFormatter: ChatTimeFormatter? = null

    /** The pins, newest first, or why they could not be loaded; null while they load. */
    private val pins: Flow<Outcome<ImmutableList<Message>>?> =
        attempts
            .flatMapLatest {
                repository
                    .pinnedMessagesOrError(
                        chatId,
                        topic,
                    ).map<_, Outcome<ImmutableList<Message>>?> { it }
                    .onStart {
                        emit(null)
                    }
            }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** Whether the user may unpin the chat's messages, asked of TDLib for the newest pin. */
    private val canUnpin: Flow<Boolean> =
        pins
            .map { it?.getOrNull()?.firstOrNull()?.id }
            .distinctUntilChanged()
            .mapLatest { id -> id != null && actions.properties(chatId, id).getOrNull()?.canBePinned == true }
            .onStart { emit(false) }

    /**
     * The chats that sent pins other than this one, by id: a linked channel, whose posts its discussion group shows as
     * the channel's, named by its title.
     */
    private val senderChats: Flow<Map<Long, ChatRef>> =
        pins
            .map { pins -> pins?.getOrNull().orEmpty().senderChatIds(except = chatId) }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(ids.map(chatRepository::chatRef)) { refs -> refs.filterNotNull().associateBy { it.id } }
                }
            }

    private val people: Flow<ChatPeople> =
        combine(
            userRepository.users,
            userRepository.myUserId,
            senderChats,
        ) { users, myUserId, chats -> ChatPeople(users, myUserId, chats) }

    private val filters: Flow<Filters> =
        combine(
            savedState.getStateFlow(KEY_FILTER, PinnedFilter.All),
            savedState.getStateFlow(KEY_SEARCHING, false),
            savedState.getStateFlow(KEY_QUERY, ""),
            ::Filters,
        )

    val uiState: StateFlow<PinnedPageUiState> =
        combine(
            combine(pins, chatRepository.chat(chatId).filterNotNull(), people, ::Sources),
            filters,
            canUnpin,
            confirmUnpinAll,
            clock.minutes,
        ) { sources, filters, canUnpin, confirm, now ->
            state(sources, filters, canUnpin, confirm, now)
        }.flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PinnedPageUiState())

    fun onEvent(event: PinnedPageEvent) {
        when (event) {
            is PinnedPageEvent.SelectFilter -> savedState[KEY_FILTER] = event.filter
            PinnedPageEvent.OpenSearch -> savedState[KEY_SEARCHING] = true
            PinnedPageEvent.CloseSearch -> closeSearch()
            is PinnedPageEvent.Search -> savedState[KEY_QUERY] = event.query
            is PinnedPageEvent.Jump -> jump(event.messageId)
            is PinnedPageEvent.Copy -> copy(event.messageId)
            is PinnedPageEvent.Unpin -> unpin(event.messageId)
            PinnedPageEvent.UnpinAll -> confirmUnpinAll.value = true
            PinnedPageEvent.DismissUnpinAll -> confirmUnpinAll.value = false
            PinnedPageEvent.ConfirmUnpinAll -> unpinAll()
            PinnedPageEvent.Retry -> attempts.value++
        }
    }

    private fun closeSearch() {
        savedState[KEY_SEARCHING] = false
        savedState[KEY_QUERY] = ""
    }

    private fun jump(messageId: Long) {
        jumps.request(chatId, topic, messageId)
        effectChannel.trySend(PinnedPageEffect.Leave)
    }

    private fun copy(messageId: Long) {
        viewModelScope.launch {
            val message = pins.first()?.getOrNull()?.firstOrNull { it.id == messageId } ?: return@launch
            effectChannel.trySend(PinnedPageEffect.CopyText(message.content.textOrCaption.text))
        }
    }

    private fun unpin(messageId: Long) {
        viewModelScope.launch {
            val unpinned = actions.unpin(chatId, messageId)
            if (unpinned is Outcome.Failure) effectChannel.trySend(PinnedPageEffect.ShowError(unpinned.error))
        }
    }

    private fun unpinAll() {
        confirmUnpinAll.value = false
        viewModelScope.launch {
            when (val unpinned = repository.unpinAll(chatId)) {
                is Outcome.Success -> effectChannel.trySend(PinnedPageEffect.UnpinnedAll)
                is Outcome.Failure -> effectChannel.trySend(PinnedPageEffect.ShowError(unpinned.error))
            }
        }
    }

    /** Runs on the dispatcher: the title, the chips and the cards. */
    private fun state(
        sources: Sources,
        filters: Filters,
        canUnpin: Boolean,
        confirm: Boolean,
        now: Instant,
    ): PinnedPageUiState {
        val loaded = sources.pins?.getOrNull()
        val kinds = loaded.orEmpty().groupingBy { it.pinnedKind() }.eachCount()
        val chips =
            PinnedFilter.entries.map { filter ->
                PinnedFilterChip(filter, if (filter == PinnedFilter.All) loaded.orEmpty().size else kinds[filter] ?: 0)
            }
        val query = filters.query.takeIf { filters.searching }.orEmpty()
        val shown = { message: Message ->
            (filters.filter == PinnedFilter.All || message.pinnedKind() == filters.filter) && message.matches(query)
        }
        val content =
            when (val pins = sources.pins) {
                null -> {
                    PinnedPageContent.Loading
                }

                is Outcome.Failure -> {
                    PinnedPageContent.Error(pins.error)
                }

                is Outcome.Success -> {
                    val months =
                        pinnedMonths(pins.value, shown, CardSources(sources.chat, sources.people, formatter(), now))
                    if (months.isEmpty()) {
                        PinnedPageContent.Empty(filtered = pins.value.isNotEmpty())
                    } else {
                        PinnedPageContent.Content(months.toImmutableList())
                    }
                }
            }
        return PinnedPageUiState(
            chatTitle = sources.chat.title,
            total = loaded.orEmpty().size,
            filters = chips.toImmutableList(),
            selected = filters.filter,
            searching = filters.searching,
            query = filters.query,
            content = content,
            canUnpin = canUnpin,
            confirmUnpinAll = confirm,
        )
    }

    /** The formatter for the current time zone, language and clock setting, which can change while the app runs. */
    private fun formatter(): ChatTimeFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return timeFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatTimeFormatter(zone, locale, is24HourClock, timePatterns).also { timeFormatter = it }
    }

    /** The pins and what names their senders. */
    private class Sources(
        val pins: Outcome<ImmutableList<Message>>?,
        val chat: Chat,
        val people: ChatPeople,
    )

    /** The filter chosen and the search. */
    private data class Filters(
        val filter: PinnedFilter,
        val searching: Boolean,
        val query: String,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val KEY_FILTER = "pinned.filter"
        const val KEY_SEARCHING = "pinned.searching"
        const val KEY_QUERY = "pinned.query"
    }
}

/** The forum topic or message thread a [PinnedMessagesRoute] names; null for the whole chat. */
internal val PinnedMessagesRoute.topic: MessageTopic?
    get() =
        topicId?.let(MessageTopic::Forum)
            ?: threadId?.let(MessageTopic::Thread)
            ?: savedTopicId?.let(MessageTopic::SavedMessages)
            ?: directTopicId?.let(MessageTopic::DirectMessages)

/** The page of the pinned messages of chat [chatId]'s conversation of this topic (the whole chat for none). */
internal fun MessageTopic?.pinnedMessagesRoute(chatId: Long): PinnedMessagesRoute =
    PinnedMessagesRoute(
        chatId = chatId,
        topicId = (this as? MessageTopic.Forum)?.topicId,
        threadId = (this as? MessageTopic.Thread)?.threadId,
        savedTopicId = (this as? MessageTopic.SavedMessages)?.topicId,
        directTopicId = (this as? MessageTopic.DirectMessages)?.topicId,
    )

/** What the user does on the pinned messages page. */
sealed interface PinnedPageEvent {
    data class SelectFilter(
        val filter: PinnedFilter,
    ) : PinnedPageEvent

    data object OpenSearch : PinnedPageEvent

    /** Back or the search's close: the search and its words go. */
    data object CloseSearch : PinnedPageEvent

    data class Search(
        val query: String,
    ) : PinnedPageEvent

    data class Jump(
        val messageId: Long,
    ) : PinnedPageEvent

    data class Copy(
        val messageId: Long,
    ) : PinnedPageEvent

    data class Unpin(
        val messageId: Long,
    ) : PinnedPageEvent

    /** "Unpin all" in the top bar's menu: asks first. */
    data object UnpinAll : PinnedPageEvent

    data object ConfirmUnpinAll : PinnedPageEvent

    data object DismissUnpinAll : PinnedPageEvent

    data object Retry : PinnedPageEvent
}

/** One-off effects of the pinned messages page. */
sealed interface PinnedPageEffect {
    /** The page leaves, back to the conversation, which shows the message jumped to. */
    data object Leave : PinnedPageEffect

    data class CopyText(
        val text: String,
    ) : PinnedPageEffect {
        override fun toString() = "CopyText(…)"
    }

    data object UnpinnedAll : PinnedPageEffect

    data class ShowError(
        val error: DomainError,
    ) : PinnedPageEffect
}

/** The ids of the chats that sent these messages, other than chat [except]. */
private fun List<Message>.senderChatIds(except: Long): Set<Long> =
    mapNotNullTo(mutableSetOf()) { message -> (message.sender as? MessageSender.Chat)?.chatId?.takeIf { it != except } }
