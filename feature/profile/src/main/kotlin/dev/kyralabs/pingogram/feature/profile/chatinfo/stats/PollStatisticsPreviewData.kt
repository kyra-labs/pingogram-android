package dev.kyralabs.pingogram.feature.profile.chatinfo.stats

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChartStyle
import dev.kyralabs.pingogram.core.ui.chart.ChartUi
import dev.kyralabs.pingogram.core.ui.chart.SeriesUi
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** The states of a poll's statistics page, shared by previews and screenshot tests. */
internal object PollStatisticsPreviewData {
    private fun series(
        name: String,
        values: List<Long>,
    ) = SeriesUi(name, values.toImmutableList(), values.first().toString(), values.last().toString(), "${values.max()}")

    private fun votes(count: Int) = UiText.plural(R.plurals.poll_statistics_votes, count, count)

    private val chart =
        ChartUi(
            series =
                persistentListOf(
                    series("Summer", listOf(40, 62, 31, 18, 12, 9, 6)),
                    series("Autumn", listOf(28, 44, 25, 15, 9, 7, 5)),
                    series("Winter", listOf(10, 16, 9, 6, 4, 3, 2)),
                ),
            style = ChartStyle.Bar,
            stacked = true,
            percentage = false,
            maxValue = 122,
            maxLabel = "122",
            xLabels = persistentListOf("Oct 16", "Oct 19", "Oct 22"),
            pointCount = 7,
        )

    val content =
        PollStatisticsUiState.Content(
            question = "Which season do you like best?",
            votes = votes(356),
            options =
                persistentListOf(
                    PollTotal("Summer", votes(178), "50%"),
                    PollTotal("Autumn", votes(133), "37%"),
                    PollTotal("Winter", votes(45), "13%"),
                ),
            graph = GraphState.Drawn(chart),
        )

    val noGraph = content.copy(votes = votes(42), graph = GraphState.Empty)

    val graphLoading = content.copy(graph = GraphState.Loading)

    val error = PollStatisticsUiState.Error(DomainError.Network)
}
