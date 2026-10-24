package dev.kyralabs.pingogram.core.domain.model

/**
 * One of the recent actions of a supergroup or channel (td_api.tl `chatEvent`), which its administrators see for the
 * last 48 hours: who ([actor]) did what ([action]) when ([date], Unix time).
 */
data class ChatEvent(
    val id: Long,
    val date: Long,
    val actor: MessageSender,
    val action: ChatEventAction,
)

/** What happened. Telegram's other kinds of action are [Other]. */
sealed interface ChatEventAction {
    /** A message edited: as it was ([old]) and is ([new]). */
    data class MessageEdited(
        val old: Message,
        val new: Message,
    ) : ChatEventAction

    data class MessageDeleted(
        val message: Message,
    ) : ChatEventAction

    data class MessagePinned(
        val message: Message,
    ) : ChatEventAction

    data class MessageUnpinned(
        val message: Message,
    ) : ChatEventAction

    /** The actor joined: by themselves, by an invite link ([inviteLink]), or approved by [approverUserId]. */
    data class MemberJoined(
        val inviteLink: String? = null,
        val approverUserId: Long = 0,
    ) : ChatEventAction

    data object MemberLeft : ChatEventAction

    /** The actor added user [userId]. */
    data class MemberInvited(
        val userId: Long,
    ) : ChatEventAction

    /** [member]'s role went from [old] to [new]: promoted, dismissed, restricted, banned, let back. */
    data class MemberRoleChanged(
        val member: MessageSender,
        val old: MemberRole,
        val new: MemberRole,
    ) : ChatEventAction

    data class TitleChanged(
        val old: String,
        val new: String,
    ) : ChatEventAction

    data class DescriptionChanged(
        val new: String,
    ) : ChatEventAction

    /** The public username changed to [new] (empty: the chat became private). */
    data class UsernameChanged(
        val new: String,
    ) : ChatEventAction

    /** The photo changed, or was [removed]. */
    data class PhotoChanged(
        val removed: Boolean,
    ) : ChatEventAction

    data object PermissionsChanged : ChatEventAction

    /** Slow mode set to [seconds] (0: off). */
    data class SlowModeChanged(
        val seconds: Int,
    ) : ChatEventAction

    /** Invite link [link] was edited, revoked or deleted ([change]). */
    data class InviteLinkChanged(
        val link: String,
        val change: InviteLinkChange,
    ) : ChatEventAction

    /** The discussion group or channel became [chatId] (0: unlinked). */
    data class LinkedChatChanged(
        val chatId: Long,
    ) : ChatEventAction

    /** A setting was turned on or off ([on]). */
    data class SettingToggled(
        val setting: ChatEventSetting,
        val on: Boolean,
    ) : ChatEventAction

    data object ReactionsChanged : ChatEventAction

    data object VideoChatStarted : ChatEventAction

    data object VideoChatEnded : ChatEventAction

    /** A forum topic created, edited, closed, pinned, deleted, … */
    data object TopicChanged : ChatEventAction

    data object Other : ChatEventAction
}

enum class InviteLinkChange { Edited, Revoked, Deleted }

/** The switches the recent actions report. */
enum class ChatEventSetting { SignMessages, ShowMessageSender, ProtectedContent, Topics, Invites, HistoryForNewMembers }

/** The kinds of recent actions to read (td_api.tl `chatEventLogFilters`). */
enum class ChatEventFilter {
    MessageEdits,
    MessageDeletions,
    MessagePins,
    MemberJoins,
    MemberLeaves,
    MemberInvites,
    MemberPromotions,
    MemberRestrictions,
    InfoChanges,
    SettingChanges,
    InviteLinkChanges,
    VideoChatChanges,
    TopicChanges,
}
