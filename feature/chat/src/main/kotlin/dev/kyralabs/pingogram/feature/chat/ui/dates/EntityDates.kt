package dev.kyralabs.pingogram.feature.chat.ui.dates

import dev.kyralabs.pingogram.core.domain.model.DateTimeFormat
import dev.kyralabs.pingogram.core.domain.model.DateTimePrecision
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * How dates in messages ([TextEntityType.DateTime]) are worded for their reader: in [zone] and [locale], on the
 * 24-hour clock where [is24HourClock], with the platform's best pattern for the parts a date's format asks for
 * ([patterns]); its formatters are made as texts need them and kept.
 */
internal class EntityDates(
    private val zone: ZoneId,
    private val locale: Locale,
    private val is24HourClock: Boolean,
    private val patterns: TimePatterns,
) {
    private val formatters = ConcurrentHashMap<String, DateTimeFormatter>()

    /**
     * [entity] as its reader should see it, as of [now]: with as much of the date and the time as its
     * [DateTimeFormat] asks for, or relative to now ("in 3 days") where the platform words that; null where the format
     * shows nothing, or none is given, which keeps the text as its sender wrote it.
     */
    fun text(
        entity: TextEntityType.DateTime,
        now: Instant,
    ): String? =
        when (val format = entity.format) {
            null -> {
                null
            }

            DateTimeFormat.Relative -> {
                patterns.relativeTime(entity.unixTime - now.epochSecond, locale) ?: full(entity.unixTime)
            }

            is DateTimeFormat.Absolute -> {
                skeletonOf(format)?.let { skeleton -> formatterOf(skeleton).format(zoned(entity.unixTime)) }
            }
        }

    /** [epochSeconds] (Unix time) with its weekday, date and time, as the date sheet shows it. */
    fun full(epochSeconds: Long): String = formatterOf(FULL_DATE + clockSkeleton()).format(zoned(epochSeconds))

    private fun zoned(epochSeconds: Long) = Instant.ofEpochSecond(epochSeconds).atZone(zone)

    private fun clockSkeleton(seconds: Boolean = false): String =
        (if (is24HourClock) "Hm" else "hm") + if (seconds) "s" else ""

    /** The skeleton of [format]'s parts; null where it shows neither the date nor the time. */
    private fun skeletonOf(format: DateTimeFormat.Absolute): String? {
        val weekday = if (format.showDayOfWeek) "EEEE" else ""
        val date =
            when (format.date) {
                DateTimePrecision.None -> weekday
                DateTimePrecision.Short -> "yMd" + (if (format.showDayOfWeek) "EEE" else "")
                DateTimePrecision.Long -> "yMMMMd$weekday"
            }
        val time =
            when (format.time) {
                DateTimePrecision.None -> ""
                DateTimePrecision.Short -> clockSkeleton()
                DateTimePrecision.Long -> clockSkeleton(seconds = true)
            }
        return (date + time).ifEmpty { null }
    }

    /** The platform's pattern for [skeleton], or java.time's medium date and short time if java.time cannot read it. */
    private fun formatterOf(skeleton: String): DateTimeFormatter =
        formatters.getOrPut(skeleton) {
            try {
                DateTimeFormatter.ofPattern(patterns.bestPattern(locale, skeleton), locale)
            } catch (_: IllegalArgumentException) {
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
            }
        }

    private companion object {
        /** The weekday, the month in full, the day and the year, before the time of day. */
        const val FULL_DATE = "yMMMMEEEEd"
    }
}
