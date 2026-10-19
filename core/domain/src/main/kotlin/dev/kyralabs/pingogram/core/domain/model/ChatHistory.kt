package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * The loaded part of a chat's history, for the conversation screen (`REQUIREMENTS.md` FR-C1): a run of consecutive
 * messages, without gaps, from the newest loaded one back to the oldest loaded so far. Usually it starts at the chat's
 * newest message; loaded around an older one (the first unread message, a message found or replied to far back), it
 * ends before it until newer pages reach it ([reachedEnd]). A snapshot, replaced on every change.
 */
data class ChatHistory(
    /**
     * Newest first, as the conversation's reverse layout shows them: by id, which TDLib assigns in increasing order
     * (a message being sent has a temporary id, larger than every sent one, until it gets its final id).
     */
    val messages: ImmutableList<Message>,
    /** [messages] go back to the chat's first message: there is nothing older to load. */
    val reachedStart: Boolean,
    /**
     * [messages] go up to the chat's newest message: there is nothing newer to load, and new messages join them. False
     * while a history loaded around an older message has not reached it.
     */
    val reachedEnd: Boolean = true,
    /**
     * Tells runs of messages apart: it grows each time a load replaces the messages with another run
     * (`MessageRepository.loadAround`, `loadNewest`), and stays while pages and updates change them. A caller that
     * gets snapshots on more than one path can tell from it whether one is from before a replacement.
     */
    val windowId: Long = 0,
)
