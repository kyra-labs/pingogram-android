package dev.kyralabs.pingogram.feature.chat.ui.secret

import android.content.res.Resources
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The self-destruct timer of secret chats (REQUIREMENTS.md FR-E1): the choices and how they read.

/**
 * The timers the sheet offers, in seconds, 0 for off: the steps Telegram's apps offer, from a second to a week. TDLib
 * takes any number of seconds in a secret chat (`setChatMessageAutoDeleteTime`; other chats take whole days only), so
 * a timer the other user's app set to another value is shown among them ([timerChoices]).
 */
internal val SELF_DESTRUCT_CHOICES: List<Int> =
    listOf(0, 1, 2, 5, 10, 15, 30, MINUTE, HOUR, DAY, WEEK)

/** [SELF_DESTRUCT_CHOICES], with [current] in its place among them if it is another value. */
internal fun timerChoices(current: Int): List<Int> {
    val known = current < 0 || current in SELF_DESTRUCT_CHOICES
    return if (known) SELF_DESTRUCT_CHOICES else (SELF_DESTRUCT_CHOICES + current).sorted()
}

/**
 * A timer in words: "Off", "30 seconds", "1 minute", "1 hour", "1 day", "1 week", in the largest unit it reaches, whole
 * units only (Telegram's own steps are whole units).
 */
internal fun selfDestructText(
    resources: Resources,
    seconds: Int,
): String {
    val (count, unit) = unitOf(seconds) ?: return resources.getString(R.string.secret_timer_off)
    val plural =
        when (unit) {
            TimerUnit.Seconds -> UiR.plurals.duration_seconds
            TimerUnit.Minutes -> UiR.plurals.duration_minutes
            TimerUnit.Hours -> UiR.plurals.duration_hours
            TimerUnit.Days -> R.plurals.secret_timer_days
            TimerUnit.Weeks -> R.plurals.secret_timer_weeks
        }
    return resources.getQuantityString(plural, count, count)
}

/** A timer in short: "30s", "5m", "1h", "1d", "1w", for the top bar's badge and a bubble's time; empty for off. */
internal fun selfDestructShortText(
    resources: Resources,
    seconds: Int,
): String {
    val (count, unit) = unitOf(seconds) ?: return ""
    val text =
        when (unit) {
            TimerUnit.Seconds -> R.string.secret_timer_short_seconds
            TimerUnit.Minutes -> R.string.secret_timer_short_minutes
            TimerUnit.Hours -> R.string.secret_timer_short_hours
            TimerUnit.Days -> R.string.secret_timer_short_days
            TimerUnit.Weeks -> R.string.secret_timer_short_weeks
        }
    return resources.getString(text, count)
}

private enum class TimerUnit { Seconds, Minutes, Hours, Days, Weeks }

/** The count of the largest unit [seconds] reaches, rounded down; null for off. */
private fun unitOf(seconds: Int): Pair<Int, TimerUnit>? =
    when {
        seconds <= 0 -> null
        seconds < MINUTE -> seconds to TimerUnit.Seconds
        seconds < HOUR -> seconds / MINUTE to TimerUnit.Minutes
        seconds < DAY -> seconds / HOUR to TimerUnit.Hours
        seconds < WEEK -> seconds / DAY to TimerUnit.Days
        else -> seconds / WEEK to TimerUnit.Weeks
    }

private const val MINUTE = 60
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY
