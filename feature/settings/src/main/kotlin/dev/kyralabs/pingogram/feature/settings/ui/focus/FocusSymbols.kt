package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.annotation.StringRes
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.feature.settings.R

// The symbols and shapes are core:ui's (`FocusBadge`); what screen readers call them is the editor's.

/** What screen readers call this profile icon in the editor's icon choice. */
@StringRes
internal fun FocusIcon.label(): Int =
    when (this) {
        FocusIcon.Work -> R.string.settings_focus_icon_work
        FocusIcon.Sleep -> R.string.settings_focus_icon_sleep
        FocusIcon.Weekend -> R.string.settings_focus_icon_weekend
        FocusIcon.Reading -> R.string.settings_focus_icon_reading
        FocusIcon.Fitness -> R.string.settings_focus_icon_fitness
        FocusIcon.Driving -> R.string.settings_focus_icon_driving
    }
