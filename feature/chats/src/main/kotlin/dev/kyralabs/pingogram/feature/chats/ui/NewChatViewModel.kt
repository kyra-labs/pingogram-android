package dev.kyralabs.pingogram.feature.chats.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/**
 * The New chat screen (`REQUIREMENTS.md` FR-L7) as [uiState]: the user's contacts to start a chat with. Events come in
 * through [onEvent]; opening a chat and snackbars go out through [effects].
 *
 * - **Contacts** come from [SearchRepository.contacts], read once the screen collects [uiState] (not in the
 *   constructor, `docs/PERFORMANCE.md` §4), and again on [NewChatEvent.Retry] after a failure, which shows its error
 *   (FloodWait, Network, Unauthorized and the rest, as the screen words them). Nothing is retried by itself. They stay
 *   while the screen collects, and 5 s after, so a rotation does not read them again.
 * - **Rows** are mapped on [dispatcher] as search maps its own ([toSearchItem]), with the users, groups, current user
 *   and minute they show, so names and online dots follow TDLib's updates.
 * - **Actions**: a contact opens its private chat ([NewChatEffect.OpenChat]); new contact opens Add contact
 *   ([NewChatEffect.AddContact]); new group and channel say that a later version adds them
 *   ([NewChatEffect.NotAvailableYet]).
 *
 * It takes the repositories the rows combine, plus the clock, time patterns, dispatcher and language that tests
 * replace, hence `LongParameterList` is suppressed.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewChatViewModel internal constructor(
    private val searchRepository: SearchRepository,
    userRepository: UserRepository,
    groupRepository: GroupRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
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

    /** Changes on every retry, which reads the contacts again. */
    private val attempt = MutableStateFlow(0)

    private var timeFormatter: ChatListTimeFormatter? = null

    private val effectChannel = Channel<NewChatEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: opening a chat, and snackbars. */
    val effects: Flow<NewChatEffect> = effectChannel.receiveAsFlow()

    /** The contacts: null while they are read, then what reading them gave. */
    private val contacts: Flow<Outcome<List<Chat>>?> =
        attempt.flatMapLatest {
            flow<Outcome<List<Chat>>?> {
                emit(null)
                emit(searchRepository.contacts())
            }
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

    val uiState: StateFlow<NewChatUiState> =
        combine(contacts, sources, ::toUiState)
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NewChatUiState.Loading)

    fun onEvent(event: NewChatEvent) {
        when (event) {
            is NewChatEvent.Action -> {
                val effect =
                    if (event.action ==
                        NewChatAction.Contact
                    ) {
                        NewChatEffect.AddContact
                    } else {
                        NewChatEffect.NotAvailableYet
                    }
                effectChannel.trySend(effect)
            }

            is NewChatEvent.OpenChat -> {
                effectChannel.trySend(NewChatEffect.OpenChat(event.chatId))
            }

            NewChatEvent.Retry -> {
                if (uiState.value is NewChatUiState.Error) attempt.update { it + 1 }
            }
        }
    }

    /** Runs on the dispatcher: maps [contacts] to rows with [sources]. */
    private fun toUiState(
        contacts: Outcome<List<Chat>>?,
        sources: SearchRowSources,
    ): NewChatUiState =
        when (contacts) {
            null -> {
                NewChatUiState.Loading
            }

            is Outcome.Failure -> {
                NewChatUiState.Error(contacts.error)
            }

            is Outcome.Success -> {
                if (contacts.value.isEmpty()) {
                    NewChatUiState.Empty
                } else {
                    NewChatUiState.Content(contacts.value.map { it.toSearchItem(sources) }.toImmutableList())
                }
            }
        }

    /** The formatter for the current time zone, language and clock setting, which can change while the app runs. */
    private fun timeFormatter(): ChatListTimeFormatter {
        val zone: ZoneId = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return timeFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatListTimeFormatter(zone, locale, is24HourClock, timePatterns).also { timeFormatter = it }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
