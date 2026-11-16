package dev.kyralabs.pingogram.core.ui.chart

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.ChartStyle
import kotlinx.collections.immutable.ImmutableList

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
