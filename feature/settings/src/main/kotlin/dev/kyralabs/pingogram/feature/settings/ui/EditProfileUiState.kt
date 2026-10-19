package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfileLimits
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.model.UsernameProblem
import dev.kyralabs.pingogram.core.domain.model.characterCount

/** What the Edit profile page shows (`REQUIREMENTS.md` FR-S1, Account). */
@Immutable
sealed interface EditProfileUiState {
    /** The profile is loading; TDLib answers from memory, so briefly. */
    data object Loading : EditProfileUiState

    /** The profile could not be loaded, and why; the page offers to try again. */
    data class Error(
        val error: DomainError,
    ) : EditProfileUiState

    /**
     * The profile, as typed so far.
     *
     * @property account the user's photo and name for the avatar; null until TDLib has announced the user.
     * @property photoTask what is being done with the photo.
     * @property saved the profile as TDLib has it, against which [fields] count as changes.
     * @property fields the profile as the user typed it.
     * @property bioLengthMax the longest bio Telegram accepts from the user, in characters.
     * @property usernameStatus what is known about the username typed, shown below its field.
     * @property isSaving the changes are being saved; the fields wait meanwhile.
     * @property dialog the open confirmation, if any.
     */
    data class Content(
        val account: ProfileAccount?,
        val saved: ProfileFields,
        val fields: ProfileFields = saved,
        val photoTask: PhotoTask = PhotoTask.None,
        val bioLengthMax: Int = ProfileLimits.BIO_LENGTH_MAX_DEFAULT,
        val usernameStatus: UsernameStatus = UsernameStatus.Unchanged,
        val isSaving: Boolean = false,
        val dialog: EditProfileDialog? = null,
    ) : EditProfileUiState {
        /** Why the first name cannot be saved as typed; null if it can. */
        val firstNameProblem: FieldProblem?
            get() =
                when {
                    fields.firstName.isBlank() -> FieldProblem.Required
                    fields.firstName.characterCount() > ProfileLimits.NAME_LENGTH_MAX -> FieldProblem.TooLong
                    else -> null
                }

        /** Why the last name cannot be saved as typed; null if it can. */
        val lastNameProblem: FieldProblem?
            get() = FieldProblem.TooLong.takeIf { fields.lastName.characterCount() > ProfileLimits.NAME_LENGTH_MAX }

        /** Why the bio cannot be saved as typed; null if it can. */
        val bioProblem: FieldProblem?
            get() = FieldProblem.TooLong.takeIf { fields.bio.characterCount() > bioLengthMax }

        /** Something differs from what TDLib has, as TDLib would save it (names and bio trimmed). */
        val hasChanges: Boolean
            get() = nameChanged || usernameChanged || bioChanged

        val nameChanged: Boolean
            get() = fields.firstName.trim() != saved.firstName || fields.lastName.trim() != saved.lastName

        val usernameChanged: Boolean
            get() = fields.username != saved.username

        val bioChanged: Boolean
            get() = fields.bio.trim() != saved.bio

        /** Save is enabled: something changed, everything typed is valid, and nothing is being saved. */
        val canSave: Boolean
            get() =
                !isSaving &&
                    hasChanges &&
                    firstNameProblem == null &&
                    lastNameProblem == null &&
                    bioProblem == null &&
                    usernameStatus.allowsSaving
    }
}

/**
 * The user's names, username and bio, as TDLib has them or as typed. `toString()` leaves them out, so a state can be
 * logged.
 *
 * @property username without `@`; empty for none.
 */
@Immutable
data class ProfileFields(
    val firstName: String,
    val lastName: String,
    val username: String,
    val bio: String,
) {
    override fun toString() = "ProfileFields(…)"
}

/**
 * The avatar at the top of the page: the user's [photo] (null for none), or the placeholder of their [userId] with the
 * initials of their [name] as TDLib has it.
 */
@Immutable
data class ProfileAccount(
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
) {
    override fun toString() = "ProfileAccount(userId=$userId, …)"
}

/** What is being done with the profile photo; one thing at a time. */
@Immutable
sealed interface PhotoTask {
    data object None : PhotoTask

    /** The picked image is being copied and scaled down. */
    data object Preparing : PhotoTask

    /** The new photo is uploading: [progress] from 0 to 1, null until TDLib reports some. */
    data class Uploading(
        val progress: Float?,
    ) : PhotoTask

    data object Removing : PhotoTask
}

/** Why a name or the bio cannot be saved as typed. */
enum class FieldProblem {
    /** Empty, or only spaces (the first name). */
    Required,

    /** Longer than Telegram accepts. */
    TooLong,
}

/** What is known about the username typed, shown below its field. */
@Immutable
sealed interface UsernameStatus {
    /** The username TDLib has; nothing to say. */
    data object Unchanged : UsernameStatus

    /** Emptied: saving removes the username, after a confirmation. */
    data object Removed : UsernameStatus

    /** It cannot be a username, found without asking Telegram. */
    data class Problem(
        val problem: UsernameProblem,
    ) : UsernameStatus

    /** Telegram is asked (after a pause in typing). */
    data object Checking : UsernameStatus

    /** What Telegram said. */
    data class Checked(
        val result: UsernameCheck,
    ) : UsernameStatus

    /** Telegram could not be asked, e.g. offline; saving may still work, and says why if not. */
    data object CheckFailed : UsernameStatus

    /** Saving is allowed with this status. */
    val allowsSaving: Boolean
        get() =
            this == Unchanged ||
                this == Removed ||
                this == CheckFailed ||
                this == Checked(UsernameCheck.Available)
}

/** The confirmations of the page. */
enum class EditProfileDialog {
    /** Leaving with unsaved changes. */
    Discard,

    /** Removing the profile photo. */
    RemovePhoto,

    /** Saving an emptied username, which removes it. */
    RemoveUsername,
}

/** What the user does on the Edit profile page, handled by [EditProfileViewModel.onEvent]. */
sealed interface EditProfileEvent {
    data class FirstNameChanged(
        val value: String,
    ) : EditProfileEvent

    data class LastNameChanged(
        val value: String,
    ) : EditProfileEvent

    /** The username typed, without the `@` the field shows. */
    data class UsernameChanged(
        val value: String,
    ) : EditProfileEvent

    data class BioChanged(
        val value: String,
    ) : EditProfileEvent

    data object Save : EditProfileEvent

    /** The photo picker returned an image: its content URI, as a string. */
    data class PhotoPicked(
        val uri: String,
    ) : EditProfileEvent

    /** "Remove photo" was tapped: ask for confirmation. */
    data object RemovePhoto : EditProfileEvent

    /** The open dialog's confirm button was tapped. */
    data object ConfirmDialog : EditProfileEvent

    data object DismissDialog : EditProfileEvent

    /** Back, from the top bar or the system; asks first if there are unsaved changes. */
    data object Back : EditProfileEvent

    /** Load the profile again after it failed. */
    data object Retry : EditProfileEvent
}

/** One-off side effects of [EditProfileViewModel], handled once by its route. */
sealed interface EditProfileEffect {
    /** A request failed, shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : EditProfileEffect

    /** Everything was saved: leave the page, and say so on Settings. */
    data object Saved : EditProfileEffect

    /** Leave the page: back without changes, or with changes discarded. */
    data object Leave : EditProfileEffect
}
