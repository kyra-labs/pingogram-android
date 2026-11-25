package dev.kyralabs.pingogram.feature.chats.ui.mentions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.LocalChatNamesRepository
import dev.kyralabs.pingogram.core.domain.repository.MentionRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chats.ui.ChatListTimeFormatter
import dev.kyralabs.pingogram.feature.chats.ui.SearchRowInputs
import dev.kyralabs.pingogram.feature.chats.ui.SearchRowSources
import dev.kyralabs.pingogram.feature.chats.ui.toSearchItem
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject

/**
 * The Mentions & replies page (`REQUIREMENTS.md` FR-X1) as [uiState]: the messages that mention the user or reply to
 * them, across chats ([MentionRepository.mentions]), unread ones first.
 *
 * - **Reading**: once the page collects [uiState] (not in the constructor, `docs/PERFORMANCE.md` §4), again on
 *   [MentionsEvent.Retry] after a failure, which shows its error, and on [MentionsEvent.Refresh] when the page shows
 *   again, quietly: the rows stay until the new ones replace them. Nothing is read by itself, and they stay while the
 *   page collects, and 5 s after, so a rotation does not read them again.
 * - **Rows** are mapped on [dispatcher] as global search maps its messages ([toSearchItem], nothing highlighted), with
 *   the users, groups, current user and minute they show.
 * - A row opens its chat at the message ([MentionsEffect.OpenMessage]).
 *
 * Tests replace the clock, time patterns, dispatcher and language ([locale]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MentionsViewModel internal constructor(
    private val mentionRepository: MentionRepository,
    rowInputs: SearchRowInputs,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale = Locale::getDefault,
) : ViewModel() {
    @Inject
    constructor(
        mentionRepository: MentionRepository,
        rowInputs: SearchRowInputs,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        mentionRepository,
        rowInputs,
        clock,
        timePatterns,
        dispatcher,
        Locale::getDefault,
    )

    /** Each read asked for: a retry shows the loading state, a refresh keeps the rows meanwhile. */
    private val reads = MutableStateFlow(Read(number = 0, showsLoading = true))

    private var timeFormatter: ChatListTimeFormatter? = null

    private val effectChannel = Channel<MentionsEffect>(Channel.BUFFERED)

    /** One-off effects for the page: opening a chat at a message. */
    val effects: Flow<MentionsEffect> = effectChannel.receiveAsFlow()

    /** The messages: null while they are read, then what reading them gave. */
    private val mentions: Flow<Outcome<List<FoundMessage>>?> =
        reads.transformLatest { read ->
            if (read.showsLoading) emit(null)
            emit(mentionRepository.mentions())
        }

    private val sources =
        rowInputs.sources(clock.minutes, ::timeFormatter)

    val uiState: StateFlow<MentionsUiState> =
        combine(mentions, sources, ::toUiState)
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MentionsUiState.Loading)

    fun onEvent(event: MentionsEvent) {
        when (event) {
            is MentionsEvent.Open -> {
                effectChannel.trySend(MentionsEffect.OpenMessage(event.chatId, event.messageId))
            }

            MentionsEvent.Retry -> {
                if (uiState.value is MentionsUiState.Error) reads.update { Read(it.number + 1, showsLoading = true) }
            }

            MentionsEvent.Refresh -> {
                reads.update { Read(it.number + 1, showsLoading = false) }
            }
        }
    }

    /** Runs on the dispatcher: maps [mentions] to rows with [sources], unread ones apart. */
    private fun toUiState(
        mentions: Outcome<List<FoundMessage>>?,
        sources: SearchRowSources,
    ): MentionsUiState =
        when (mentions) {
            null -> {
                MentionsUiState.Loading
            }

            is Outcome.Failure -> {
                MentionsUiState.Error(mentions.error)
            }

            is Outcome.Success -> {
                if (mentions.value.isEmpty()) {
                    MentionsUiState.Empty
                } else {
                    val (unread, earlier) = mentions.value.partition { it.message.containsUnreadMention }
                    MentionsUiState.Content(
                        unread = unread.map { it.toSearchItem(sources, MatchHighlight.None) }.toImmutableList(),
                        earlier = earlier.map { it.toSearchItem(sources, MatchHighlight.None) }.toImmutableList(),
                    )
                }
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

    /** Read [number], showing the loading state meanwhile if [showsLoading]. */
    private data class Read(
        val number: Int,
        val showsLoading: Boolean,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
