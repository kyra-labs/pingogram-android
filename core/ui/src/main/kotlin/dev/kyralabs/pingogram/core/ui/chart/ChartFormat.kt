package dev.kyralabs.pingogram.core.ui.chart

import dev.kyralabs.pingogram.core.domain.model.Chart
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.toImmutableList
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Makes the [ChartUi] that [StatisticsChart] draws from a [Chart] (Telegram's chart JSON, as `core:data` reads it):
 * its numbers in [locale]'s format, its x values as days or hours at [zone].
 */
class ChartFormat(
    locale: Locale,
    private val zone: ZoneId,
    timePatterns: TimePatterns,
) {
    private val integers = NumberFormat.getIntegerInstance(locale)
    private val day = DateTimeFormatter.ofPattern(timePatterns.bestPattern(locale, "MMMd"), locale)
    private val hour =
        DateTimeFormatter.ofPattern(
            timePatterns.bestPattern(locale, if (timePatterns.is24HourClock()) "Hm" else "hm"),
            locale,
        )

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

    private companion object {
        const val PERCENT = 100L

        /** Telegram's x values in milliseconds are dates after 2001; smaller ones are counts or hours. */
        const val MILLISECONDS_SINCE_2001 = 1_000_000_000_000L
    }
}
