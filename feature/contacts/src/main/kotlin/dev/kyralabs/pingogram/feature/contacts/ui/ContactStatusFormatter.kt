package dev.kyralabs.pingogram.feature.contacts.ui

import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.model.isOnlineAt
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.contacts.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * What a contact's row says below the name, as the conversation's header says it (`REQUIREMENTS.md` FR-P1): "online",
 * "last seen just now", "5 minutes ago", "today at 9:41 AM", "yesterday at 9:41 AM", "Mar 17", or "3/17/2024" before
 * this year, and Telegram's rough times where the contact's privacy settings hide the exact one; "bot" for a bot.
 * In [zone] and [locale], with the platform's patterns ([TimePatterns]), built by the view model once per zone,
 * language and clock setting. Times in the future (a clock behind Telegram's) count as now.
 */
internal class ContactStatusFormatter(
    val zone: ZoneId,
    val locale: Locale,
    val is24HourClock: Boolean,
    patterns: TimePatterns,
) {
    private val time =
        patterns.formatter(if (is24HourClock) "Hm" else "hm") { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    private val dayAndMonth = patterns.formatter("MMMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    private val shortDate = patterns.formatter("yMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT) }

    /** The status of [user] as of [now]. */
    fun status(
        user: User,
        now: Instant,
    ): UiText {
        if (user.type == UserType.Bot) return UiText.resource(R.string.contacts_status_bot)
        return when (val status = user.status) {
            is UserStatus.Online -> {
                if (status.isOnlineAt(now.epochSecond)) {
                    UiText.resource(R.string.contacts_status_online)
                } else {
                    lastSeen(status.expiresAtEpochSeconds, now)
                }
            }

            is UserStatus.Offline -> {
                lastSeen(status.wasOnlineAtEpochSeconds, now)
            }

            UserStatus.Recently -> {
                UiText.resource(R.string.contacts_status_recently)
            }

            UserStatus.LastWeek -> {
                UiText.resource(R.string.contacts_status_week)
            }

            UserStatus.LastMonth -> {
                UiText.resource(R.string.contacts_status_month)
            }

            UserStatus.Empty -> {
                UiText.resource(R.string.contacts_status_long_ago)
            }
        }
    }

    /** "Last synced …" for contacts sync, last done at [syncedAt], as of [now]. */
    fun lastSynced(
        syncedAt: Instant,
        now: Instant,
    ): UiText {
        val seconds = (now.epochSecond - syncedAt.epochSecond).coerceAtLeast(0)
        val dateTime = syncedAt.atZone(zone)
        return when {
            seconds < SECONDS_PER_MINUTE -> {
                UiText.resource(R.string.contacts_sync_last_just_now)
            }

            seconds < SECONDS_PER_HOUR -> {
                val minutes = (seconds / SECONDS_PER_MINUTE).toInt()
                UiText.plural(R.plurals.contacts_sync_last_minutes, minutes, minutes)
            }

            !dateTime.toLocalDate().isBefore(now.atZone(zone).toLocalDate()) -> {
                UiText.resource(R.string.contacts_sync_last_today, time.format(dateTime))
            }

            else -> {
                UiText.resource(R.string.contacts_sync_last_date, dayAndMonth.format(dateTime))
            }
        }
    }

    /** Whether this formatter is the one for [zone], [locale] and the clock setting. */
    fun isFor(
        zone: ZoneId,
        locale: Locale,
        is24HourClock: Boolean,
    ): Boolean = this.zone == zone && this.locale == locale && this.is24HourClock == is24HourClock

    /** "last seen …" for a user last online at [epochSeconds] (Unix time), as of [now]. */
    private fun lastSeen(
        epochSeconds: Long,
        now: Instant,
    ): UiText {
        val seconds = (now.epochSecond - epochSeconds).coerceAtLeast(0)
        val dateTime = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        val day = dateTime.toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return when {
            seconds < SECONDS_PER_MINUTE -> {
                UiText.resource(R.string.contacts_status_just_now)
            }

            seconds < SECONDS_PER_HOUR -> {
                val minutes = (seconds / SECONDS_PER_MINUTE).toInt()
                UiText.plural(R.plurals.contacts_status_minutes, minutes, minutes)
            }

            !day.isBefore(today) -> {
                UiText.resource(R.string.contacts_status_today, time.format(dateTime))
            }

            day == today.minusDays(1) -> {
                UiText.resource(R.string.contacts_status_yesterday, time.format(dateTime))
            }

            day.year == today.year -> {
                UiText.resource(R.string.contacts_status_date, dayAndMonth.format(dateTime))
            }

            else -> {
                UiText.resource(R.string.contacts_status_date, shortDate.format(dateTime))
            }
        }
    }

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
