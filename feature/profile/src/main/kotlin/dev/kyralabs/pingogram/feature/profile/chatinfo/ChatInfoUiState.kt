package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.MentionTarget
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.profile.ui.ProfileHeader
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * What the info page of a group or channel shows (`REQUIREMENTS.md` FR-P3, FR-P4), apart from its shared media, which
 * is the profile's `SharedMediaUiState`.
 */
@Immutable
sealed interface ChatInfoUiState {
    data object Loading : ChatInfoUiState

    /** The chat could not be found ([DomainError.NotFound]) or read, because of [error]; [ChatInfoEvent.Retry]. */
    data class Error(
        val error: DomainError,
    ) : ChatInfoUiState

    /**
     * @property header the top bar's photo, title, "Public group · 1,024 members" and badges, as the profile's.
     * @property description the description with its links, mentions and hashtags; null without one.
     * @property link the public link or, for administrators of a private group, its primary invite link; null without.
     * @property members the first members and how many there are; null where the user may not list them.
     */
    data class Content(
        val header: ProfileHeader,
        val kind: GroupKind,
        val description: FormattedText? = null,
        val link: ChatLink? = null,
        val actions: ChatInfoActions = ChatInfoActions(),
        val members: MembersPreview? = null,
        val administration: ChatAdministration? = null,
        val dialog: ChatInfoDialog? = null,
    ) : ChatInfoUiState
}

/**
 * The link of a group or channel: its public `t.me/username` ([isPublic]), or its primary invite link. [url] is the
 * full link that copying, sharing and the QR code use; [text] what the row shows.
 */
@Immutable
data class ChatLink(
    val url: String,
    val text: String,
    val isPublic: Boolean,
) {
    override fun toString() = "ChatLink(isPublic=$isPublic, …)"
}

/**
 * What the user can do with the chat from its info page; an action that does not apply is false (or null).
 *
 * @property isMuted the chat's notifications are off; null until the chat is known.
 * @property canLeave the user is a member and can leave.
 * @property canDelete the user owns the group or channel and can delete it for everyone.
 * @property canClearHistory the history can be cleared for the user: groups, not channels.
 * @property isBusy leaving, deleting or clearing was asked for, and TDLib has not answered yet.
 */
@Immutable
data class ChatInfoActions(
    val isMuted: Boolean? = null,
    val canLeave: Boolean = false,
    val canDelete: Boolean = false,
    val canClearHistory: Boolean = false,
    val isBusy: Boolean = false,
)

/**
 * The first members of a group or channel, and how many it has ([totalCount]); [items] may be fewer than that, and
 * "All members" opens the rest. [isHidden] when the group hides its members from non-administrators.
 */
@Immutable
data class MembersPreview(
    val items: ImmutableList<MemberItem> = persistentListOf(),
    val totalCount: Int = 0,
    val isHidden: Boolean = false,
)

/**
 * The rows administrators get, each where their rights allow it: editing the title and description, the default
 * permissions and slow mode (groups), the administrators, the invite links, and the removed users.
 */
@Immutable
data class ChatAdministration(
    val canEditInfo: Boolean = false,
    val canEditPermissions: Boolean = false,
    val administratorCount: Int? = null,
    val canManageInviteLinks: Boolean = false,
    val bannedCount: Int? = null,
    val restrictedCount: Int? = null,
    val canViewBoosts: Boolean = false,
) {
    val isEmpty: Boolean
        get() =
            !canEditInfo && !canEditPermissions && administratorCount == null && !canManageInviteLinks &&
                bannedCount == null && restrictedCount == null && !canViewBoosts
}

/**
 * A member as their row shows them, on the info page and the members page.
 *
 * @property name the user's full name ("Deleted Account" for a deleted one), or a chat's title.
 * @property status "online", "last seen …", "bot"; null for chats.
 * @property role what the trailing label says: "Owner", "Admin" or the member's tag, "Restricted", "Removed"; null for
 *   other members.
 * @property actions what the current user may do to the member, from the row's menu; empty for none.
 */
@Immutable
data class MemberItem(
    val sender: MessageSender,
    val name: UiText,
    val photo: ProfilePhoto?,
    val avatarId: Long,
    @param:DrawableRes val placeholderIcon: Int? = null,
    val status: UiText? = null,
    val isOnline: Boolean = false,
    val role: UiText? = null,
    val actions: ImmutableList<MemberAction> = persistentListOf(),
) {
    /** The row's key in a list: users and chats have ids of their own kinds. */
    val key: String
        get() =
            when (sender) {
                is MessageSender.User -> "user:${sender.userId}"
                is MessageSender.Chat -> "chat:${sender.chatId}"
            }

    override fun toString() = "MemberItem(sender=$sender, actions=$actions, …)"
}

