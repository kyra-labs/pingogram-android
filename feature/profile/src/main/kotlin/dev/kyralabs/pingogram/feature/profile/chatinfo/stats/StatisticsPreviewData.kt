package dev.kyralabs.pingogram.feature.profile.chatinfo.stats

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChartStyle
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** The states of the statistics page, shared by previews and screenshot tests. */
internal object StatisticsPreviewData {
    private fun series(
        name: String,
        values: List<Long>,
    ) = SeriesUi(name, values.toImmutableList(), values.first().toString(), values.last().toString(), "${values.max()}")

    private val days: ImmutableList<String> = persistentListOf("Oct 1", "Oct 11", "Oct 22")

    private val growth =
        ChartUi(
            series =
                persistentListOf(
                    series("Members", listOf(1000, 1012, 1030, 1025, 1060, 1090, 1110, 1150, 1170, 1200)),
                ),
            style = ChartStyle.Line,
            stacked = false,
            percentage = false,
            maxValue = 1200,
            maxLabel = "1,200",
            minValue = 1000,
            minLabel = "1,000",
            xLabels = days,
            pointCount = 10,
        )

    private val joins =
        ChartUi(
            series =
                persistentListOf(
                    series("Joined", listOf(12, 18, 9, 22, 30, 14, 25, 19, 28, 33)),
                    series("Left", listOf(3, 5, 2, 8, 4, 6, 3, 9, 5, 4)),
                ),
            style = ChartStyle.Bar,
            stacked = true,
            percentage = false,
            maxValue = 37,
            maxLabel = "37",
            xLabels = days,
            pointCount = 10,
        )

    private val languages =
        ChartUi(
            series =
                persistentListOf(
                    series("English", listOf(60, 58, 61, 57, 55, 56, 54)),
                    series("German", listOf(30, 31, 29, 32, 33, 32, 34)),
                    series("Other", listOf(10, 11, 10, 11, 12, 12, 12)),
                ),
            style = ChartStyle.Area,
            stacked = true,
            percentage = true,
            maxValue = 100,
            maxLabel = "100%",
            xLabels = days,
            pointCount = 7,
        )

    val channel =
        StatisticsUiState.Content(
            isChannel = true,
            period = "Oct 1 – Oct 22",
            overview =
                persistentListOf(
                    OverviewItem(R.string.statistics_members, "1,200", "+200 (+20%)", Trend.Up),
                    OverviewItem(R.string.statistics_views_per_post, "312", "−18 (−5.5%)", Trend.Down),
                    OverviewItem(R.string.statistics_notifications, "42.5%", null, Trend.Flat),
                ),
            graphs =
                persistentListOf(
                    GraphItem("Members", R.string.statistics_graph_members, GraphState.Drawn(growth)),
                    GraphItem("Joins", R.string.statistics_graph_joins, GraphState.Drawn(joins)),
                    GraphItem("Languages", R.string.statistics_graph_languages, GraphState.Drawn(languages)),
                    GraphItem("ViewsBySource", R.string.statistics_graph_views_by_source, GraphState.Empty),
                ),
            topSenders = persistentListOf(),
        )

    val supergroup =
        channel.copy(
            isChannel = false,
            overview =
                persistentListOf(
                    OverviewItem(R.string.statistics_members, "1,200", "+200 (+20%)", Trend.Up),
                    OverviewItem(R.string.statistics_messages, "9,400", "+1,100 (+13.3%)", Trend.Up),
                ),
            graphs = persistentListOf(channel.graphs[1]),
            topSenders =
                persistentListOf(
                    TopSenderItem(1, UiText.Plain("Ada Lovelace"), null, UiText.Plain("312 messages")),
                    TopSenderItem(2, UiText.Plain("Grace Hopper"), null, UiText.Plain("240 messages")),
                ),
        )

    /**
     * A chart still on its way. Previews only: the `LoadingIndicator` morphs without end, so a golden of it would
     * differ from run to run.
     */
    val graphLoading =
        channel.copy(
            graphs = persistentListOf(GraphItem("Muted", R.string.statistics_graph_muted, GraphState.Loading)),
        )

    val error = StatisticsUiState.Error(DomainError.Network)
}
