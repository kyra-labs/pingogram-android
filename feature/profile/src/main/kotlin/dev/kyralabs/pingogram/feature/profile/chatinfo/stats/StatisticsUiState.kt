package dev.kyralabs.pingogram.feature.profile.chatinfo.stats

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChartStyle
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/** What the statistics page of a supergroup or channel shows (`REQUIREMENTS.md` FR-P3). */
@Immutable
sealed interface StatisticsUiState {
    data object Loading : StatisticsUiState

    data class Error(
        val error: DomainError,
    ) : StatisticsUiState

    /**
     * @property period the days the statistics cover, "Oct 1 – Oct 22".
     * @property overview the period's numbers against the period before.
     * @property graphs the charts, in Telegram's order.
     * @property topSenders a supergroup's members who sent the most messages.
     */
    data class Content(
        val isChannel: Boolean,
        val period: String,
        val overview: ImmutableList<OverviewItem>,
        val graphs: ImmutableList<GraphItem>,
        val topSenders: ImmutableList<TopSenderItem>,
    ) : StatisticsUiState
}

/** A number of the period ([value], "1,200") under its [label], and how it changed ([change], "+200 (20%)"). */
@Immutable
data class OverviewItem(
    @param:StringRes val label: Int,
    val value: String,
    val change: String?,
    val trend: Trend,
)

enum class Trend { Up, Down, Flat }

/** A chart under its [title], loading, drawn, or without data. */
@Immutable
data class GraphItem(
    val key: String,
    @param:StringRes val title: Int,
    val state: GraphState,
)

@Immutable
sealed interface GraphState {
    data object Loading : GraphState

    data object Empty : GraphState

    data class Drawn(
        val chart: ChartUi,
    ) : GraphState
}

/**
 * A chart to draw: its [series] over the x axis labelled by [xLabels] (its first, middle and last values), up to
 * [maxValue] (the highest sum of a stack, or 100 for shares of the whole), formatted as [maxLabel], from [minValue]
 * (0 for stacks, a line's lowest value), formatted as [minLabel].
 */
@Immutable
data class ChartUi(
    val series: ImmutableList<SeriesUi>,
    val style: ChartStyle,
    val stacked: Boolean,
    val percentage: Boolean,
    val maxValue: Long,
    val maxLabel: String,
    val minValue: Long = 0,
    val minLabel: String = "0",
    val xLabels: ImmutableList<String>,
    val pointCount: Int,
)

/** A series of [values] named [name], with its [first] and [last] value formatted for screen readers. */
@Immutable
data class SeriesUi(
    val name: String,
    val values: ImmutableList<Long>,
    val first: String,
    val last: String,
    val highest: String,
)

/** A member who sent the most messages: [name], [messages] ("120 messages") under it. */
@Immutable
data class TopSenderItem(
    val userId: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
    val messages: UiText,
) {
    override fun toString() = "TopSenderItem(userId=$userId, …)"
}

sealed interface StatisticsEvent {
    data object Retry : StatisticsEvent

    data class Open(
        val userId: Long,
    ) : StatisticsEvent
}

sealed interface StatisticsEffect {
    data class OpenProfile(
        val userId: Long,
    ) : StatisticsEffect
}