/** What an administrator may do to a member, from the row's menu. */
enum class MemberAction {
    /** Make the member an administrator, choosing their rights. */
    Promote,

    /** Change an administrator's rights. */
    EditRights,

    /** Take an administrator's rights away. */
    Dismiss,

    /** Choose what the member may still do. */
    Restrict,

    /** Lift a member's restrictions. */
    Unrestrict,

    /** Remove the member and keep them from coming back, after a confirmation. */
    Remove,

    /** Let a removed user come back. */
    Unban,
}

/** A confirmation the info page shows. */
@Immutable
sealed interface ChatInfoDialog {
    /** Leaving the group or channel. */
    data object Leave : ChatInfoDialog

    /** Deleting the group or channel for everyone, which cannot be undone. */
    data object Delete : ChatInfoDialog

    /** Clearing the history for the user. */
    data object ClearHistory : ChatInfoDialog

    /** The QR code of [url], in a sheet. */
    data class QrCode(
        val url: String,
    ) : ChatInfoDialog {
        override fun toString() = "QrCode(…)"
    }
}

/** What the user does on the info page, handled by [ChatInfoViewModel.onEvent]. */
sealed interface ChatInfoEvent {
    /** Look the chat up again after it failed. */
    data object Retry : ChatInfoEvent

    /** Mute or unmute the chat. */
    data object ToggleMute : ChatInfoEvent

    data object Leave : ChatInfoEvent

    data object Delete : ChatInfoEvent

    data object ClearHistory : ChatInfoEvent

    /** Do what the dialog shown asks to confirm. */
    data object Confirm : ChatInfoEvent

    /** Close the dialog or sheet shown without doing anything. */
    data object DismissDialog : ChatInfoEvent

    data object CopyLink : ChatInfoEvent

    data object ShareLink : ChatInfoEvent

    data object ShowQrCode : ChatInfoEvent

    /** Put the description on the clipboard. */
    data object CopyDescription : ChatInfoEvent

    /** A mention in the description was tapped: open who it names. */
    data class OpenMention(
        val target: MentionTarget,
    ) : ChatInfoEvent

    /** A member's row was tapped: open their profile. */
    data class OpenMember(
        val sender: MessageSender,
    ) : ChatInfoEvent

    /** Open the members page with [filter]. */
    data class OpenMembers(
        val filter: MemberFilter,
    ) : ChatInfoEvent

    /** Open one of the administrators' pages. */
    data class Open(
        val page: ChatInfoPage,
    ) : ChatInfoEvent
}

/** The pages the info page opens for administrators. */
enum class ChatInfoPage {
    EditInfo,
    Permissions,
    InviteLinks,

    /** The boosts of a channel or supergroup, read only (`REQUIREMENTS.md` FR-P5). */
    Boosts,
}

/** One-off side effects of [ChatInfoViewModel], handled once by its route. */
sealed interface ChatInfoEffect {
    data class OpenChat(
        val chatId: Long,
    ) : ChatInfoEffect

    data class OpenProfile(
        val userId: Long,
    ) : ChatInfoEffect

    data class OpenMembers(
        val filter: MemberFilter,
    ) : ChatInfoEffect

    data class Open(
        val page: ChatInfoPage,
    ) : ChatInfoEffect

    /** The user left or deleted the chat, which is gone from the lists: the page and the conversation close. */
    data object Left : ChatInfoEffect

    /** The history was cleared: a snackbar says so. */
    data object HistoryCleared : ChatInfoEffect

    /** An action failed: a snackbar shows the error's text, none for [DomainError.Silent]. */
    data class ShowError(
        val error: DomainError,
    ) : ChatInfoEffect

    /** Put [text] on the clipboard; before Android 13 a snackbar says [what] was copied. */
    data class CopyText(
        val text: String,
        val what: CopiedChatText,
    ) : ChatInfoEffect {
        override fun toString() = "CopyText(what=$what, …)"
    }

    /** Share [text] through the system's share sheet. */
    data class Share(
        val text: String,
    ) : ChatInfoEffect {
        override fun toString() = "Share(…)"
    }
}

/** What [ChatInfoEffect.CopyText] copied, for its snackbar. */
enum class CopiedChatText {
    Link,
    Description,
}
