package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * An event shown as a service message, centred in the conversation (`REQUIREMENTS.md` FR-C4). "The sender" is the
 * [Message.sender] of the service message; events Pingogram does not describe yet are [Other].
 */
sealed interface ServiceAction {
    /** The sender created a basic group, with [memberUserIds] as its first members. */
    data class BasicGroupCreated(
        val title: String,
        val memberUserIds: ImmutableList<Long>,
    ) : ServiceAction

    data class SupergroupCreated(
        val title: String,
    ) : ServiceAction

    data class ChannelCreated(
        val title: String,
    ) : ServiceAction

    data class TitleChanged(
        val title: String,
    ) : ServiceAction

    data class PhotoChanged(
        val photo: Photo,
    ) : ServiceAction

    data object PhotoDeleted : ServiceAction

    /** The sender added these users. */
    data class MembersAdded(
        val userIds: ImmutableList<Long>,
    ) : ServiceAction

    /** The sender joined a public group. */
    data object MemberJoined : ServiceAction

    /** The sender joined with an invite link. */
    data object MemberJoinedByLink : ServiceAction

    /** An admin accepted the sender's request to join. */
    data object MemberJoinedByRequest : ServiceAction

    /** The sender left. */
    data object MemberLeft : ServiceAction

    /** The sender removed [userId]. */
    data class MemberRemoved(
        val userId: Long,
    ) : ServiceAction

    /** The sender pinned [messageId], which may be deleted by now, or 0. */
    data class MessagePinned(
        val messageId: Long,
    ) : ServiceAction

    /** The sender took a screenshot of the chat. */
    data object ScreenshotTaken : ServiceAction

    /** A call that ended; the caller is the sender. [duration] is in seconds. */
    data class Call(
        val isVideo: Boolean,
        val duration: Int,
        val discardReason: CallDiscardReason,
    ) : ServiceAction

    /** This basic group became supergroup [supergroupId]; the basic group is closed. */
    data class UpgradedToSupergroup(
        val supergroupId: Long,
    ) : ServiceAction

    /** This supergroup was created from basic group [basicGroupId]. */
    data class UpgradedFromBasicGroup(
        val title: String,
        val basicGroupId: Long,
    ) : ServiceAction

    /**
     * Messages are now deleted [seconds] after they were sent, never if 0. [fromUserId] is not 0 when the default
     * setting of that user was applied.
     */
    data class AutoDeleteTimerChanged(
        val seconds: Int,
        val fromUserId: Long,
    ) : ServiceAction

    /** The sender, one of the user's contacts, joined Telegram. */
    data object ContactRegistered : ServiceAction

    /** A video chat starts at [startDate] (Unix seconds). */
    data class VideoChatScheduled(
        val startDate: Long,
    ) : ServiceAction

    data object VideoChatStarted : ServiceAction

    /** A video chat ended after [duration] seconds. */
    data class VideoChatEnded(
        val duration: Int,
    ) : ServiceAction

    /** The sender invited these users to the video chat. */
    data class VideoChatInvited(
        val userIds: ImmutableList<Long>,
    ) : ServiceAction

    /** An event Telegram describes in [text] itself. `toString()` leaves the text out. */
    data class Custom(
        val text: String,
    ) : ServiceAction {
        override fun toString() = "Custom(…)"
    }

    /** Any other event: gifts, payments, boosts, forum topics, chat themes, …. */
    data object Other : ServiceAction
}

/** Why a [ServiceAction.Call] ended. */
enum class CallDiscardReason {
    /** The reason is not known. */
    Unknown,

    /** Before it was answered: cancelled by the caller, or not answered. */
    Missed,

    /** Before it was answered, by the callee. */
    Declined,

    /** The connection was lost during the call. */
    Disconnected,

    /** One of the two hung up during the call. */
    HungUp,

    UpgradedToGroupCall,
}
