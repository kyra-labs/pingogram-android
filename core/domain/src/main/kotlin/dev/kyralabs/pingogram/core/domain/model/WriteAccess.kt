package dev.kyralabs.pingogram.core.domain.model

/**
 * Whether the current user can send messages in a chat, for the conversation's composer (`REQUIREMENTS.md` FR-C5). It
 * follows TDLib's own checks (`DialogManager::can_send_message_to_dialog`, `chatMemberStatus`, `chatPermissions`), so
 * the composer shows only where sending can work; TDLib still has the last word when a message is sent.
 */
enum class WriteAccess {
    /** Messages can be sent. */
    Allowed,

    /** A channel the user reads without the right to post: nothing to send, nothing to explain. */
    ReadOnly,

    /** The user is not (any more) a member of the group: left, removed or banned. */
    NotMember,

    /**
     * The user may not send messages here: restricted in the group, a group where members cannot write, a deactivated
     * group, a deleted account, or a read-only service chat.
     */
    Restricted,

    /** Not known yet: TDLib has not announced the chat's group. */
    Unknown,
}

/**
 * The current user's [WriteAccess] to this chat, given the chat's [user] for a private or secret chat (null while TDLib
 * has not announced it, or for other chats), and its [basicGroup] or [supergroup] for a group or channel (null while
 * unknown, or for other chats).
 *
 * - **Private and secret chats**: allowed unless the user's account was deleted or the chat's permissions forbid it,
 *   as TDLib's do for its read-only service chats (replies, verification codes).
 * - **Groups**: the owner and administrators can always write, members as the chat's permissions allow, restricted
 *   members as their restriction and the chat's permissions both allow. Not a member: [WriteAccess.NotMember].
 * - **Channels**: only the owner and administrators with the right to post; everyone else reads
 *   ([WriteAccess.ReadOnly]).
 */
fun Chat.writeAccess(
    user: User?,
    basicGroup: BasicGroup?,
    supergroup: Supergroup?,
): WriteAccess =
    when (val type = type) {
        is ChatType.Private, is ChatType.Secret -> {
            when {
                user?.type == UserType.Deleted -> WriteAccess.Restricted
                permissions.canSendBasicMessages -> WriteAccess.Allowed
                else -> WriteAccess.Restricted
            }
        }

        is ChatType.BasicGroup -> {
            when {
                basicGroup == null -> WriteAccess.Unknown
                !basicGroup.isActive -> WriteAccess.Restricted
                else -> groupAccess(basicGroup.status)
            }
        }

        is ChatType.Supergroup -> {
            when {
                supergroup == null -> WriteAccess.Unknown
                type.isChannel -> channelAccess(supergroup.status)
                else -> groupAccess(supergroup.status)
            }
        }
    }

private fun Chat.groupAccess(status: ChatMemberStatus): WriteAccess =
    when {
        !status.isMember -> WriteAccess.NotMember
        status is ChatMemberStatus.Creator || status is ChatMemberStatus.Administrator -> WriteAccess.Allowed
        status is ChatMemberStatus.Restricted && !status.canSendBasicMessages -> WriteAccess.Restricted
        permissions.canSendBasicMessages -> WriteAccess.Allowed
        else -> WriteAccess.Restricted
    }

private fun channelAccess(status: ChatMemberStatus): WriteAccess =
    when (status) {
        is ChatMemberStatus.Creator -> if (status.isMember) WriteAccess.Allowed else WriteAccess.ReadOnly
        is ChatMemberStatus.Administrator -> if (status.canPostMessages) WriteAccess.Allowed else WriteAccess.ReadOnly
        else -> WriteAccess.ReadOnly
    }
