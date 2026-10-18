package dev.kyralabs.pingogram.core.ui.text

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import dev.kyralabs.pingogram.core.ui.R

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
