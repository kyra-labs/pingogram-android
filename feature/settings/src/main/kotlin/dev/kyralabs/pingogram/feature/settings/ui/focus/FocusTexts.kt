package dev.kyralabs.pingogram.feature.settings.ui.focus

import android.icu.text.ListFormatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.core.os.ConfigurationCompat
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import dev.kyralabs.pingogram.feature.settings.R
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

// How the Focus page words a profile: when it runs and what it lets through, in the app's language.

private val Weekdays =
    setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )
private val WeekendDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
private const val MINUTES_PER_HOUR = 60

/** The app's locale. */
@Composable
internal fun appLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) { ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault() }
}

/** A time of day [minute] minutes after midnight, in the language's short style ("9:00 AM", "21:00"). */
internal fun focusTime(
    minute: Int,
    locale: Locale,
): String =
    LocalTime
        .of(minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

/** The days of the week in the order [locale]'s calendar starts its week with. */
internal fun weekDays(locale: Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return List(DayOfWeek.entries.size) { first.plus(it.toLong()) }
}

/** "Weekdays · 9:00 AM–6:00 PM", "Every day · 10:30 PM–7:00 AM, next day". */
@Composable
internal fun scheduleText(schedule: FocusSchedule): String {
    val resources = LocalResources.current
    val locale = appLocale()
    return remember(schedule, locale, resources) {
        val days =
            when (schedule.days) {
                DayOfWeek.entries.toSet() -> {
                    resources.getString(R.string.settings_focus_every_day)
                }

                Weekdays -> {
                    resources.getString(R.string.settings_focus_weekdays)
                }

                WeekendDays -> {
                    resources.getString(R.string.settings_focus_weekends)
                }

                else -> {
                    weekDays(locale)
                        .filter { it in schedule.days }
                        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
                }
            }
        val start = focusTime(schedule.startMinute, locale)
        val end = focusTime(schedule.endMinute, locale)
        val range =
            if (schedule.endsNextDay) {
                resources.getString(R.string.settings_focus_range_next_day, start, end)
            } else {
                resources.getString(R.string.settings_focus_range, start, end)
            }
        resources.getString(R.string.settings_focus_schedule, days, range)
    }
}

/** "Lets through 2 folders, 3 chats and calls from contacts", or that it holds back every chat. */
@Composable
internal fun allowsText(item: FocusProfileItem): String {
    val resources = LocalResources.current
    val locale = appLocale()
    return remember(item.folderCount, item.chatCount, item.allowsCalls, locale, resources) {
        val parts =
            buildList {
                if (item.folderCount > 0) {
                    add(
                        resources.getQuantityString(
                            R.plurals.settings_focus_folders,
                            item.folderCount,
                            item.folderCount,
                        ),
                    )
                }
                if (item.chatCount > 0) {
                    add(resources.getQuantityString(R.plurals.settings_focus_chats, item.chatCount, item.chatCount))
                }
                if (item.allowsCalls) add(resources.getString(R.string.settings_focus_calls_from_contacts))
            }
        if (parts.isEmpty()) {
            resources.getString(R.string.settings_focus_allows_nothing)
        } else {
            resources.getString(R.string.settings_focus_allows, ListFormatter.getInstance(locale).format(parts))
        }
    }
}
