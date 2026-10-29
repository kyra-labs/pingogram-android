package dev.kyralabs.pingogram.feature.profile.chatinfo.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.ChartStyle
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A statistics chart (`docs/DESIGN_SYSTEM.md` §6, "Statistics"), drawn on a canvas in the draw phase, its paths built
 * once per size: lines, stacked bars, or stacked areas of shares of the whole, over `outlineVariant` grid lines, each
 * series in a color role of [ChartColors]. The highest value is labelled above it and the x axis's first, middle and
 * last values below it (`labelSmall`, `onSurfaceVariant`), then a legend where there are several series. Screen
 * readers hear each series' first, last and highest value.
 */
@Composable
internal fun StatisticsChart(
    chart: ChartUi,
    modifier: Modifier = Modifier,
) {
    val colors = rememberChartColors()
    val labelStyle = MaterialTheme.typography.labelSmall
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Text(
            chart.maxLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = labelStyle.numericContent,
            modifier = Modifier.clearAndSetSemantics {},
        )
        ChartPlot(chart, colors)
        if (chart.minValue != 0L) {
            Text(
                chart.minLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = labelStyle.numericContent,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
            chart.xLabels.forEach { label ->
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = labelStyle.numericContent)
            }
        }
        if (chart.series.size > 1) Legend(chart.series, colors)
    }
}

/**
 * The plot: grid lines, then each series' path in its color, built once per size; one description for screen readers.
 */
@Composable
private fun ChartPlot(
    chart: ChartUi,
    colors: ImmutableList<Color>,
) {
    val resources = LocalResources.current
    val grid = MaterialTheme.colorScheme.outlineVariant
    val line = MaterialTheme.sizes.chartLine
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(MaterialTheme.sizes.chartHeight)
            .semantics {
                contentDescription =
                    chart.series.joinToString("\n") { series ->
                        resources.getString(
                            R.string.statistics_chart_series,
                            series.name,
                            series.first,
                            series.last,
                            series.highest,
                        )
                    }
            }.drawWithCache {
                val stroke = Stroke(width = line.toPx())
                val shapes = chartShapes(chart, size)
                onDrawBehind {
                    repeat(GRID_LINES + 1) { i ->
                        val y = size.height * i / GRID_LINES
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), Stroke.HairlineWidth)
                    }
                    shapes.forEachIndexed { index, path ->
                        val color = colors[index % colors.size]
                        drawPath(path, color, style = if (chart.style == ChartStyle.Line) stroke else Fill)
                    }
                }
            },
    )
}

@Composable
private fun Legend(
    series: ImmutableList<SeriesUi>,
    colors: ImmutableList<Color>,
) {
    FlowRow(
        Modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        series.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            ) {
                Box(
                    Modifier
                        .size(MaterialTheme.sizes.badgeIcon)
                        .background(colors[index % colors.size], CircleShape),
                )
                Text(item.name, style = MaterialTheme.typography.labelMedium.userContent)
            }
        }
    }
}

/**
 * The series' colors, from the scheme's roles that keep 3:1 against `surface` in every theme: `primary`, `tertiary`,
 * `secondary`, `error`, `onSurfaceVariant`, then again.
 */
@Composable
private fun rememberChartColors(): ImmutableList<Color> {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        persistentListOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.error, scheme.onSurfaceVariant)
    }
}

/**
 * Each series' path over [size]: a line through its points, or for stacked bars and areas, its band above the series
 * before it. Shares of the whole are each point's share of the sum there.
 */
private fun chartShapes(
    chart: ChartUi,
    size: Size,
): List<Path> {
    val count = chart.pointCount
    val range = chart.maxValue - chart.minValue
    if (count == 0 || range <= 0) return emptyList()
    val step = if (count > 1) size.width / (count - 1) else size.width
    val scale = size.height / range

    fun value(
        series: Int,
        point: Int,
    ): Float {
        val raw = chart.series[series].values[point].toFloat()
        if (!chart.percentage) return raw
        val sum = chart.series.sumOf { it.values[point] }.toFloat()
        return if (sum == 0f) 0f else raw / sum * PERCENT
    }
    val below = FloatArray(count)
    return chart.series.indices.map { series ->
        when {
            chart.style == ChartStyle.Line -> {
                linePath(count, step) { point ->
                    size.height - (value(series, point) - chart.minValue) * scale
                }
            }

            chart.style == ChartStyle.Bar -> {
                barPath(count, size, below, scale) { point -> value(series, point) }
            }

            else -> {
                areaPath(count, size, below, scale) { point -> value(series, point) }
            }
        }
    }
}

private inline fun linePath(
    count: Int,
    step: Float,
    y: (Int) -> Float,
) = Path().apply {
    moveTo(0f, y(0))
    for (point in 1 until count) lineTo(point * step, y(point))
}

/** Bars side by side filling the width, each stacked on [below], which then grows by this series. */
private inline fun barPath(
    count: Int,
    size: Size,
    below: FloatArray,
    scale: Float,
    value: (Int) -> Float,
) = Path().apply {
    val width = size.width / count
    for (point in 0 until count) {
        val bottom = size.height - below[point] * scale
        val height = value(point) * scale
        addRect(Rect(point * width, bottom - height, (point + 1) * width, bottom))
        below[point] += value(point)
    }
}

/** The band between [below] and [below] plus this series, which [below] then becomes. */
private inline fun areaPath(
    count: Int,
    size: Size,
    below: FloatArray,
    scale: Float,
    value: (Int) -> Float,
) = Path().apply {
    val step = if (count > 1) size.width / (count - 1) else size.width
    val top = FloatArray(count) { below[it] + value(it) }
    moveTo(0f, size.height - top[0] * scale)
    for (point in 1 until count) lineTo(point * step, size.height - top[point] * scale)
    for (point in count - 1 downTo 0) lineTo(point * step, size.height - below[point] * scale)
    close()
    top.copyInto(below)
}

/** The grid's spaces: lines at 0, 25, 50, 75 and 100% of the highest value. */
private const val GRID_LINES = 4

private const val PERCENT = 100f
