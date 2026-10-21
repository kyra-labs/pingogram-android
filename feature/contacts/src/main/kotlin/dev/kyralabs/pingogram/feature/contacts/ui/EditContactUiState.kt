package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto

/** The Edit contact screen (`REQUIREMENTS.md` FR-P1): a Telegram user saved, or to be saved, as a contact. */
@Immutable
sealed interface EditContactUiState {
    /** The user is on their way. */
    data object Loading : EditContactUiState

    /** Reading the user failed with [error]: shown with a retry ([EditContactEvent.Retry]). */
    data class Error(
        val error: DomainError,
    ) : EditContactUiState

    /**
     * User [userId] with their [photo], [name] and [phoneNumber] (digits, empty where their privacy settings hide it)
     * as the screen's header; [isContact] whether they are a contact already (Edit, or Add to contacts). The fields as
     * typed: [firstName], [lastName], and [sharePhoneNumber] where saving can share the user's own number with them
     * ([canSharePhoneNumber]). [fieldError], [isWorking] and the `FLOOD_WAIT` countdown ([floodWaitSeconds] from
     * [floodWaitTotalSeconds]) as on Add contact; [showsDeleteDialog] while deleting is asked about.
     */
    data class Content(
        val userId: Long,
        val name: String,
        val photo: ProfilePhoto?,
        val phoneNumber: String,
        val isContact: Boolean,
        val canSharePhoneNumber: Boolean,
        val firstName: String,
        val lastName: String,
        val sharePhoneNumber: Boolean,
        val fieldError: ContactFieldError? = null,
        val isWorking: Boolean = false,
        val floodWaitSeconds: Long? = null,
        val floodWaitTotalSeconds: Long = 0,
        val showsDeleteDialog: Boolean = false,
    ) : EditContactUiState {
        /** Whether "Save" may be tapped: a first name, and nothing on its way or counting down. */
        val canSave: Boolean get() = firstName.isNotBlank() && !isWorking && floodWaitSeconds == null

        override fun toString() = "Content(userId=$userId, isContact=$isContact, isWorking=$isWorking)"
    }
}

/** What the user does on the Edit contact screen, sent to [EditContactViewModel.onEvent]. */
sealed interface EditContactEvent {
    data class FirstNameChanged(
        val value: String,
    ) : EditContactEvent

    data class LastNameChanged(
        val value: String,
    ) : EditContactEvent

    data class SharePhoneNumberChanged(
        val share: Boolean,
    ) : EditContactEvent

    /** "Save" (or "Add"). */
    data object Save : EditContactEvent

    /** "Delete contact": asks first. */
    data object Delete : EditContactEvent

    /** The deletion was confirmed. */
    data object ConfirmDelete : EditContactEvent

    /** The deletion was not confirmed. */
    data object DismissDialog : EditContactEvent

    /** "Try again" after reading the user failed. */
    data object Retry : EditContactEvent
}

/** One-off effects of [EditContactViewModel], handled by the screen once each. */
sealed interface EditContactEffect {
    /** The contact was saved or deleted: the screen closes. */
    data object Done : EditContactEffect

    /** Something failed with [error] that no field shows: a snackbar says so. */
    data class ShowError(
        val error: DomainError,
    ) : EditContactEffect
}
