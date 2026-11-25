package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatNote
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.Flow

/**
 * The account's notes and names for chats (`REQUIREMENTS.md` FR-X20), kept on this device only: Telegram never sees
 * them, and they go when the account logs out. Writes fail with `DomainError.Unknown` when the file cannot be written.
 */
interface ChatNoteRepository {
    /** Every chat's note, by chat id, then every change. */
    val notes: Flow<ImmutableMap<Long, ChatNote>>

    /**
     * Keeps [note] ([ChatNote.kept]) in place of the chat's earlier one; an empty one ([ChatNote.isEmpty]) removes
     * it.
     */
    suspend fun set(note: ChatNote): Outcome<Unit>
}
