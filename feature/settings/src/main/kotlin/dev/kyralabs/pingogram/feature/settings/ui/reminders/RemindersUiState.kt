package dev.kyralabs.pingogram.feature.settings.ui.reminders

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/** The Reminders page (the owner's board 29e, 29f): the reminders kept on this phone, upcoming and done. */
@Immutable
sealed interface RemindersUiState {
    data object Loading : RemindersUiState

    /** No reminder at all: how to set one. */
    data object Empty : RemindersUiState

    /** The reminders not done ([upcoming], past ones first) and those done, in the [sort] chosen. */
    data class Content(
        val upcoming: ImmutableList<ReminderRow>,
        val done: ImmutableList<ReminderRow>,
        val sort: ReminderSort,
    ) : RemindersUiState
}

/** How the page lists the reminders: by when they ring, or by chat (then by when). */
enum class ReminderSort {
    ByTime,
    ByChat,
}

/**
 * A reminder on the page.
 *
 * @property chatTitle the chat's name; "Chat" while TDLib has not told it.
 * @property time when it rings ("Sun, 9:00 AM"), or for one done the day it was for ("Thu").
 * @property preview the message as the chat list previews it, after its sender in a group; null until read, or where
 *   TDLib no longer has it.
 * @property note what the user wrote with it; empty for none.
 * @property isOverdue its time went by without it being marked done.
 */
@Immutable
data class ReminderRow(
    val chatId: Long,
    val messageId: Long,
    val chatTitle: String,
    val photo: ProfilePhoto?,
    val avatarShape: AvatarShape,
    val time: UiText,
    val preview: UiText?,
    val note: String,
    val isDone: Boolean,
    val isOverdue: Boolean = false,
) {
    val key: String get() = "$chatId:$messageId"

    override fun toString() = "ReminderRow(chatId=$chatId, messageId=$messageId, isDone=$isDone)"
}

/** What the user does on the page. */
sealed interface RemindersEvent {
    /** A reminder was tapped: its chat opens. */
    data class Open(
        val chatId: Long,
    ) : RemindersEvent

    /** "Mark done" (a swipe toward the end, or the custom action), or "Mark not done" on one done. */
    data class ToggleDone(
        val chatId: Long,
        val messageId: Long,
    ) : RemindersEvent

    /** "Delete" (a swipe toward the start, or the custom action); the snackbar offers "Undo". */
    data class Delete(
        val chatId: Long,
        val messageId: Long,
    ) : RemindersEvent

    /** "Undo" in the snackbar after deleting. */
    data object UndoDelete : RemindersEvent

    data class Sort(
        val sort: ReminderSort,
    ) : RemindersEvent
}

/** The page's one-off effects. */
sealed interface RemindersEffect {
    data class OpenChat(
        val chatId: Long,
    ) : RemindersEffect

    /** A reminder was deleted; the snackbar offers "Undo". */
    data object Deleted : RemindersEffect

    data class ShowError(
        val error: DomainError,
    ) : RemindersEffect
}
