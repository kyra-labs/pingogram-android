package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A member of a group who saw a message (TDLib's `messageViewer`): user [userId], at [viewDate] (Unix time, seconds).
 * Telegram keeps these for a while only, and in groups up to a size, which TDLib decides
 * (`MessageProperties.canGetViewers`).
 */
data class MessageViewer(
    val userId: Long,
    val viewDate: Long,
)

/**
 * A reaction someone added to a message (TDLib's `addedReaction`): [type] by [sender] (a user, or a chat such as a
 * channel the sender reacted as), [isOutgoing] if the current user added it, at [date] (Unix time, seconds; 0 where
 * Telegram does not say).
 */
data class AddedReaction(
    val type: ReactionType,
    val sender: MessageSender,
    val isOutgoing: Boolean,
    val date: Long,
)

/**
 * A page of the reactions added to a message (`MessageInteractionRepository.addedReactions`).
 *
 * @property totalCount how many reactions of the kind asked for the message has in all, as Telegram counts them.
 * @property reactions this page's reactions, newest first, as Telegram gives them.
 * @property nextOffset where the next page starts; empty once there is none.
 */
data class AddedReactionsPage(
    val totalCount: Int,
    val reactions: ImmutableList<AddedReaction>,
    val nextOffset: String,
) {
    /** Telegram has no more reactions after this page. */
    val isLast: Boolean get() = nextOffset.isEmpty()
}
