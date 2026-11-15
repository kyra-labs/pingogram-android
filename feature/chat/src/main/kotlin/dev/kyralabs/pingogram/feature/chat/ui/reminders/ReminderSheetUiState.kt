package dev.kyralabs.pingogram.feature.chat.ui.reminders

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/** When a reminder rings (the owner's board 29b; morning 9:00 and evening 20:00, owner decision 2026-11-15). */
enum class ReminderOption {
    InOneHour,
    ThisEvening,
    TomorrowMorning,
    NextWeek,
    Custom,
}

/** A choice of "When": its [option], when it would ring ([at], Unix seconds; null for a date not picked yet) worded. */
@Immutable
data class ReminderChoice(
    val option: ReminderOption,
    val at: Long?,
    val whenText: UiText?,
)

/**
 * The reminder sheet of message [messageId]: who sent it and what it says, the choices of "When" ("This evening" left
 * out once 8 PM has passed) with the [selected] one, the [note] of an existing reminder ([hasReminder]), and whether it
 * is being saved.
 */
@Immutable
data class ReminderSheetUiState(
    val messageId: Long,
    val sender: UiText?,
    val text: UiText?,
    val choices: ImmutableList<ReminderChoice>,
    val selected: ReminderOption,
    val note: String,
    val hasReminder: Boolean,
    val isSaving: Boolean = false,
) {
    /** When the [selected] choice rings, or null while "Pick a date and time" has none. */
    val at: Long? get() = choices.firstOrNull { it.option == selected }?.at
}

/** What the reminder sheet says once. */
sealed interface ReminderEffect {
    /** "Reminder set for Sun, 9:00 AM", with "Undo". */
    data class Set(
        val whenText: UiText,
    ) : ReminderEffect

    data object Removed : ReminderEffect

    data class ShowError(
        val error: DomainError,
    ) : ReminderEffect
}
