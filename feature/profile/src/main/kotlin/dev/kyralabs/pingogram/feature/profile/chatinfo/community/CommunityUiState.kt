package dev.kyralabs.pingogram.feature.profile.chatinfo.community

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import kotlinx.collections.immutable.ImmutableList

/** What the page of a community shows (`docs/DESIGN_SYSTEM.md` §6 "Community page", the owner's board 19a). */
@Immutable
sealed interface CommunityUiState {
    data object Loading : CommunityUiState

    /** The community could not be read, because of [error]; [CommunityEvent.Retry]. */
    data class Error(
        val error: DomainError,
    ) : CommunityUiState

    /**
     * The community: its [header], its [chats] in Telegram's order, the [dialog] open, and whether a change is on its
     * way ([isBusy]).
     */
    data class Content(
        val header: CommunityHeader,
        val chats: ImmutableList<CommunityChatItem>,
        val dialog: CommunityDialog? = null,
        val isBusy: Boolean = false,
    ) : CommunityUiState
}

/**
 * The community's head: its [name] (empty while TDLib has not announced it) and photo, what the user is in it
 * ([role], null for a member), and what they may change.
 */
@Immutable
data class CommunityHeader(
    val id: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val role: CommunityRoleLabel? = null,
    val canRename: Boolean = false,
    val canDelete: Boolean = false,
) {
    val hasOptions: Boolean get() = canRename || canDelete
}

/** What the header says the user is in the community. */
enum class CommunityRoleLabel { Owner, Administrator }

/**
 * A chat of the community, as its row shows it.
 *
 * @property memberCount members or subscribers; null where unknown (a bot, a chat TDLib has not announced).
 * @property isHidden only the community's administrators see it.
 */
@Immutable
data class CommunityChatItem(
    val chatId: Long,
    val title: String,
    val photo: ProfilePhoto?,
    val shape: AvatarShape,
    val kind: CommunityChatKind,
    val memberCount: Int? = null,
    val isHidden: Boolean = false,
    val membership: Membership = Membership.None,
) {
    override fun toString() = "CommunityChatItem(chatId=$chatId, kind=$kind, membership=$membership, …)"
}

/** What kind of chat a community's chat is. */
enum class CommunityChatKind { Group, Channel, Bot, Chat }

/** Whether the user is in a community's chat, and so what its row's button does. */
enum class Membership {
    /** The user is in it: a "Joined" label. */
    Joined,

    /** The user may join it: a "Join" button. */
    CanJoin,

    /** "Join" was tapped and Telegram has not answered. */
    Joining,

    /** Nothing to join (a bot, a private chat). */
    None,
}

/** The dialog the page shows. */
@Immutable
sealed interface CommunityDialog {
    /** Renaming the community, the name as typed. */
    data class Rename(
        val name: String,
    ) : CommunityDialog {
        override fun toString() = "Rename(…)"
    }

    /** Deleting the community, after a question. */
    data object Delete : CommunityDialog
}

/** What the user does on the page, handled by [CommunityViewModel.onEvent]. */
sealed interface CommunityEvent {
    data object Retry : CommunityEvent

    data class OpenChat(
        val chatId: Long,
    ) : CommunityEvent

    data class Join(
        val chatId: Long,
    ) : CommunityEvent

    data object Rename : CommunityEvent

    data class NameChanged(
        val name: String,
    ) : CommunityEvent {
        override fun toString() = "NameChanged(…)"
    }

    data object Delete : CommunityEvent

    /** Rename or delete, as the dialog shown asks. */
    data object Confirm : CommunityEvent

    data object DismissDialog : CommunityEvent
}

/** One-off side effects of [CommunityViewModel], handled once by its route. */
sealed interface CommunityEffect {
    data class OpenChat(
        val chatId: Long,
    ) : CommunityEffect

    /** A snackbar says [text]. */
    data class Notice(
        @param:StringRes val text: Int,
    ) : CommunityEffect

    /** A change failed: a snackbar shows the error's text, none for [DomainError.Silent]. */
    data class ShowError(
        val error: DomainError,
    ) : CommunityEffect

    /** The community was deleted: the page closes. */
    data object Deleted : CommunityEffect
}
