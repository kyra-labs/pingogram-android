package dev.kyralabs.pingogram.feature.contacts.ui.birthdays

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/** The Birthdays page (`REQUIREMENTS.md` FR-X18): the contacts Telegram names as having a birthday these days. */
@Immutable
sealed interface BirthdaysUiState {
    /** The contacts' names are on their way. */
    data object Loading : BirthdaysUiState

    /** No contact has a birthday these days. */
    data object Empty : BirthdaysUiState

    /**
     * The birthdays of [today], then the [upcoming] ones, then those just [past], each nearest first; any of them may
     * be empty.
     */
    data class Content(
        val today: ImmutableList<BirthdayItem>,
        val upcoming: ImmutableList<BirthdayItem>,
        val past: ImmutableList<BirthdayItem>,
    ) : BirthdaysUiState {
        override fun toString() = "Content(today=${today.size}, upcoming=${upcoming.size}, past=${past.size})"
    }
}

/**
 * A contact's birthday: [name] and [photo], the [date] it falls on ("24 November"), the age they turn ([turns], null
 * unless they share their year), and whether the user set a reminder for it ([remind]).
 */
@Immutable
data class BirthdayItem(
    val userId: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
    val date: String,
    val turns: Int?,
    val remind: Boolean,
) {
    override fun toString() = "BirthdayItem(userId=$userId, remind=$remind)"
}

/** What the user does on the Birthdays page, sent to [BirthdaysViewModel.onEvent]. */
sealed interface BirthdaysEvent {
    /** A row or its "Send a message" was tapped: the chat with user [userId] opens. */
    data class Open(
        val userId: Long,
    ) : BirthdaysEvent

    /** "Remind me" was turned [on] or off for user [userId]'s birthday. */
    data class SetReminder(
        val userId: Long,
        val on: Boolean,
    ) : BirthdaysEvent

    /** "Add to calendar" for user [userId]'s birthday. */
    data class AddToCalendar(
        val userId: Long,
    ) : BirthdaysEvent
}

/** One-off effects of [BirthdaysViewModel], handled by the page once each. */
sealed interface BirthdaysEffect {
    /** Opens the chat with user [userId]. */
    data class OpenChat(
        val userId: Long,
    ) : BirthdaysEffect

    /** Hands the calendar app a yearly all-day event for [name]'s birthday, first on [day] of [month] of [year]. */
    data class AddToCalendar(
        val name: UiText,
        val year: Int,
        val month: Int,
        val day: Int,
    ) : BirthdaysEffect

    /** A reminder could not be saved: says so with [error]. */
    data class ReminderFailed(
        val error: DomainError,
    ) : BirthdaysEffect
}
