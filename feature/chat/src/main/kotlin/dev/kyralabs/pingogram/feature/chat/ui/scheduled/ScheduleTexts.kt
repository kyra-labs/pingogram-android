package dev.kyralabs.pingogram.feature.chat.ui.scheduled

import android.content.res.Resources
import dev.kyralabs.pingogram.core.domain.model.MessageSchedule
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatTimeFormatter
import java.time.LocalDate

// How scheduled messages are worded (REQUIREMENTS.md FR-C5): how often they repeat, and when they go out.

/**
 * How often a scheduled message repeats every [seconds] (td_api.tl `messageSchedulingStateSendAtDate.repeat_period`):
 * "Daily", "Weekly", "Every 2 weeks", "Monthly", "Every 3 months", "Every 6 months", "Yearly" for the periods Telegram
 * offers, "Every 5 days" or "Every 5 minutes" (the test DC's) for others.
 */
internal fun repeatLabel(
    resources: Resources,
    seconds: Int,
): String {
    val days = seconds / SECONDS_PER_DAY
    return when {
        seconds % SECONDS_PER_DAY != 0 -> {
            val minutes = (seconds / SECONDS_PER_MINUTE).coerceAtLeast(1)
            resources.getQuantityString(R.plurals.scheduled_repeat_minutes, minutes, minutes)
        }

        days == 1 -> {
            resources.getString(R.string.scheduled_repeat_daily)
        }

        days == DAYS_WEEK -> {
            resources.getString(R.string.scheduled_repeat_weekly)
        }

        days == DAYS_TWO_WEEKS -> {
            resources.getString(R.string.scheduled_repeat_two_weeks)
        }

        days == DAYS_MONTH -> {
            resources.getString(R.string.scheduled_repeat_monthly)
        }

        days == DAYS_THREE_MONTHS -> {
            resources.getString(R.string.scheduled_repeat_three_months)
        }

        days == DAYS_SIX_MONTHS -> {
            resources.getString(R.string.scheduled_repeat_six_months)
        }

        days == DAYS_YEAR -> {
            resources.getString(R.string.scheduled_repeat_yearly)
        }

        else -> {
            resources.getQuantityString(R.plurals.scheduled_repeat_days, days, days)
        }
    }
}

/**
 * When a bubble's scheduled message goes out, for screen readers: "Sends 9:41 AM" (its [time]), or "Sends when Ada is
 * online"; with how often it repeats after it, where it does.
 */
internal fun scheduleDescription(
    resources: Resources,
    schedule: BubbleSchedule,
    time: String,
): String =
    when (schedule) {
        is BubbleSchedule.At -> {
            val sends = resources.getString(R.string.scheduled_sends_at, time)
            if (schedule.repeatPeriod > 0) {
                val repeats =
                    resources.getString(
                        R.string.scheduled_bubble_repeats,
                        repeatLabel(resources, schedule.repeatPeriod),
                    )
                "$sends, $repeats"
            } else {
                sends
            }
        }

        is BubbleSchedule.WhenOnline -> {
            whenOnlineText(resources, schedule)
        }
    }

/** "Sends when Ada is online", or without the name while it is not known. */
internal fun whenOnlineText(
    resources: Resources,
    schedule: BubbleSchedule.WhenOnline,
): String =
    schedule.user?.let {
        resources.getString(R.string.scheduled_sends_when_user_online, it.resolve(resources).bidiIsolated())
    }
        ?: resources.getString(R.string.scheduled_sends_when_online)

/**
 * When a message with [schedule] goes out, as of [today]: "Today, 9:41 AM", "Friday, October 30, 9:41 AM"; null for
 * one that waits for the other user to come online.
 */
internal fun ChatTimeFormatter.dayAndTime(
    schedule: MessageSchedule,
    today: LocalDate,
): UiText? =
    schedule.sendDate?.let { sendDate ->
        UiText.resource(R.string.scheduled_day_time, dateLabel(day(sendDate), today), time(sendDate))
    }

/** The bar's "Next: Today, 9:41 AM" for the first scheduled message, or "When Ada is online" ([user], if known). */
internal fun ChatTimeFormatter.nextText(
    schedule: MessageSchedule,
    today: LocalDate,
    user: UiText?,
): UiText =
    dayAndTime(schedule, today)?.let { UiText.resource(R.string.scheduled_bar_next, it) }
        ?: user?.let { UiText.resource(R.string.scheduled_when_user_online, it.bidiIsolated()) }
        ?: UiText.resource(R.string.scheduled_when_online)

/** The actions sheet's "Sends Today, 9:41 AM", or "Sends when Ada is online" ([user], if known). */
internal fun ChatTimeFormatter.sendsText(
    schedule: MessageSchedule,
    today: LocalDate,
    user: UiText?,
): UiText =
    dayAndTime(schedule, today)?.let { UiText.resource(R.string.scheduled_sends_at, it) }
        ?: user?.let { UiText.resource(R.string.scheduled_sends_when_user_online, it.bidiIsolated()) }
        ?: UiText.resource(R.string.scheduled_sends_when_online)

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_DAY = 86_400

// The periods Telegram offers (td_api.tl): a day, a week, two weeks, 30, 91, 182 and 365 days.
private const val DAYS_WEEK = 7
private const val DAYS_TWO_WEEKS = 14
private const val DAYS_MONTH = 30
private const val DAYS_THREE_MONTHS = 91
private const val DAYS_SIX_MONTHS = 182
private const val DAYS_YEAR = 365
