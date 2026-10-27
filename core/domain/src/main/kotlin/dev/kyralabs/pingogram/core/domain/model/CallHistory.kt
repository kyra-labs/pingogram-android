package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * One call of the call history (Recent calls), from the service message TDLib keeps of it in the private chat with
 * the other person: td_api.tl `messageCall`. [userId] is that person, whose private chat has the same id as they do
 * (td_api.tl: "the identifier of a private chat with a user is equal to the user identifier"). [date] is in seconds
 * since the epoch, [duration] in seconds (0 for a call that was never answered).
 *
 * A conference call's invitation (td_api.tl `messageGroupCall`, `docs/adr/0025-conference-calls.md`) is an entry too
 * ([isGroupCall]), in the private chat it was sent in; while its call goes on ([isActive]), it joins it.
 */
data class CallHistoryEntry(
    val chatId: Long,
    val messageId: Long,
    val userId: Long,
    val date: Long,
    val isOutgoing: Boolean,
    val isVideo: Boolean,
    val outcome: CallOutcome,
    val duration: Int,
    val isGroupCall: Boolean = false,
    val isActive: Boolean = false,
)

/** How a call of the history went, as Telegram's apps word it for the user's side. */
enum class CallOutcome {
    /** The call was answered; it lasted the entry's duration. */
    Answered,

    /** An incoming call nobody answered. */
    Missed,

    /** An incoming call the user declined, or an outgoing one the other person declined. */
    Declined,

    /** An outgoing call the user hung up before the other person answered. */
    Cancelled,
}

/** One page of the call history, newest first; [nextOffset] is where the next page starts, empty after the last. */
data class CallHistoryPage(
    val entries: ImmutableList<CallHistoryEntry>,
    val nextOffset: String,
)

/** A change to the call history after its pages were read. */
sealed interface CallHistoryChange {
    /** A call ended and its service message arrived. */
    data class Added(
        val entry: CallHistoryEntry,
    ) : CallHistoryChange

    /** Messages [messageIds] of chat [chatId] are gone, on this device or every device. */
    data class Removed(
        val chatId: Long,
        val messageIds: ImmutableList<Long>,
    ) : CallHistoryChange
}
