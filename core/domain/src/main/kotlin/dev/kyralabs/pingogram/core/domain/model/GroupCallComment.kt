package dev.kyralabs.pingogram.core.domain.model

import dev.kyralabs.pingogram.core.common.result.DomainError

/**
 * A comment in a voice chat or conference call (td_api.tl `groupCallMessage`): who wrote it, when, its text, and
 * whether the user may delete it. TDLib announces them with `updateNewGroupCallMessage` while the user is in the call;
 * they are not kept by TDLib.
 */
data class GroupCallComment(
    /** TDLib's id, unique in its call. */
    val id: Int,
    val sender: MessageSender,
    /** When it was sent, Unix time in seconds. */
    val date: Long,
    val text: FormattedText,
    /** Telegram Stars its sender paid to highlight it; 0 for a free comment. */
    val paidStarCount: Long = 0,
    /** From the call's owner. */
    val isFromOwner: Boolean = false,
    val canBeDeleted: Boolean = false,
    /** The user's own comment that Telegram refused (`updateGroupCallMessageSendFailed`); null when sent. */
    val sendError: DomainError? = null,
)
