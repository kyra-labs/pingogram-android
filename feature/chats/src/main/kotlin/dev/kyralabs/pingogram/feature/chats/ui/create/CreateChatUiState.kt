package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MessageAutoDelete
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.model.UsernameProblem
import kotlinx.collections.immutable.ImmutableList

// The states, events and effects of creating a group or a channel (REQUIREMENTS.md FR-P4), NewGroupViewModel and
// NewChannelViewModel.

/** The photo picked for a new chat: none, one being copied, or the copy at [Ready.path], which the chat will get. */
@Immutable
sealed interface ChatPhoto {
    data object None : ChatPhoto

    data object Preparing : ChatPhoto

    data class Ready(
        val path: String,
    ) : ChatPhoto {
        override fun toString() = "Ready(…)"
    }
}

/**
 * While Telegram asks to wait (`FLOOD_WAIT`): [seconds] left of [totalSeconds].
 */
@Immutable
data class FloodWait(
    val seconds: Long,
    val totalSeconds: Long,
)

/**
 * The people Telegram did not add to the new chat [chatId], usually for their privacy settings: their [names] where
 * known, [count] in all. They can join through an invite link, which the dialog offers to share; [isSharing] while the
 * link is on its way.
 */
@Immutable
data class NotAddedDialog(
    val chatId: Long,
    val count: Int,
    val names: ImmutableList<String>,
    val isSharing: Boolean = false,
)

/**
 * The last step of creating a group: its [title] as typed (required), its [photo], how long its messages stay
 * ([autoDelete]), how many people will be added ([memberCount]), whether the title is missing or refused
 * ([titleError]), the creation on its way ([isCreating]) or a [floodWait], and the people Telegram did not add
 * ([notAdded]) once it exists.
 */
@Immutable
data class NewGroupDetailsUiState(
    val title: String = "",
    val photo: ChatPhoto = ChatPhoto.None,
    val autoDelete: MessageAutoDelete = MessageAutoDelete.Off,
    val memberCount: Int = 0,
    val titleError: Boolean = false,
    val isCreating: Boolean = false,
    val floodWait: FloodWait? = null,
    val notAdded: NotAddedDialog? = null,
) {
    /** Whether "Create" may be tapped: nothing is on its way or counting down. A missing title shows at its field. */
    val canCreate: Boolean get() = !isCreating && floodWait == null && photo != ChatPhoto.Preparing

    override fun toString() =
        "NewGroupDetailsUiState(photo=$photo, autoDelete=$autoDelete, memberCount=$memberCount, " +
            "isCreating=$isCreating, notAdded=${notAdded != null})"
}

/**
 * What the user does on the details of a new group or channel, sent to [NewGroupViewModel.onEvent] or
 * [NewChannelViewModel.onDetailsEvent]. Only a group has an auto-delete time, only a channel a description.
 */
sealed interface ChatDetailsEvent {
    data class TitleChanged(
        val value: String,
    ) : ChatDetailsEvent

    data class DescriptionChanged(
        val value: String,
    ) : ChatDetailsEvent

    data class AutoDeleteChanged(
        val autoDelete: MessageAutoDelete,
    ) : ChatDetailsEvent

    /** The photo picker returned [uri]. */
    data class PhotoPicked(
        val uri: String,
    ) : ChatDetailsEvent

    data object RemovePhoto : ChatDetailsEvent

    /** "Create". */
    data object Create : ChatDetailsEvent
}

/**
 * The first step of creating a channel: its [title] (required) and [description] as typed, its [photo], whether the
 * title is missing or refused ([titleError]), and the creation on its way ([isCreating]) or a [floodWait].
 */
@Immutable
data class NewChannelDetailsUiState(
    val title: String = "",
    val description: String = "",
    val photo: ChatPhoto = ChatPhoto.None,
    val titleError: Boolean = false,
    val isCreating: Boolean = false,
    val floodWait: FloodWait? = null,
) {
    /** Whether "Next" may be tapped: nothing is on its way or counting down. A missing title shows at its field. */
    val canCreate: Boolean get() = !isCreating && floodWait == null && photo != ChatPhoto.Preparing

    override fun toString() = "NewChannelDetailsUiState(photo=$photo, isCreating=$isCreating, floodWait=$floodWait)"
}

