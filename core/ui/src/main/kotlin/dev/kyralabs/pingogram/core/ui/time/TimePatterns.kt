package dev.kyralabs.pingogram.core.ui.time

import android.content.Context
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.text.format.DateFormat
import java.util.Locale

/**
 * How the device formats times, for the formatters of ViewModels (the chat list's times, the conversation's message
 * times, date separators and "last seen"): the user's 12- or 24-hour setting, and the platform's best pattern for a
 * skeleton in a language. [AndroidTimePatterns] asks the platform; tests pass fixed patterns, so ViewModels stay on
 * the JVM. `:app` binds it.
 */
interface TimePatterns {
    /** Whether the user chose the 24-hour clock in the system settings (or the language uses it by default). */
    fun is24HourClock(): Boolean

    /**
     * The pattern that shows the fields of [skeleton] ("Hm", "hm", "MMMd", "yMd") in [locale]'s order and separators,
     * e.g. "MMM d" for "MMMd" in English, "d. MMM" in German.
     */
    fun bestPattern(
        locale: Locale,
        skeleton: String,
    ): String

    /**
     * [seconds] from now, worded in [locale]: "in 5 minutes", "2 days ago"; null where the platform cannot word it
     * (tests on the JVM), which shows the moment as a date instead.
     */
    fun relativeTime(
        seconds: Long,
        locale: Locale,
    ): String? = null
}

/**
 * [TimePatterns] of the platform: `android.text.format.DateFormat`, with the application [context]. Both may be called
 * off the main thread.
 */
class AndroidTimePatterns(
    private val context: Context,
) : TimePatterns {
    override fun is24HourClock(): Boolean = DateFormat.is24HourFormat(context)

    override fun bestPattern(
        locale: Locale,
        skeleton: String,
    ): String = DateFormat.getBestDateTimePattern(locale, skeleton)

    override fun relativeTime(
        seconds: Long,
        locale: Locale,
    ): String = relativeText(seconds, RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale)))
}

/**
 * [seconds] from now in the largest unit that fits, as ICU words it: "in 5 minutes", "2 days ago", "now" within a
 * minute.
 */
internal fun relativeText(
    seconds: Long,
    formatter: RelativeDateTimeFormatter,
): String {
    val distance = kotlin.math.abs(seconds)
    if (distance <
        SECONDS_PER_MINUTE
    ) {
        return formatter.format(
            RelativeDateTimeFormatter.Direction.PLAIN,
            RelativeDateTimeFormatter.AbsoluteUnit.NOW,
        )
    }
    val (amount, unit) =
        when {
            distance < SECONDS_PER_HOUR -> {
                distance / SECONDS_PER_MINUTE to
                    RelativeDateTimeFormatter.RelativeUnit.MINUTES
            }

            distance < SECONDS_PER_DAY -> {
                distance / SECONDS_PER_HOUR to RelativeDateTimeFormatter.RelativeUnit.HOURS
            }

            distance < SECONDS_PER_WEEK -> {
                distance / SECONDS_PER_DAY to RelativeDateTimeFormatter.RelativeUnit.DAYS
            }

            distance < SECONDS_PER_MONTH -> {
                distance / SECONDS_PER_WEEK to RelativeDateTimeFormatter.RelativeUnit.WEEKS
            }

            distance < SECONDS_PER_YEAR -> {
                distance / SECONDS_PER_MONTH to RelativeDateTimeFormatter.RelativeUnit.MONTHS
            }

            else -> {
                distance / SECONDS_PER_YEAR to RelativeDateTimeFormatter.RelativeUnit.YEARS
            }
        }
    val direction =
        if (seconds <
            0
        ) {
            RelativeDateTimeFormatter.Direction.LAST
        } else {
            RelativeDateTimeFormatter.Direction.NEXT
        }
    return formatter.format(amount.toDouble(), direction, unit)
}

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE
private const val SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR
private const val SECONDS_PER_WEEK = 7 * SECONDS_PER_DAY
private const val SECONDS_PER_MONTH = 30 * SECONDS_PER_DAY
private const val SECONDS_PER_YEAR = 365 * SECONDS_PER_DAY
