package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The chats the forward and share sheets show first, after Saved Messages (Settings → Appearance → Chats → "Chats shown
 * first"): at most [MAX], in the user's order, kept on this device.
 */
interface ChatsShownFirstRepository {
    /** The chats' ids in their order, then every change. Never fails: an unreadable file reads as none. */
    val chatIds: Flow<ImmutableList<Long>>

    /**
     * Replaces them with the first [MAX] of [chatIds], each once; fails with `DomainError.Unknown` if they cannot be
     * written (a full disk).
     */
    suspend fun setChatIds(chatIds: List<Long>): Outcome<Unit>

    companion object {
        /** The most chats shown first: enough for the people shared with most, few enough to leave the list below. */
        const val MAX = 5
    }
}
