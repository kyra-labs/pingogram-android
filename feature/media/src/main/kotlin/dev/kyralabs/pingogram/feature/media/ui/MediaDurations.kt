package dev.kyralabs.pingogram.feature.media.ui

import java.util.Locale

/** A media length or position, "0:05", "1:05" or "1:01:05", in the digits of [locale]. Negative counts as 0. */
internal fun formatDuration(
    seconds: Int,
    locale: Locale,
): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / SECONDS_PER_HOUR
    val minutes = total / SECONDS_PER_MINUTE % MINUTES_PER_HOUR
    val rest = total % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(locale, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(locale, "%d:%02d", minutes, rest)
    }
}

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_HOUR = 3_600
