package dev.kyralabs.pingogram.core.ui.time

import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * When a user was last online, as the conversation's top bar and the profile say it (`REQUIREMENTS.md` FR-C8,
 * FR-P2), in [zone] and [locale], with the platform's patterns ([TimePatterns]): "online" until the status expires,
 * then "last seen just now", "5 minutes ago", "today at 9:41 AM", "yesterday at 9:41 AM", "Mar 17", or "3/17/2024"
 * before this year; the rough bucket ("recently", "within a week") where the user's privacy settings hide the time.
 *
 * Built in the ViewModel, so formatters are created once per zone, locale and clock setting ([isFor]). Times in the
 * future (a clock behind Telegram's) count as now. A platform pattern java.time cannot read falls back to java.time's
 * own localized style.
 */
class UserStatusFormatter(
    val zone: ZoneId,
    val locale: Locale,
    val is24HourClock: Boolean,
    patterns: TimePatterns,
) {
    private val time =
        patterns.formatter(if (is24HourClock) "Hm" else "hm") { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    private val dayAndMonth = patterns.formatter("MMMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    private val shortDate = patterns.formatter("yMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT) }

    /** "online" or when the user with [status] was last online, as of [now]. */
    fun status(
        status: UserStatus,
        now: Instant,
    ): UiText =
        when (status) {
            is UserStatus.Online -> {
                if (status.expiresAtEpochSeconds > now.epochSecond) {
                    UiText.resource(R.string.user_status_online)
                } else {
                    lastSeen(status.expiresAtEpochSeconds, now)
                }
            }

            is UserStatus.Offline -> {
                lastSeen(status.wasOnlineAtEpochSeconds, now)
            }

            UserStatus.Recently -> {
                UiText.resource(R.string.user_status_last_seen_recently)
            }

            UserStatus.LastWeek -> {
                UiText.resource(R.string.user_status_last_seen_week)
            }

            UserStatus.LastMonth -> {
                UiText.resource(R.string.user_status_last_seen_month)
            }

            UserStatus.Empty -> {
                UiText.resource(R.string.user_status_last_seen_long_ago)
            }
        }

    /** "last seen …" for a user last online at [epochSeconds] (Unix time), as of [now]. */
    fun lastSeen(
        epochSeconds: Long,
        now: Instant,
    ): UiText {
        val seconds = (now.epochSecond - epochSeconds).coerceAtLeast(0)
        val dateTime = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        val day = dateTime.toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return when {
            seconds < SECONDS_PER_MINUTE -> {
                UiText.resource(R.string.user_status_last_seen_just_now)
            }

            seconds < SECONDS_PER_HOUR -> {
                val minutes = (seconds / SECONDS_PER_MINUTE).toInt()
                UiText.plural(R.plurals.user_status_last_seen_minutes, minutes, minutes)
            }

            !day.isBefore(today) -> {
                UiText.resource(R.string.user_status_last_seen_today, time.format(dateTime))
            }

            day == today.minusDays(1) -> {
                UiText.resource(R.string.user_status_last_seen_yesterday, time.format(dateTime))
            }

            day.year == today.year -> {
                UiText.resource(R.string.user_status_last_seen_date, dayAndMonth.format(dateTime))
            }

            else -> {
                UiText.resource(R.string.user_status_last_seen_date, shortDate.format(dateTime))
            }
        }
    }

    /** Whether this formatter is the one for [zone], [locale] and the clock setting. */
    fun isFor(
        zone: ZoneId,
        locale: Locale,
        is24HourClock: Boolean,
    ): Boolean = this.zone == zone && this.locale == locale && this.is24HourClock == is24HourClock

    /** The platform's pattern for [skeleton], or [fallback] if java.time cannot read it. */
    private fun TimePatterns.formatter(
        skeleton: String,
        fallback: () -> DateTimeFormatter,
    ): DateTimeFormatter =
        try {
            DateTimeFormatter.ofPattern(bestPattern(locale, skeleton), locale)
        } catch (_: IllegalArgumentException) {
            fallback().withLocale(locale)
        }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
        const val SECONDS_PER_HOUR = 3_600L
    }
}
