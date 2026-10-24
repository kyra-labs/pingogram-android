package dev.kyralabs.pingogram.feature.profile.chatinfo

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.Chart
import dev.kyralabs.pingogram.core.domain.model.ChartSeries
import dev.kyralabs.pingogram.core.domain.model.ChartStyle
import dev.kyralabs.pingogram.core.domain.model.ChatStatistics
import dev.kyralabs.pingogram.core.domain.model.StatisticGraph
import dev.kyralabs.pingogram.core.domain.model.StatisticGraphEntry
import dev.kyralabs.pingogram.core.domain.model.StatisticGraphKind
import dev.kyralabs.pingogram.core.domain.model.StatisticKind
import dev.kyralabs.pingogram.core.domain.model.StatisticValue
import dev.kyralabs.pingogram.core.domain.model.TopSender
import dev.kyralabs.pingogram.core.domain.repository.ChatStatisticsRepository
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.GraphState
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.StatisticsEffect
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.StatisticsEvent
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.StatisticsUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.StatisticsViewModel
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.Trend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * The statistics page on a fake [ChatStatisticsRepository] (Telegram's chart JSON is parsed by the real repository,
 * tested in `core:data` on Robolectric) and the real user repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest : GroupViewModelTest() {
    private val chart =
        Chart(
            x = persistentListOf(DAY_1, DAY_2, DAY_3),
            series =
                persistentListOf(
                    ChartSeries("Joined", persistentListOf(5, 7, 9)),
                    ChartSeries("Left", persistentListOf(1, 2, 1)),
                ),
            style = ChartStyle.Bar,
            stacked = true,
        )

    private val statistics =
        ChatStatistics(
            isChannel = false,
            periodStart = DAY_1 / 1000,
            periodEnd = DAY_3 / 1000,
            values =
                persistentListOf(
                    StatisticValue(StatisticKind.Members, 1_200.0, 1_000.0, 20.0),
                    StatisticValue(StatisticKind.Messages, 900.0, 950.0, -5.26),
                ),
            graphs =
                persistentListOf(
                    StatisticGraphEntry(StatisticGraphKind.Joins, StatisticGraph.Loaded(chart)),
                    StatisticGraphEntry(StatisticGraphKind.Languages, StatisticGraph.Pending("languages")),
                ),
            topSenders = persistentListOf(TopSender(ADA, 120, 40)),
        )

    private inner class FakeStatistics(
        var answer: Outcome<ChatStatistics> = Outcome.Success(statistics),
    ) : ChatStatisticsRepository {
        val graph = CompletableDeferred<Outcome<StatisticGraph>>()
        val tokens = mutableListOf<String>()

        override suspend fun statistics(chatId: Long) = answer

        override suspend fun graph(
            chatId: Long,
            token: String,
        ): Outcome<StatisticGraph> {
            tokens += token
            return graph.await()
        }
    }

    private suspend fun TestScope.viewModel(repository: FakeStatistics): StatisticsViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        runCurrent()
        client.emit(TdApiFixtures.updateUser(TdApiFixtures.user(id = ADA, firstName = "Ada")))
        runCurrent()
        return StatisticsViewModel(CHAT, repository, users, clock, patterns) { Locale.US }.also {
            created += it
            runCurrent()
        }
    }

    private val StatisticsViewModel.content get() = uiState.value as StatisticsUiState.Content

    @Test
    fun `formats the overview with its change against the period before`() =
        runTest {
            val content = viewModel(FakeStatistics()).content

            assertEquals("Oct 21 – Oct 23", content.period)
            val (members, messages) = content.overview
            assertEquals("1,200" to "+200 (+20%)", members.value to members.change)
            assertEquals(Trend.Up, members.trend)
            assertEquals("−50 (−5.3%)", messages.change)
            assertEquals(Trend.Down, messages.trend)
        }

    @Test
    fun `draws a chart with its highest stack and its days, and loads the others by their token`() =
        runTest {
            val repository = FakeStatistics()
            val viewModel = viewModel(repository)

            val joins = viewModel.content.graphs[0].state as GraphState.Drawn
            assertEquals(0L to 10L, joins.chart.minValue to joins.chart.maxValue)
            assertEquals(listOf("Oct 21", "Oct 22", "Oct 23"), joins.chart.xLabels)
            assertEquals(GraphState.Loading, viewModel.content.graphs[1].state)
            assertEquals(listOf("languages"), repository.tokens)

            repository.graph.complete(Outcome.Success(StatisticGraph.Failed("Not enough data")))
            runCurrent()

            assertEquals(GraphState.Empty, viewModel.content.graphs[1].state)
        }

    @Test
    fun `a line chart runs from its lowest value, a stack from 0`() =
        runTest {
            val lines = chart.copy(style = ChartStyle.Line, stacked = false)
            val repository =
                FakeStatistics(
                    Outcome.Success(
                        statistics.copy(
                            graphs =
                                persistentListOf(
                                    StatisticGraphEntry(StatisticGraphKind.Members, StatisticGraph.Loaded(lines)),
                                ),
                        ),
                    ),
                )

            val drawn =
                viewModel(repository)
                    .content.graphs
                    .single()
                    .state as GraphState.Drawn

            assertEquals(1L to 9L, drawn.chart.minValue to drawn.chart.maxValue)
            assertEquals("1", drawn.chart.minLabel)
        }

    @Test
    fun `names the top members, whose rows open their profile`() =
        runTest {
            val viewModel = viewModel(FakeStatistics())

            val top = viewModel.content.topSenders.single()
            assertEquals(UiText.Plain("Ada"), top.name)
            viewModel.onEvent(StatisticsEvent.Open(ADA))
            assertEquals(StatisticsEffect.OpenProfile(ADA), viewModel.effects.first())
        }

    @Test
    fun `statistics that cannot be read show the error, and trying again reads them`() =
        runTest {
            val repository = FakeStatistics(Outcome.Failure(DomainError.Network))
            val viewModel = viewModel(repository)
            assertEquals(StatisticsUiState.Error(DomainError.Network), viewModel.uiState.value)

            repository.answer = Outcome.Success(statistics.copy(graphs = persistentListOf()))
            viewModel.onEvent(StatisticsEvent.Retry)
            runCurrent()

            assertTrue(viewModel.content.graphs.isEmpty())
        }

    private companion object {
        const val DAY_1 = 1_792_540_800_000L
        const val DAY_2 = DAY_1 + 86_400_000L
        const val DAY_3 = DAY_2 + 86_400_000L
    }
}
