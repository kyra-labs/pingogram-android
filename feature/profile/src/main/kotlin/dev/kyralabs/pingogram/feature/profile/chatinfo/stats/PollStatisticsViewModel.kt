package dev.kyralabs.pingogram.feature.profile.chatinfo.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.StatisticGraph
import dev.kyralabs.pingogram.core.domain.repository.ChatStatisticsRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.navigation.PollStatisticsRoute
import dev.kyralabs.pingogram.core.ui.chart.ChartFormat
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

/**
 * The statistics of the poll of message [messageId] in chat [chatId]: its totals from the message
 * ([MessageRepository.message]) and its votes over time (`getPollVoteStatistics`, [ChatStatisticsRepository.pollVotes];
 * `getMessageStatistics` has no graph of a poll's votes), read again by its token where Telegram gives one. Telegram
 * makes the graph once a poll has enough votes; until then, or where it refuses, the page says so in the graph's place.
 */
@Suppress("LongParameterList") // The route, two repositories, the clock, the patterns and the language.
@HiltViewModel
class PollStatisticsViewModel internal constructor(
    private val chatId: Long,
    private val messageId: Long,
    private val statistics: ChatStatisticsRepository,
    private val messageOf: suspend (chatId: Long, messageId: Long) -> Outcome<Message>,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        statistics: ChatStatisticsRepository,
        messages: MessageRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
    ) : this(
        savedStateHandle.toRoute<PollStatisticsRoute>().chatId,
        savedStateHandle.toRoute<PollStatisticsRoute>().messageId,
        statistics,
        messages::message,
        clock,
        timePatterns,
        Locale::getDefault,
    )

    private val mutableUiState = MutableStateFlow<PollStatisticsUiState>(PollStatisticsUiState.Loading)
    val uiState: StateFlow<PollStatisticsUiState> = mutableUiState.asStateFlow()

    init {
        load()
    }

    /** "Try again" after the page could not be read. */
    fun retry() {
        if (uiState.value is PollStatisticsUiState.Error) load()
    }

    private fun load() {
        mutableUiState.value = PollStatisticsUiState.Loading
        viewModelScope.launch {
            val votes = async { statistics.pollVotes(chatId, messageId) }
            val poll =
                when (val message = messageOf(chatId, messageId)) {
                    is Outcome.Failure -> return@launch fail(message.error)
                    is Outcome.Success -> (message.value.content as? MessageContent.Poll)?.poll
                } ?: return@launch fail(DomainError.NotFound)
            val format = ChartFormat(locale(), clock.zone(), timePatterns)
            val graph = (votes.await() as? Outcome.Success)?.value
            mutableUiState.value = content(poll, graph?.let { graphState(it, format) } ?: GraphState.Empty)
            (graph as? StatisticGraph.Pending)?.let { pending ->
                val loaded = (statistics.graph(chatId, pending.token) as? Outcome.Success)?.value
                val state = loaded?.let { graphState(it, format) }?.takeIf { it != GraphState.Loading }
                mutableUiState.update { current ->
                    (current as? PollStatisticsUiState.Content)?.copy(graph = state ?: GraphState.Empty) ?: current
                }
            }
        }
    }

    private fun fail(error: DomainError) {
        mutableUiState.value = PollStatisticsUiState.Error(error)
    }

    private fun graphState(
        graph: StatisticGraph,
        format: ChartFormat,
    ): GraphState =
        when (graph) {
            is StatisticGraph.Loaded -> GraphState.Drawn(format.chart(graph.chart, byHour = false))
            is StatisticGraph.Pending -> GraphState.Loading
            is StatisticGraph.Failed -> GraphState.Empty
        }

    private fun content(
        poll: Poll,
        graph: GraphState,
    ): PollStatisticsUiState.Content {
        val percent = NumberFormat.getPercentInstance(locale())
        return PollStatisticsUiState.Content(
            question = poll.question.text,
            votes = UiText.plural(R.plurals.poll_statistics_votes, poll.totalVoterCount, poll.totalVoterCount),
            options =
                poll.options
                    .map { option ->
                        PollTotal(
                            text = option.text.text,
                            votes =
                                UiText.plural(
                                    R.plurals.poll_statistics_votes,
                                    option.voterCount,
                                    option.voterCount,
                                ),
                            percent = percent.format(option.votePercentage / PERCENT),
                        )
                    }.toImmutableList(),
            graph = graph,
        )
    }

    private companion object {
        const val PERCENT = 100.0
    }
}
