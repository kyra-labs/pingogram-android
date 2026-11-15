package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Transcript
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.Flow

/**
 * The transcripts of an account's voice and round video messages, kept on this device only, by message: never sent to
 * Telegram or anywhere else. Logging out deletes them, as does the account leaving the device.
 */
interface TranscriptRepository {
    /** The transcripts of chat [chatId]'s messages, by message id, then every change. */
    fun transcripts(chatId: Long): Flow<ImmutableMap<Long, Transcript>>

    /** Keeps [transcript] for message [messageId] of chat [chatId]; fails with `DomainError.Unknown` if it cannot. */
    suspend fun save(
        chatId: Long,
        messageId: Long,
        transcript: Transcript,
    ): Outcome<Unit>
}
