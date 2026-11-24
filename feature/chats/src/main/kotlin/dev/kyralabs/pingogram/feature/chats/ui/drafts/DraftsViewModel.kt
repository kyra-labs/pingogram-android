package dev.kyralabs.pingogram.feature.chats.ui.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chats.ui.ChatListTimeFormatter
import dev.kyralabs.pingogram.feature.chats.ui.SearchRowSources
import dev.kyralabs.pingogram.feature.chats.ui.shownDraft
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject

/**
 * The Drafts page (`REQUIREMENTS.md` FR-X16) as [uiState]: every main or archived chat whose draft the chat list
 * shows, newest draft first, following the chats as their drafts change.
 *
 * - **Loading**: TDLib knows only the chats loaded so far, so once the page collects [uiState] (not in the
 *   constructor, `docs/PERFORMANCE.md` §4) it loads the rest of both lists, page by page, until they end or a page
 *   fails; the loading state shows only until the first draft is found or both lists are loaded.
 * - **Rows** are mapped on [dispatcher] as global search's message rows, the draft as the message. Locked chats
 *   ([ChatLockRepository]) never show, as in the chat list (`docs/adr/0029-locked-chats.md`).
 * - A row opens its chat ([DraftsEffect.OpenChat]), where the message box holds the draft.
 *
 * Tests replace the clock, time patterns, dispatcher and language ([locale]).
 */
@HiltViewModel
class DraftsViewModel internal constructor(
    private val chatRepository: ChatRepository,
    chatLock: ChatLockRepository,
    userRepository: UserRepository,
    groupRepository: GroupRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale = Locale::getDefault,
) : ViewModel() {
    @Inject
    constructor(
        chatRepository: ChatRepository,
        chatLock: ChatLockRepository,
        userRepository: UserRepository,
        groupRepository: GroupRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        chatRepository = chatRepository,
        chatLock = chatLock,
        userRepository = userRepository,
        groupRepository = groupRepository,
        clock = clock,
        timePatterns = timePatterns,
        dispatcher = dispatcher,
        locale = Locale::getDefault,
    )

    private var timeFormatter: ChatListTimeFormatter? = null

    private val effectChannel = Channel<DraftsEffect>(Channel.BUFFERED)

    /** One-off effects for the page: opening a chat. */
    val effects: Flow<DraftsEffect> = effectChannel.receiveAsFlow()

    /** False while the rest of the lists loads, then true. */
    private val loaded: Flow<Boolean> =
        flow {
            emit(false)
            LISTS.forEach { list -> loadAll(list) }
            emit(true)
        }

    /** The chats with a draft the chat list shows, from both lists, newest draft first; never a locked chat. */
    private val drafts: Flow<List<Chat>> =
        combine(combine(LISTS.map(chatRepository::chatList)) { it.toList() }, chatLock.lockedChatIds) { lists, locked ->
            lists
                .flatMap { it }
                .distinctBy { it.id }
                .filterNot { it.id in locked }
                .mapNotNull { chat -> chat.shownDraft?.let { chat to it.date } }
                .sortedByDescending { it.second }
                .map { it.first }
        }

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

    val uiState: StateFlow<DraftsUiState> =
        combine(drafts, loaded, sources, ::toUiState)
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DraftsUiState.Loading)

    fun onEvent(event: DraftsEvent) {
        when (event) {
            is DraftsEvent.Open -> effectChannel.trySend(DraftsEffect.OpenChat(event.chatId))
        }
    }

    /** Loads the rest of [list], page by page, until it ends or a page fails. */
    private suspend fun loadAll(list: ChatListType) {
        // Each page reaches the list's flow by itself.
        do {
            val more = (chatRepository.loadChats(list) as? Outcome.Success)?.value == true
        } while (more)
    }

    /** Runs on the dispatcher: maps [chats] to rows with [sources]; none is loading until both lists are [loaded]. */
    private fun toUiState(
        chats: List<Chat>,
        loaded: Boolean,
        sources: SearchRowSources,
    ): DraftsUiState =
        when {
            chats.isNotEmpty() -> DraftsUiState.Content(chats.mapNotNull { it.toDraftItem(sources) }.toImmutableList())
            loaded -> DraftsUiState.Empty
            else -> DraftsUiState.Loading
        }

    /** The formatter for the current time zone, language and clock setting, which can change while the app runs. */
    private fun timeFormatter(): ChatListTimeFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return timeFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatListTimeFormatter(zone, locale, is24HourClock, timePatterns).also { timeFormatter = it }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val LISTS = listOf(ChatListType.Main, ChatListType.Archive)
    }
}
