package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Chart
import dev.kyralabs.pingogram.core.domain.model.ChartSeries
import dev.kyralabs.pingogram.core.domain.model.ChartStyle
import kotlinx.collections.immutable.toImmutableList
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The [Chart] in Telegram's statistics JSON (`statisticalGraphData.json_data`): `columns` holds `["x", …]` and one
 * `["y0", …]` per series, `types` each column's kind (`x`, `line`, `step`, `bar`, `area`), `names` the series' names,
 * and `stacked` / `percentage` how they add up. Telegram's `colors` are left out: the theme colors the series. Null for
 * data that does not parse or has no series.
 */
internal fun chartOf(json: String): Chart? =
    try {
        val root = JSONObject(json)
        val columns = root.getJSONArray("columns")
        val types = root.optJSONObject("types") ?: JSONObject()
        val names = root.optJSONObject("names") ?: JSONObject()
        var x: List<Long>? = null
        val series = mutableListOf<ChartSeries>()
        var style = ChartStyle.Line
        for (index in 0 until columns.length()) {
            val column = columns.getJSONArray(index)
            val id = column.getString(0)
            val values = column.longsAfterFirst()
            when (val type = types.optString(id)) {
                "x" -> {
                    x = values
                }

                else -> {
                    style = styleOf(type, style)
                    series += ChartSeries(names.optString(id, id), values.toImmutableList())
                }
            }
        }
        val xs = x
        if (xs == null || series.isEmpty() || series.any { it.values.size != xs.size }) {
            null
        } else {
            Chart(
                x = xs.toImmutableList(),
                series = series.toImmutableList(),
                style = style,
                stacked = root.optBoolean("stacked") || style == ChartStyle.Area,
                percentage = root.optBoolean("percentage"),
            )
        }
    } catch (_: JSONException) {
        null
    }

private fun styleOf(
    type: String,
    current: ChartStyle,
): ChartStyle =
    when (type) {
        "bar" -> ChartStyle.Bar
        "area" -> ChartStyle.Area
        else -> current
    }

private fun JSONArray.longsAfterFirst(): List<Long> = (1 until length()).map { getLong(it) }
