package dev.kyralabs.pingogram.feature.calls.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.CallHistoryChange
import dev.kyralabs.pingogram.core.domain.model.CallHistoryEntry
import dev.kyralabs.pingogram.core.domain.model.CallOutcome
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.CallHistoryRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.calls.ui.formatDuration
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Recent calls (`docs/ARCHITECTURE.md`, Calls): the user's one-to-one calls from [CallHistoryRepository], newest first
 * and by day, with the people's names and photos from [UserRepository], all of them or only the missed ones. Pages
 * load as the list nears its end; calls that end and call messages deleted elsewhere change the list while it shows.
 * A tap calls back the way the call went ([RecentCallsEffect.Call]); deleting removes the call at once and puts it
 * back if Telegram refuses.
 *
 * The filter is kept in the [SavedStateHandle]; the calls are read again after a process death.
 */
@HiltViewModel
class RecentCallsViewModel
    @Inject
    internal constructor(
        private val savedStateHandle: SavedStateHandle,
        private val repository: CallHistoryRepository,
        userRepository: UserRepository,
        private val clock: WallClock,
        private val timePatterns: TimePatterns,
    ) : ViewModel() {
        /** The language the times are formatted in; the device's, replaced in tests. */
        internal var locale: () -> Locale = Locale::getDefault

        private val history = MutableStateFlow(History())
        private val effects = Channel<RecentCallsEffect>(Channel.BUFFERED)
        private var loadJob: Job? = null
        private var times: CallHistoryTimes? = null

        /** What the screen does once: calls, and the result of deleting. */
        val effect: Flow<RecentCallsEffect> = effects.receiveAsFlow()

        private val filter: CallFilter
            get() = savedStateHandle.get<String>(KEY_FILTER)?.let { CallFilter.valueOf(it) } ?: CallFilter.All

        val uiState: StateFlow<RecentCallsUiState> =
            combine(
                history,
                userRepository.users.onStart { emit(persistentMapOf()) },
                clock.minutes.onStart { emit(clock.now()) },
            ) { history, users, now ->
                RecentCallsUiState(history.filter, history.toContent(users, now))
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                RecentCallsUiState(filter, RecentCallsContent.Loading),
            )

        init {
            // After the first frame (docs/PERFORMANCE.md §4).
            viewModelScope.launch {
                load(reset = true)
                repository.changes.collect(::apply)
            }
        }

        fun onEvent(event: RecentCallsEvent) {
            when (event) {
                is RecentCallsEvent.SelectFilter -> {
                    select(event.filter)
                }

                is RecentCallsEvent.CallBack -> {
                    effects.trySend(
                        RecentCallsEffect.Call(event.call.userId, event.call.isVideo),
                    )
                }

                is RecentCallsEvent.Delete -> {
                    delete(event.call)
                }

                RecentCallsEvent.LoadMore -> {
                    loadMore()
                }

                RecentCallsEvent.Retry -> {
                    if (history.value.entries.isEmpty()) load(reset = true) else loadMore()
                }
            }
        }

        private fun select(selected: CallFilter) {
            if (selected == history.value.filter) return
            savedStateHandle[KEY_FILTER] = selected.name
            load(reset = true)
        }

        private fun loadMore() {
            val current = history.value
            val failedFirstPage = current.error != null && current.entries.isEmpty()
            if (current.isLoading || current.nextOffset.isEmpty() || failedFirstPage) return
            load(reset = false)
        }

        private fun load(reset: Boolean) {
            val shown = filter
            if (reset) {
                loadJob?.cancel()
                history.value = History(filter = shown)
            }
            val offset = history.value.nextOffset
            history.update { it.copy(isLoading = true, error = null) }
            loadJob =
                viewModelScope.launch {
                    when (val page = repository.page(offset = offset, onlyMissed = shown == CallFilter.Missed)) {
                        is Outcome.Success -> {
                            history.update { current ->
                                val known = current.entries.mapTo(HashSet()) { it.key() }
                                current.copy(
                                    entries = current.entries + page.value.entries.filter { it.key() !in known },
                                    nextOffset = page.value.nextOffset,
                                    isLoading = false,
                                    loaded = true,
                                )
                            }
                        }

                        is Outcome.Failure -> {
                            history.update { it.copy(isLoading = false, error = page.error) }
                        }
                    }
                }
        }

        private fun apply(change: CallHistoryChange) {
            when (change) {
                is CallHistoryChange.Added -> {
                    val entry = change.entry
                    history.update { current ->
                        val fits = current.filter == CallFilter.All || entry.outcome == CallOutcome.Missed
                        if (!current.loaded || !fits || current.entries.any { it.key() == entry.key() }) {
                            current
                        } else {
                            current.copy(entries = (listOf(entry) + current.entries).sortedByDescending { it.date })
                        }
                    }
                }

                is CallHistoryChange.Removed -> {
                    val removed = change.messageIds.toSet()
                    history.update { current ->
                        current.copy(
                            entries =
                                current.entries.filterNot { it.chatId == change.chatId && it.messageId in removed },
                        )
                    }
                }
            }
        }

        private fun delete(call: RecentCallsItem.Call) {
            val entry = history.value.entries.firstOrNull { it.chatId == call.chatId && it.messageId == call.messageId }
            if (entry == null) return
            history.update { current -> current.copy(entries = current.entries - entry) }
            viewModelScope.launch {
                when (val outcome = repository.delete(entry)) {
                    is Outcome.Success -> {
                        effects.send(RecentCallsEffect.Deleted)
                    }

                    is Outcome.Failure -> {
                        history.update { current ->
                            current.copy(entries = (current.entries + entry).sortedByDescending { it.date })
                        }
                        effects.send(RecentCallsEffect.DeleteFailed(outcome.error))
                    }
                }
            }
        }

        private fun History.toContent(
            users: ImmutableMap<Long, User>,
            now: Instant,
        ): RecentCallsContent =
            when {
                entries.isNotEmpty() -> {
                    RecentCallsContent.Calls(
                        items = items(users, now),
                        loadingMore = isLoading,
                        pageError = error,
                        reachedEnd = loaded && nextOffset.isEmpty(),
                    )
                }

                error != null -> {
                    RecentCallsContent.Error(error)
                }

                loaded && !isLoading && nextOffset.isEmpty() -> {
                    RecentCallsContent.Empty
                }

                else -> {
                    RecentCallsContent.Loading
                }
            }

        private fun History.items(
            users: ImmutableMap<Long, User>,
            now: Instant,
        ) = buildList {
            val times = times()
            val today = times.today(now)
            var lastDay: LocalDate? = null
            for (entry in entries) {
                val day = times.day(entry.date)
                if (day != lastDay) {
                    add(RecentCallsItem.Day(key = "day-$day", label = times.dayLabel(day, today)))
                    lastDay = day
                }
                add(entry.toItem(users[entry.userId], times))
            }
        }.toImmutableList()

        /** The times for the current zone, language and clock setting, which can change while the page shows. */
        private fun times(): CallHistoryTimes {
            val zone = clock.zone()
            val locale = locale()
            val is24HourClock = timePatterns.is24HourClock()
            return times?.takeIf { it.isFor(zone, locale, is24HourClock) }
                ?: CallHistoryTimes(zone, locale, is24HourClock, timePatterns).also { times = it }
        }

        private companion object {
            const val KEY_FILTER = "filter"
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/** The calls read so far for [filter], where the next page starts (empty: none), and how the last page went. */
private data class History(
    val filter: CallFilter = CallFilter.All,
    val entries: List<CallHistoryEntry> = emptyList(),
    val nextOffset: String = "",
    val isLoading: Boolean = false,
    val loaded: Boolean = false,
    val error: DomainError? = null,
)

private fun CallHistoryEntry.key() = "$chatId/$messageId"

private fun CallHistoryEntry.toItem(
    user: User?,
    times: CallHistoryTimes,
) = RecentCallsItem.Call(
    key = key(),
    chatId = chatId,
    messageId = messageId,
    userId = userId,
    name = user?.fullName() ?: UiText.resource(UiR.string.user_deleted_account),
    photo = user?.profilePhoto,
    outcome = outcome,
    isOutgoing = isOutgoing,
    isVideo = isVideo,
    kind = kind(),
    time = times.time(date),
    duration = duration.takeIf { outcome == CallOutcome.Answered && it > 0 }?.let { formatDuration(it.toLong()) },
)

/** "Missed video call", as the chat list's previews word a call (`core:ui`). */
private fun CallHistoryEntry.kind(): UiText {
    val (voice, video) =
        when (outcome) {
            CallOutcome.Missed -> {
                UiR.string.preview_call_missed to UiR.string.preview_video_call_missed
            }

            CallOutcome.Cancelled -> {
                UiR.string.preview_call_cancelled to UiR.string.preview_video_call_cancelled
            }

            CallOutcome.Declined -> {
                UiR.string.preview_call_declined to UiR.string.preview_video_call_declined
            }

            CallOutcome.Answered -> {
                if (isOutgoing) {
                    UiR.string.preview_call_outgoing to UiR.string.preview_video_call_outgoing
                } else {
                    UiR.string.preview_call_incoming to UiR.string.preview_video_call_incoming
                }
            }
        }
    return UiText.resource(if (isVideo) video else voice)
}
