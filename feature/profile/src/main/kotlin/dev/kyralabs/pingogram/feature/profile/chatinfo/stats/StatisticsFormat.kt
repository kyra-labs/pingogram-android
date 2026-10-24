package dev.kyralabs.pingogram.feature.profile.chatinfo.stats

import dev.kyralabs.pingogram.core.domain.model.Chart
import dev.kyralabs.pingogram.core.domain.model.StatisticGraphKind
import dev.kyralabs.pingogram.core.domain.model.StatisticKind
import dev.kyralabs.pingogram.core.domain.model.StatisticValue
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.toImmutableList
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** The statistics' numbers, dates and charts in [locale]'s formats, at [zone]. */
internal class StatisticsFormat(
    private val locale: Locale,
    private val zone: ZoneId,
    timePatterns: TimePatterns,
) {
    private val integers = NumberFormat.getIntegerInstance(locale)
    private val decimals = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    private val day = DateTimeFormatter.ofPattern(timePatterns.bestPattern(locale, "MMMd"), locale)
    private val hour =
        DateTimeFormatter.ofPattern(
            timePatterns.bestPattern(locale, if (timePatterns.is24HourClock()) "Hm" else "hm"),
            locale,
        )

    /** "Oct 1 – Oct 22". */
    fun period(
        start: Long,
        end: Long,
    ): String = "${day.format(at(start))} – ${day.format(at(end))}"

    fun overview(value: StatisticValue): OverviewItem {
        val percent = value.kind == StatisticKind.EnabledNotifications
        val change = value.value - value.previousValue
        return OverviewItem(
            label = value.kind.label,
            value = if (percent) "${decimals.format(value.value)}%" else number(value.value),
            change =
                if (percent || value.previousValue == 0.0) {
                    null
                } else {
                    "${sign(
                        change,
                    )}${number(abs(change))} (${sign(change)}${decimals.format(abs(value.growthPercent))}%)"
                },
            trend =
                when {
                    percent || change == 0.0 -> Trend.Flat
                    change > 0 -> Trend.Up
                    else -> Trend.Down
                },
        )
    }

    /** The chart to draw, its x values labelled as days, or as hours of the day for the [byHour] graphs. */
    fun chart(
        chart: Chart,
        byHour: Boolean,
    ): ChartUi {
        val max =
            when {
                chart.percentage -> PERCENT
                chart.stacked -> chart.x.indices.maxOfOrNull { i -> chart.series.sumOf { it.values[i] } } ?: 0
                else -> chart.series.maxOfOrNull { series -> series.values.maxOrNull() ?: 0 } ?: 0
            }
        // Lines run between their lowest and highest values, as Telegram's apps draw them; stacks start at 0.
        val min = if (chart.stacked) 0 else chart.series.minOfOrNull { series -> series.values.minOrNull() ?: 0 } ?: 0
        val labelIndices = listOf(0, chart.x.lastIndex / 2, chart.x.lastIndex).distinct()
        return ChartUi(
            series =
                chart.series
                    .map { series ->
                        SeriesUi(
                            name = series.name,
                            values = series.values,
                            first = integers.format(series.values.first()),
                            last = integers.format(series.values.last()),
                            highest = integers.format(series.values.max()),
                        )
                    }.toImmutableList(),
            style = chart.style,
            stacked = chart.stacked,
            percentage = chart.percentage,
            maxValue = max,
            maxLabel = if (chart.percentage) "100%" else integers.format(max),
            minValue = min,
            minLabel = if (chart.percentage) "0%" else integers.format(min),
            xLabels = labelIndices.map { xLabel(chart.x[it], byHour) }.toImmutableList(),
            pointCount = chart.x.size,
        )
    }

    /** A date in milliseconds as a day or an hour; anything smaller, as a number. */
    private fun xLabel(
        x: Long,
        byHour: Boolean,
    ): String =
        when {
            x < MILLISECONDS_SINCE_2001 -> integers.format(x)
            byHour -> hour.format(Instant.ofEpochMilli(x).atZone(zone))
            else -> day.format(Instant.ofEpochMilli(x).atZone(zone))
        }

    private fun at(epochSeconds: Long) = Instant.ofEpochSecond(epochSeconds).atZone(zone)

    private fun number(value: Double): String =
        if (value == value.roundToLong().toDouble()) integers.format(value.roundToLong()) else decimals.format(value)

    private fun sign(change: Double) = if (change < 0) "−" else "+"

    private companion object {
        const val PERCENT = 100L

        /** Telegram's x values in milliseconds are dates after 2001; smaller ones are counts or hours. */
        const val MILLISECONDS_SINCE_2001 = 1_000_000_000_000L
    }
}

/** The label of a number of the overview. */
internal val StatisticKind.label: Int
    get() =
        when (this) {
            StatisticKind.Members -> R.string.statistics_members
            StatisticKind.Messages -> R.string.statistics_messages
            StatisticKind.Viewers -> R.string.statistics_viewers
            StatisticKind.Senders -> R.string.statistics_senders
            StatisticKind.ViewsPerPost -> R.string.statistics_views_per_post
            StatisticKind.SharesPerPost -> R.string.statistics_shares_per_post
            StatisticKind.ReactionsPerPost -> R.string.statistics_reactions_per_post
            StatisticKind.ViewsPerStory -> R.string.statistics_views_per_story
            StatisticKind.SharesPerStory -> R.string.statistics_shares_per_story
            StatisticKind.ReactionsPerStory -> R.string.statistics_reactions_per_story
            StatisticKind.EnabledNotifications -> R.string.statistics_notifications
        }

/** The title of a graph. */
internal val StatisticGraphKind.title: Int
    get() =
        when (this) {
            StatisticGraphKind.Members -> R.string.statistics_graph_members
            StatisticGraphKind.Joins -> R.string.statistics_graph_joins
            StatisticGraphKind.JoinsBySource -> R.string.statistics_graph_joins_by_source
            StatisticGraphKind.Languages -> R.string.statistics_graph_languages
            StatisticGraphKind.MessageContent -> R.string.statistics_graph_messages
            StatisticGraphKind.Actions -> R.string.statistics_graph_actions
            StatisticGraphKind.ByHour -> R.string.statistics_graph_by_hour
            StatisticGraphKind.ByWeekday -> R.string.statistics_graph_by_weekday
            StatisticGraphKind.Muted -> R.string.statistics_graph_muted
            StatisticGraphKind.ViewsByHour -> R.string.statistics_graph_views_by_hour
            StatisticGraphKind.ViewsBySource -> R.string.statistics_graph_views_by_source
            StatisticGraphKind.Interactions -> R.string.statistics_graph_interactions
            StatisticGraphKind.Reactions -> R.string.statistics_graph_reactions
            StatisticGraphKind.StoryInteractions -> R.string.statistics_graph_story_interactions
            StatisticGraphKind.StoryReactions -> R.string.statistics_graph_story_reactions
            StatisticGraphKind.InstantViewInteractions -> R.string.statistics_graph_instant_view
        }

/** The graphs over the hours of a day, whose x values are hours. */
internal val StatisticGraphKind.isByHour: Boolean
    get() = this == StatisticGraphKind.ByHour || this == StatisticGraphKind.ViewsByHour
