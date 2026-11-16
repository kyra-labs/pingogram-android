package dev.kyralabs.pingogram.feature.chat.ui.reminders

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/**
 * The reminder sheet of message [messageId] (the owner's board 29).
 *
 * @property sender who sent it, as the chat list names them; null where unknown.
 * @property text what it says, as the chat list previews it; null until read.
 * @property options the choices offered now, in their order, each with the time it rings.
 * @property chosen the choice picked; null before "Pick a date and time" has a moment.
 * @property note the note the message's reminder has, which the field starts with.
 * @property isExisting the message has a reminder already, which "Set reminder" replaces and "Delete reminder" removes.
 * @property isSaving the reminder is being written.
 */
@Immutable
data class ReminderSheetUiState(
    val messageId: Long,
    val sender: UiText?,
    val text: UiText?,
    val options: ImmutableList<ReminderOption>,
    val chosen: ReminderChoice?,
    val note: String,
    val isExisting: Boolean,
    val isSaving: Boolean = false,
) {
    override fun toString() = "ReminderSheetUiState(messageId=$messageId, chosen=$chosen, isExisting=$isExisting)"
}

/** A choice of the sheet and the time it rings ("Sun, 9:00 AM"); none for "Pick a date and time" until picked. */
@Immutable
data class ReminderOption(
    val choice: ReminderChoice,
    val time: UiText?,
)

/** What the reminder sheet tells once it closed. */
sealed interface ReminderEffect {
    /** Set to ring at [time] ("Sun, 9:00 AM"); the snackbar offers "Undo". */
    data class Set(
        val time: UiText,
    ) : ReminderEffect

    data object Deleted : ReminderEffect

    data class ShowError(
        val error: DomainError,
    ) : ReminderEffect
}