/** What Telegram, or the form, says about the username typed for a public channel. */
@Immutable
sealed interface UsernameStatus {
    /** Nothing typed yet. */
    data object Empty : UsernameStatus

    /** It cannot be a username, found without asking Telegram. */
    data class Problem(
        val problem: UsernameProblem,
    ) : UsernameStatus

    /** Telegram is being asked, after a pause in typing. */
    data object Checking : UsernameStatus

    data class Checked(
        val check: UsernameCheck,
    ) : UsernameStatus

    /** Asking Telegram failed; setting it may still work. */
    data object CheckFailed : UsernameStatus
}

/** A private channel's invite link: on its way, [Ready] with its [Ready.link], or failed with [Error.error]. */
@Immutable
sealed interface InviteLinkState {
    data object Loading : InviteLinkState

    data class Ready(
        val link: String,
    ) : InviteLinkState {
        override fun toString() = "Ready(…)"
    }

    data class Error(
        val error: DomainError,
    ) : InviteLinkState
}

/**
 * The second step of creating a channel, which exists by then: public ([isPublic]) with the [username] as typed and
 * what is known about it ([usernameStatus]), or private with its invite link ([inviteLink]); the username being set
 * ([isSaving]) or a [floodWait].
 */
@Immutable
data class NewChannelTypeUiState(
    val isPublic: Boolean = false,
    val username: String = "",
    val usernameStatus: UsernameStatus = UsernameStatus.Empty,
    val inviteLink: InviteLinkState = InviteLinkState.Loading,
    val isSaving: Boolean = false,
    val floodWait: FloodWait? = null,
) {
    /** Whether "Next" may be tapped: a private channel always; a public one once Telegram said its username is free. */
    val canContinue: Boolean
        get() {
            val usernameReady =
                usernameStatus == UsernameStatus.Checked(UsernameCheck.Available) ||
                    usernameStatus == UsernameStatus.CheckFailed
            return !isSaving && floodWait == null && (!isPublic || usernameReady)
        }

    override fun toString() =
        "NewChannelTypeUiState(isPublic=$isPublic, usernameStatus=$usernameStatus, inviteLink=$inviteLink, " +
            "isSaving=$isSaving)"
}

/** What the user does on the second step of creating a channel, sent to [NewChannelViewModel.onTypeEvent]. */
sealed interface NewChannelTypeEvent {
    data class TypeChanged(
        val isPublic: Boolean,
    ) : NewChannelTypeEvent

    data class UsernameChanged(
        val value: String,
    ) : NewChannelTypeEvent

    /** "Try again" after the invite link could not be read. */
    data object RetryLink : NewChannelTypeEvent

    /** "Next". */
    data object Continue : NewChannelTypeEvent
}

/**
 * The last step of creating a channel: whom to add, and the adding on its way ([isAdding]) or a [floodWait], then the
 * people Telegram did not add ([notAdded]).
 */
@Immutable
data class NewChannelMembersUiState(
    val isAdding: Boolean = false,
    val floodWait: FloodWait? = null,
    val notAdded: NotAddedDialog? = null,
)

/** What the user does in the dialog about the people Telegram did not add. */
sealed interface NotAddedEvent {
    /** "Share invite link". */
    data object ShareLink : NotAddedEvent

    /** "OK", or the dialog dismissed: on to the new chat. */
    data object Dismiss : NotAddedEvent
}

/** One-off effects of creating a group or a channel, each handled once by the step that shows. */
sealed interface CreateChatEffect {
    /** On to the next step. */
    data object Next : CreateChatEffect

    /** Done: opens the new chat [chatId], in the flow's place. */
    data class OpenChat(
        val chatId: Long,
    ) : CreateChatEffect

    /** Opens Android's share sheet with the invite [link]. */
    data class ShareLink(
        val link: String,
    ) : CreateChatEffect {
        override fun toString() = "ShareLink(…)"
    }

    /** As many people are picked as can be added now: a snackbar says so. */
    data object TooManyMembers : CreateChatEffect

    /** Something failed with [error] that no field shows: a snackbar says so. */
    data class ShowError(
        val error: DomainError,
    ) : CreateChatEffect
}
