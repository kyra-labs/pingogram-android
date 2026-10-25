package dev.kyralabs.pingogram.feature.chat.ui.scheduled

import dev.kyralabs.pingogram.core.common.result.DomainError

/** What the user does on the list of scheduled messages (`REQUIREMENTS.md` FR-C5), for [ScheduledMessagesViewModel]. */
sealed interface ScheduledEvent {
    /** Scheduled message [messageId] was tapped or long-pressed, or a screen reader asked for its actions. */
    data class ShowActions(
        val messageId: Long,
    ) : ScheduledEvent

    /** The actions sheet was dismissed without an action. */
    data object DismissActions : ScheduledEvent

    /** [action] was chosen for scheduled message [messageId], in its actions sheet or as a screen reader's action. */
    data class Act(
        val messageId: Long,
        val action: ScheduledAction,
    ) : ScheduledEvent

    /** The deletion asked for was confirmed. */
    data object ConfirmDelete : ScheduledEvent

    data object DismissDelete : ScheduledEvent

    /** The pickers gave scheduled message [messageId] its new time, [sendDate] (Unix time, seconds). */
    data class Reschedule(
        val messageId: Long,
        val sendDate: Long,
    ) : ScheduledEvent

    data object DismissReschedule : ScheduledEvent

    /** The composer's check saves the edit in progress. */
    data object SaveEdit : ScheduledEvent

    /** The composer's edit bar was closed: the edit ends without being saved. */
    data object CancelEdit : ScheduledEvent

    /** The hidden spoilers of scheduled message [messageId] were tapped and show. */
    data class RevealSpoilers(
        val messageId: Long,
    ) : ScheduledEvent

    /** "Try again" after the list failed to load. */
    data object Retry : ScheduledEvent
}

/** One-off effects of [ScheduledMessagesViewModel], handled by the screen once each. */
sealed interface ScheduledEffect {
    /** An action failed: a snackbar says why (a flood wait's time, no network…), none for [DomainError.Silent]. */
    data class ShowError(
        val error: DomainError,
    ) : ScheduledEffect

    /** Put [text] on the clipboard, as plain text; a snackbar before Android 13, which confirms copies itself. */
    data class CopyText(
        val text: String,
    ) : ScheduledEffect {
        override fun toString() = "CopyText(…)"
    }

    /** A scheduled message was sent now: a snackbar says so. */
    data object Sent : ScheduledEffect

    /** A scheduled message was deleted: a snackbar says so. */
    data object Deleted : ScheduledEffect

    /** A scheduled message goes out at [sendDate] (Unix time, seconds) now: a snackbar says when. */
    data class Rescheduled(
        val sendDate: Long,
    ) : ScheduledEffect

    /** The message an action was chosen for was sent or deleted meanwhile: a snackbar says so. */
    data object MessageGone : ScheduledEffect

    /** An edit starts: the composer's field takes the focus. */
    data object FocusComposer : ScheduledEffect
}
