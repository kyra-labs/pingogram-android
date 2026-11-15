package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.annotation.StringRes
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import java.time.DayOfWeek

// The profiles the Focus page offers to start from (board 31): Work hours, Sleep, Weekend, or from scratch. The editor
// opens filled in with them; nothing is saved before "Save".

private const val HOUR = 60

@StringRes
internal fun FocusTemplate.title(): Int =
    when (this) {
        FocusTemplate.WorkHours -> R.string.settings_focus_template_work
        FocusTemplate.Sleep -> R.string.settings_focus_template_sleep
        FocusTemplate.Weekend -> R.string.settings_focus_template_weekend
        FocusTemplate.Blank -> R.string.settings_focus_template_blank
    }

@StringRes
internal fun FocusTemplate.summary(): Int =
    when (this) {
        FocusTemplate.WorkHours -> R.string.settings_focus_template_work_summary
        FocusTemplate.Sleep -> R.string.settings_focus_template_sleep_summary
        FocusTemplate.Weekend -> R.string.settings_focus_template_weekend_summary
        FocusTemplate.Blank -> R.string.settings_focus_template_blank_summary
    }

/** The template's symbol; none for a profile from scratch. */
internal fun FocusTemplate.icon(): FocusIcon? =
    when (this) {
        FocusTemplate.WorkHours -> FocusIcon.Work
        FocusTemplate.Sleep -> FocusIcon.Sleep
        FocusTemplate.Weekend -> FocusIcon.Weekend
        FocusTemplate.Blank -> null
    }

/** The profile this template fills the editor with, named [name] (its title in the app's language). */
internal fun FocusTemplate.profile(name: String): FocusProfile =
    when (this) {
        FocusTemplate.WorkHours ->
            profile(
                name,
                FocusIcon.Work,
                FocusSchedule(
                    persistentSetOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY,
                    ),
                    startMinute = 9 * HOUR,
                    endMinute = 18 * HOUR,
                ),
                calls = true,
            )

        FocusTemplate.Sleep ->
            profile(
                name,
                FocusIcon.Sleep,
                FocusSchedule(DayOfWeek.entries.toPersistentSet(), 22 * HOUR + 30, 7 * HOUR),
                calls = true,
            )

        FocusTemplate.Weekend ->
            profile(
                name,
                FocusIcon.Weekend,
                FocusSchedule(persistentSetOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), 10 * HOUR, 20 * HOUR),
                calls = false,
            )

        FocusTemplate.Blank -> profile("", FocusIcon.Work, schedule = null, calls = true)
    }

private fun profile(
    name: String,
    icon: FocusIcon,
    schedule: FocusSchedule?,
    calls: Boolean,
) = FocusProfile(
    id = 0,
    name = name,
    icon = icon,
    allowedFolderIds = persistentListOf(),
    allowedChatIds = persistentListOf(),
    schedule = schedule,
    allowsCallsFromContacts = calls,
)
