package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A basic group (a small group) as TDLib knows it (`GroupRepository`), behind a chat of [ChatType.BasicGroup]. Plain
 * data, mapped from TDLib's `basicGroup` by `core:data`; it holds nothing private, so it can be logged.
 */
data class BasicGroup(
    val id: Long,
    val memberCount: Int,
    /** The current user's membership. */
    val status: ChatMemberStatus,
    /** False once the group was deactivated, e.g. upgraded to a supergroup; then nobody can send messages. */
    val isActive: Boolean,
    /** The supergroup the group was upgraded to; null if it was not. */
    val upgradedToSupergroupId: Long?,
)

/**
 * A supergroup or channel as TDLib knows it (`GroupRepository`), behind a chat of [ChatType.Supergroup]: badges, names
 * and membership for the chat list (`REQUIREMENTS.md` FR-L2). Plain data, mapped from TDLib's `supergroup` by
 * `core:data`.
 *
 * `toString()` leaves the usernames out, so a supergroup can be logged.
 */
data class Supergroup(
    val id: Long,
    /** Active usernames without `@`, the one to show first; empty for a private group or channel. */
    val usernames: ImmutableList<String>,
    /**
     * Members (subscribers of a channel); 0 if unknown. TDLib knows it only for supergroups it received in some lists
     * (td_api.tl `supergroup`); the full info of the supergroup always has it.
     */
    val memberCount: Int,
    /** The current user's membership. */
    val status: ChatMemberStatus,
    /** A channel, where only administrators post; otherwise a group. */
    val isChannel: Boolean,
    /** A group with topics. */
    val isForum: Boolean,
    /** Verified by Telegram; shown as a badge. */
    val isVerified: Boolean,
    /** Many users reported it as a scam; shown as a warning badge. */
    val isScam: Boolean,
    /** Many users reported it as impersonating someone; shown as a warning badge. */
    val isFake: Boolean,
) {
    override fun toString() = "Supergroup(id=$id, status=$status, isChannel=$isChannel, isForum=$isForum, …)"
}

/** The current user's membership of a group or channel (td_api.tl `ChatMemberStatus`). */
sealed interface ChatMemberStatus {
    /** Whether the current user is in the group or channel. */
    val isMember: Boolean

    /** The owner, with every right. An owner who left stays the owner, and can still delete the group or channel. */
    data class Creator(
        override val isMember: Boolean,
    ) : ChatMemberStatus

    data object Administrator : ChatMemberStatus {
        override val isMember = true
    }

    data object Member : ChatMemberStatus {
        override val isMember = true
    }

    /** With fewer rights than other members; also a former member who stays restricted on return ([isMember] false). */
    data class Restricted(
        override val isMember: Boolean,
    ) : ChatMemberStatus

    data object Left : ChatMemberStatus {
        override val isMember = false
    }

    /** Removed and kept from coming back; also the status in a deactivated basic group. */
    data object Banned : ChatMemberStatus {
        override val isMember = false
    }
}
