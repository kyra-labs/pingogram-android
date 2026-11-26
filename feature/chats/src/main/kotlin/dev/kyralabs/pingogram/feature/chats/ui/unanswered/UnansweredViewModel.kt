package dev.kyralabs.pingogram.feature.chats.ui.unanswered

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.repository.UnansweredDismissalRepository
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chats.ui.ChatListTimeFormatter
import dev.kyralabs.pingogram.feature.chats.ui.SearchRowInputs
import dev.kyralabs.pingogram.feature.chats.ui.SearchRowSources
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * The Unanswered page (`REQUIREMENTS.md` FR-X22) as [uiState]: the private chats waiting for the user's answer
 * ([UnansweredChats]), the longest waiting first, following them as messages come and go.
 *
 * - Once the page collects [uiState] (not in the constructor, `docs/PERFORMANCE.md` §4) the rest of the main list
 *   loads; the loading state shows only until the first unanswered chat is found or the list is loaded.
 * - **Rows** are mapped on [dispatcher] as global search's message rows, the last message as the message.
 * - **Reply** opens the chat with its message box focused; **Dismiss** keeps it off the page until a newer message
 *   ([UnansweredDismissalRepository]). Nothing is ever sent.
 *
 * Tests replace the clock, time patterns, dispatcher and language ([locale]).
 */
@HiltViewModel
class UnansweredViewModel internal constructor(
    unansweredChats: UnansweredChats,
    private val dismissals: UnansweredDismissalRepository,
    rowInputs: SearchRowInputs,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale = Locale::getDefault,
) : ViewModel() {
    @Inject
    constructor(
        unansweredChats: UnansweredChats,
        dismissals: UnansweredDismissalRepository,
        rowInputs: SearchRowInputs,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(unansweredChats, dismissals, rowInputs, clock, timePatterns, dispatcher, Locale::getDefault)

    private var timeFormatter: ChatListTimeFormatter? = null

    private val effectChannel = Channel<UnansweredEffect>(Channel.BUFFERED)

    /** One-off effects for the page: replying in a chat, and a dismissal not saved. */
    val effects: Flow<UnansweredEffect> = effectChannel.receiveAsFlow()

    private val sources = rowInputs.sources(clock.minutes, ::timeFormatter)

    val uiState: StateFlow<UnansweredUiState> =
        combine(unansweredChats.chats, unansweredChats.loaded, sources, ::toUiState)
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UnansweredUiState.Loading)

    fun onEvent(event: UnansweredEvent) {
        when (event) {
            is UnansweredEvent.Reply -> {
                effectChannel.trySend(UnansweredEffect.Reply(event.chatId))
            }

            is UnansweredEvent.Dismiss -> {
                viewModelScope.launch {
                    val outcome = dismissals.dismiss(event.chatId, event.messageId)
                    if (outcome is Outcome.Failure) effectChannel.trySend(UnansweredEffect.ShowError(outcome.error))
                }
            }
        }
    }

    /** Runs on the dispatcher: maps [chats] to rows with [sources]; none is loading until the list is [loaded]. */
    private fun toUiState(
        chats: List<Chat>,
        loaded: Boolean,
        sources: SearchRowSources,
    ): UnansweredUiState =
        when {
            chats.isNotEmpty() -> {
                UnansweredUiState.Content(chats.mapNotNull { it.toUnansweredItem(sources) }.toImmutableList())
            }

            loaded -> {
                UnansweredUiState.Empty
            }

            else -> {
                UnansweredUiState.Loading
            }
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
    }
}
