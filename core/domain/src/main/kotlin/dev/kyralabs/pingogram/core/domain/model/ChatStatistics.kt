package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The statistics of a supergroup or channel (td_api.tl `chatStatistics`) over [periodStart]–[periodEnd] (Unix time):
 * its [values] against the period before, its [graphs] in Telegram's order, and in a supergroup the members who sent
 * the most messages ([topSenders]).
 */
data class ChatStatistics(
    val isChannel: Boolean,
    val periodStart: Long,
    val periodEnd: Long,
    val values: ImmutableList<StatisticValue>,
    val graphs: ImmutableList<StatisticGraphEntry>,
    val topSenders: ImmutableList<TopSender> = persistentListOf(),
)

/** A number of the period, its value in the period before, and the change in percent. */
data class StatisticValue(
    val kind: StatisticKind,
    val value: Double,
    val previousValue: Double,
    val growthPercent: Double,
)

enum class StatisticKind {
    Members,
    Messages,
    Viewers,
    Senders,
    ViewsPerPost,
    SharesPerPost,
    ReactionsPerPost,
    ViewsPerStory,
    SharesPerStory,
    ReactionsPerStory,
    EnabledNotifications,
}

/** One of the graphs, by [kind]. */
data class StatisticGraphEntry(
    val kind: StatisticGraphKind,
    val graph: StatisticGraph,
)

enum class StatisticGraphKind {
    Members,
    Joins,
    JoinsBySource,
    Languages,
    MessageContent,
    Actions,
    ByHour,
    ByWeekday,
    Muted,
    ViewsByHour,
    ViewsBySource,
    Interactions,
    Reactions,
    StoryInteractions,
    StoryReactions,
    InstantViewInteractions,
}

/** A graph: its data, a [token] to ask for it later with, or why Telegram has none. */
sealed interface StatisticGraph {
    data class Loaded(
        val chart: Chart,
    ) : StatisticGraph

    data class Pending(
        val token: String,
    ) : StatisticGraph

    data class Failed(
        val message: String,
    ) : StatisticGraph {
        override fun toString() = "Failed(…)"
    }
}

/**
 * A chart from Telegram's statistics: the x values ([x], Unix time in milliseconds, or hours of the day), and
 * [series] of y values, one per x. [stacked] series add up (bars, or a share of the whole with [percentage]).
 */
data class Chart(
    val x: ImmutableList<Long>,
    val series: ImmutableList<ChartSeries>,
    val style: ChartStyle,
    val stacked: Boolean = false,
    val percentage: Boolean = false,
)

/** A series of [values], named [name] as Telegram names it. */
data class ChartSeries(
    val name: String,
    val values: ImmutableList<Long>,
)

enum class ChartStyle { Line, Bar, Area }

/** A member of a supergroup who sent [messageCount] messages in the period, of [averageCharacters] on average. */
data class TopSender(
    val userId: Long,
    val messageCount: Int,
    val averageCharacters: Int,
)
