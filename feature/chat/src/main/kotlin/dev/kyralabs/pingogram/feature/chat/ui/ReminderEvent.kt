package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.feature.chat.ui.reminders.ReminderOption

/**
 * What the reminder sheet takes (`docs/DESIGN_SYSTEM.md` §6 "Message reminders", the owner's board 29), handled by
 * `reminders.ReminderSheetViewModel` rather than the conversation's view model.
 */
sealed interface ReminderEvent : ChatEvent {
    /** "Remind me" on message [messageId]: the sheet opens. */
    data class Open(
        val messageId: Long,
    ) : ReminderEvent

    /** A choice of "When". */
    data class Choose(
        val option: ReminderOption,
    ) : ReminderEvent

    /** The date and time picked for "Pick a date and time", in Unix seconds. */
    data class PickCustom(
        val at: Long,
    ) : ReminderEvent

    /** "Set reminder": the [note] typed and what the sheet shows of the message, kept with the reminder. */
    data class Save(
        val note: String,
        val sender: String,
        val text: String,
    ) : ReminderEvent

    /** "Remove reminder", for a message that has one. */
    data object Remove : ReminderEvent

    /** "Undo" in the snackbar after setting one. */
    data object Undo : ReminderEvent

    /** "Cancel", the scrim, dragging or back. */
    data object Dismiss : ReminderEvent
}
