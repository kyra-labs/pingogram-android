package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.feature.chat.ui.reminders.ReminderChoice

/**
 * What the reminder sheet takes (the owner's board 29). [Open] comes from a message's actions, as a [ChatEvent] the
 * route hands to `reminders.ReminderSheetViewModel` rather than the conversation's view model.
 */
sealed interface ReminderEvent : ChatEvent {
    /** "Remind me" on message [messageId]: the sheet opens. */
    data class Open(
        val messageId: Long,
    ) : ReminderEvent

    /** One of the quick choices. */
    data class Choose(
        val choice: ReminderChoice,
    ) : ReminderEvent

    /** "Pick a date and time": the moment picked, in epoch seconds. */
    data class Pick(
        val epochSeconds: Long,
    ) : ReminderEvent

    /** "Set reminder" with [note]. */
    data class Set(
        val note: String,
    ) : ReminderEvent {
        override fun toString() = "Set(…)"
    }

    /** "Delete reminder", on a message that has one. */
    data object Delete : ReminderEvent

    /** "Undo" in the snackbar after setting: the reminder goes back to what it was. */
    data object Undo : ReminderEvent

    /** "Cancel", the scrim, dragging or back. */
    data object Dismiss : ReminderEvent
}
