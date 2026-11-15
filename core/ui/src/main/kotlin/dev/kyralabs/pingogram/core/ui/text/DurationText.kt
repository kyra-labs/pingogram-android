package dev.kyralabs.pingogram.core.ui.text

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import dev.kyralabs.pingogram.core.ui.R
import java.util.Locale

/**
 * A remaining time for countdowns ("42 seconds", "5 minutes", "2 hours"), rounded up to the largest whole unit, so it
 * never claims less time than is left. Plurals rather than `4:59`, which screen readers read out poorly.
 */
@Composable
@ReadOnlyComposable
fun durationText(seconds: Long): String = durationText(LocalResources.current, seconds)

/** [durationText] outside composition. */
fun durationText(
    resources: Resources,
    seconds: Long,
): String {
    val remaining = seconds.coerceAtLeast(0)
    return when {
        remaining < SECONDS_PER_MINUTE -> {
            resources.getQuantityString(R.plurals.duration_seconds, remaining.toInt(), remaining.toInt())
        }

        remaining < SECONDS_PER_HOUR -> {
            val minutes = ceilDiv(remaining, SECONDS_PER_MINUTE).toInt()
            resources.getQuantityString(R.plurals.duration_minutes, minutes, minutes)
        }

        else -> {
            val hours = ceilDiv(remaining, SECONDS_PER_HOUR).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            resources.getQuantityString(R.plurals.duration_hours, hours, hours)
        }
    }
}

private fun ceilDiv(
    value: Long,
    divisor: Long,
): Long = (value + divisor - 1) / divisor

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L

/**
 * A countdown on screen as a clock (owner decision 2026-11-15, board 23): `m:ss` under an hour ("4:59", "0:42"),
 * `h:mm:ss` above ("1:04:59"), in the language's digits. Show it in a style with tabular figures (`tnum`) so it does
 * not shift as it counts; screen readers hear [durationText] instead, which reads better.
 */
fun clockText(
    seconds: Long,
    locale: Locale = Locale.getDefault(),
): String {
    val remaining = seconds.coerceAtLeast(0)
    val hours = remaining / SECONDS_PER_HOUR
    val minutes = remaining % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val secs = remaining % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(locale, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(locale, "%d:%02d", minutes, secs)
    }
}
