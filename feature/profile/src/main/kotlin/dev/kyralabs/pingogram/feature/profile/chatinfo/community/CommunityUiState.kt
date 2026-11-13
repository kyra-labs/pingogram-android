package dev.kyralabs.pingogram.feature.profile.chatinfo.community

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** What the page of a community shows (`docs/DESIGN_SYSTEM.md` §6 "Community page", the owner's board 19a). */
@Immutable
sealed interface CommunityUiState {
    data object Loading : CommunityUiState

    /** The community could not be read, because of [error]; [CommunityEvent.Retry]. */
    data class Error(
        val error: DomainError,
    ) : CommunityUiState

    /**
     * The community: its [header], its [chats] in Telegram's order, the [requests] to add a chat its administrators
     * answer, the [dialog] open, the sheet adding chats ([addChats], null while closed), and whether a change is on
     * its way ([isBusy]).
     */
    data class Content(
        val header: CommunityHeader,
        val chats: ImmutableList<CommunityChatItem>,
        val requests: ImmutableList<AddChatRequestItem> = persistentListOf(),
        val dialog: CommunityDialog? = null,
        val addChats: AddChatsSheet? = null,
        val isBusy: Boolean = false,
    ) : CommunityUiState
}

/**
 * The community's head: its [name] (empty while TDLib has not announced it) and photo, what the user is in it
 * ([role], null for a member), and what they may change.
 *
 * @property isJoined the user is in the community: "Add chats" or "Suggest a chat", "Mute", and the chat list's entry.
 * @property canEditChatList the user adds and removes chats themselves; the others suggest them.
 * @property isMuted the user muted the community's chats.
 * @property areChatsCollapsed the chat list shows the community's chats as one entry.
 */
@Immutable
data class CommunityHeader(
    val id: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val role: CommunityRoleLabel? = null,
    val canRename: Boolean = false,
    val canDelete: Boolean = false,
    val isJoined: Boolean = false,
    val canEditChatList: Boolean = false,
    val isMuted: Boolean = false,
    val areChatsCollapsed: Boolean = false,
) {
    val hasOptions: Boolean get() = canRename || canDelete || isJoined
}

/**
 * A request to add [chat] to the community, which [senderName] sent, waiting for its administrators; [isBusy] while
 * Telegram takes the answer.
 */
@Immutable
data class AddChatRequestItem(
    val chat: CommunityChatItem,
    val senderName: UiText,
    val isBusy: Boolean = false,
) {
    override fun toString() = "AddChatRequestItem(chat=$chat, …)"
}

/**
 * The sheet that adds chats to the community, or suggests them to its administrators: the user's chats that may be
 * added ([chats], null while loading, [error] where they could not be read), the ones [picked], whether they are to be
 * visible to administrators only ([isHidden]), and whether they are on their way ([isSending]).
 */
@Immutable
data class AddChatsSheet(
    val chats: ImmutableList<CommunityChatItem>? = null,
    val error: DomainError? = null,
    val picked: ImmutableSet<Long> = persistentSetOf(),
    val isHidden: Boolean = false,
    val isSending: Boolean = false,
)

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

    /** Taking chat [chatId], named [title], out of the community, after a question. */
    data class RemoveChat(
        val chatId: Long,
        val title: String,
    ) : CommunityDialog {
        override fun toString() = "RemoveChat(chatId=$chatId, …)"
    }
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

    /** Rename, delete or remove a chat, as the dialog shown asks. */
    data object Confirm : CommunityEvent

    /** Mutes the community's chats, or unmutes them. */
    data object ToggleMute : CommunityEvent

    /** Shows the community's chats as one entry in the chat list, or each on its own. */
    data object ToggleCollapsed : CommunityEvent

    /** Asks before taking chat [chatId] out of the community. */
    data class RemoveChat(
        val chatId: Long,
    ) : CommunityEvent

    /** Adds chat [chatId] to the community as its sender asked ([approve]), or declines. */
    data class AnswerRequest(
        val chatId: Long,
        val approve: Boolean,
    ) : CommunityEvent

    /** Opens the sheet that adds chats, or suggests them. */
    data object AddChats : CommunityEvent

    /** Picks chat [chatId] in the sheet, or unpicks it. */
    data class PickChat(
        val chatId: Long,
    ) : CommunityEvent

    /** The sheet's "Visible to admins only". */
    data class HiddenChanged(
        val isHidden: Boolean,
    ) : CommunityEvent

    /** Adds or suggests the chats picked. */
    data object SendChats : CommunityEvent

    /** Reads the sheet's chats again after a failure. */
    data object RetryAddChats : CommunityEvent

    data object DismissAddChats : CommunityEvent

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

    /** A snackbar says plural [text] for [count]. */
    data class CountNotice(
        @param:PluralsRes val text: Int,
        val count: Int,
    ) : CommunityEffect

    /** A change failed: a snackbar shows the error's text, none for [DomainError.Silent]. */
    data class ShowError(
        val error: DomainError,
    ) : CommunityEffect

    /** The community was deleted: the page closes. */
    data object Deleted : CommunityEffect
}
