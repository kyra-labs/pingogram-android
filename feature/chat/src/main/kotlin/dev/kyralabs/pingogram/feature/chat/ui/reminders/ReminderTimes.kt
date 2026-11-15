package dev.kyralabs.pingogram.feature.chat.ui.reminders

import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatTimeFormatter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

/**
 * The reminder sheet's times, in the reader's zone, language and clock as they are when asked: the choices of "When"
 * ([presetAt], and [custom] for a picked one) and how a time is worded ("Tomorrow, 9:00 AM").
 */
internal class ReminderTimes(
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
) {
    /** Now, in Unix seconds. */
    fun now(): Long = clock.now().epochSecond

    /** The choices of "When": the quick ones that are still ahead, then "Pick a date and time" with [custom]. */
    fun choices(custom: Long?): ImmutableList<ReminderChoice> =
        ReminderOption.entries
            .mapNotNull { choice ->
                val at = if (choice == ReminderOption.Custom) custom else presetAt(choice, clock.now(), clock.zone())
                when {
                    choice == ReminderOption.Custom -> ReminderChoice(choice, at, at?.let(::whenText))
                    at == null -> null
                    else -> ReminderChoice(choice, at, whenText(at))
                }
            }.toImmutableList()

    /** [at] (Unix seconds) as its day and time: "Today, 10:41 AM", "Monday, November 23, 9:00 AM". */
    fun whenText(at: Long): UiText {
        val formatter = ChatTimeFormatter(clock.zone(), Locale.getDefault(), timePatterns.is24HourClock(), timePatterns)
        val today = clock.now().atZone(clock.zone()).toLocalDate()
        return UiText.resource(
            R.string.reminder_day_time,
            formatter.dateLabel(formatter.day(at), today),
            formatter.time(at),
        )
    }
}
