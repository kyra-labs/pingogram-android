package dev.kyralabs.pingogram.feature.profile.chatinfo.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.ChatStatistics
import dev.kyralabs.pingogram.core.domain.model.StatisticGraph
import dev.kyralabs.pingogram.core.domain.model.StatisticGraphEntry
import dev.kyralabs.pingogram.core.domain.model.TopSender
import dev.kyralabs.pingogram.core.domain.repository.ChatStatisticsRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.ChatStatisticsRoute
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Locale
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The statistics of supergroup or channel [chatId] (`REQUIREMENTS.md` FR-P3): `getChatStatistics` once the page opens,
 * then the graphs Telegram gives only as a token, three at a time (`getStatisticalGraph`). Numbers and dates are
 * formatted here in the user's language ([StatisticsFormat]); the charts keep their raw values for the canvas.
 */
@Suppress("LongParameterList") // The route, two repositories, the clock, the patterns and the language.
@HiltViewModel
class StatisticsViewModel internal constructor(
    private val chatId: Long,
    private val repository: ChatStatisticsRepository,
    private val userRepository: UserRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: ChatStatisticsRepository,
        userRepository: UserRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
    ) : this(
        savedStateHandle.toRoute<ChatStatisticsRoute>().chatId,
        repository,
        userRepository,
        clock,
        timePatterns,
        Locale::getDefault,
    )

    private val mutableUiState = MutableStateFlow<StatisticsUiState>(StatisticsUiState.Loading)
    val uiState: StateFlow<StatisticsUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<StatisticsEffect>(Channel.BUFFERED)
    val effects: Flow<StatisticsEffect> = effectChannel.receiveAsFlow()

    private val graphLoads = Semaphore(GRAPH_LOADS)

    init {
        load()
    }

    fun onEvent(event: StatisticsEvent) {
        when (event) {
            StatisticsEvent.Retry -> if (uiState.value is StatisticsUiState.Error) load()
            is StatisticsEvent.Open -> effectChannel.trySend(StatisticsEffect.OpenProfile(event.userId))
        }
    }

    private fun load() {
        mutableUiState.value = StatisticsUiState.Loading
        viewModelScope.launch {
            when (val loaded = repository.statistics(chatId)) {
                is Outcome.Success -> show(loaded.value)
                is Outcome.Failure -> mutableUiState.value = StatisticsUiState.Error(loaded.error)
            }
        }
    }

    private suspend fun show(statistics: ChatStatistics) {
        val format = StatisticsFormat(locale(), clock.zone(), timePatterns)
        mutableUiState.value =
            StatisticsUiState.Content(
                isChannel = statistics.isChannel,
                period = format.period(statistics.periodStart, statistics.periodEnd),
                overview = statistics.values.map(format::overview).toImmutableList(),
                graphs = statistics.graphs.map { graphItem(it.kind.name, it, format) }.toImmutableList(),
                topSenders =
                    statistics.topSenders
                        .take(TOP_SENDERS)
                        .map { topSender(it) }
                        .toImmutableList(),
            )
        statistics.graphs
            .mapNotNull { entry -> (entry.graph as? StatisticGraph.Pending)?.let { entry to it.token } }
            .forEach { (entry, token) -> viewModelScope.launch { loadGraph(entry, token, format) } }
    }

    private suspend fun loadGraph(
        entry: StatisticGraphEntry,
        token: String,
        format: StatisticsFormat,
    ) {
        val graph = graphLoads.withPermit { repository.graph(chatId, token) }
        val state = (graph as? Outcome.Success)?.value?.let { graphState(it, entry, format) } ?: GraphState.Empty
        mutableUiState.update { current ->
            if (current !is StatisticsUiState.Content) return@update current
            current.copy(
                graphs =
                    current.graphs
                        .map { if (it.key == entry.kind.name) it.copy(state = state) else it }
                        .toImmutableList(),
            )
        }
    }

    private fun graphItem(
        key: String,
        entry: StatisticGraphEntry,
        format: StatisticsFormat,
    ) = GraphItem(key, entry.kind.title, graphState(entry.graph, entry, format))

    private fun graphState(
        graph: StatisticGraph,
        entry: StatisticGraphEntry,
        format: StatisticsFormat,
    ): GraphState =
        when (graph) {
            is StatisticGraph.Loaded -> GraphState.Drawn(format.chart(graph.chart, byHour = entry.kind.isByHour))
            is StatisticGraph.Pending -> GraphState.Loading
            is StatisticGraph.Failed -> GraphState.Empty
        }

    private suspend fun topSender(sender: TopSender): TopSenderItem {
        val user = userRepository.user(sender.userId).first()
        return TopSenderItem(
            userId = sender.userId,
            name = user?.takeUnless { it.isDeleted }?.fullName() ?: UiText.resource(UiR.string.user_deleted_account),
            photo = user?.profilePhoto,
            messages = UiText.plural(R.plurals.statistics_messages, sender.messageCount, sender.messageCount),
        )
    }

    private companion object {
        /** Graphs asked for at once. */
        const val GRAPH_LOADS = 3

        /** Members shown under "Top members", as Telegram's apps do. */
        const val TOP_SENDERS = 10
    }
}
