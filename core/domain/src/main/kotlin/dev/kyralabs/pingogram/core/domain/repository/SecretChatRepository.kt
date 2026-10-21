package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.SecretChat
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.Flow

/**
 * The secret chats TDLib announced in this session, kept in memory (`docs/ARCHITECTURE.md`, Secret chats), and what the
 * user does with them (`REQUIREMENTS.md` FR-E1). TDLib does all of the cryptography, and accepts the secret chats other
 * users start by itself: there is no call for that. Everything is dropped when TDLib closes (logout).
 *
 * The actions fail with a `DomainError`: `FloodWait`, `Network` for a request that gets no answer in time, `NotFound`
 * for a user or chat TDLib does not know, …
 */
interface SecretChatRepository {
    /** Every secret chat by id, then the map after every change of any of them; for lists of chats. */
    val secretChats: Flow<ImmutableMap<Int, SecretChat>>

    /** The secret chat with [id], then every change; null while TDLib has not announced it. */
    fun secretChat(id: Int): Flow<SecretChat?>

    /**
     * Starts a new secret chat with user [userId] and returns the id of its chat, which TDLib announces at once, in
     * the pending state until the other user's app agrees on the key.
     */
    suspend fun createSecretChat(userId: Long): Outcome<Long>

    /** Ends secret chat [secretChatId] for both sides; its messages stay on this device until the chat is deleted. */
    suspend fun closeSecretChat(secretChatId: Int): Outcome<Unit>

    /**
     * Sets the self-destruct timer of secret chat [chatId]: new messages are deleted [seconds] after they were opened,
     * on both sides; 0 turns it off. TDLib announces the new time with the chat, and posts a service message.
     */
    suspend fun setSelfDestructTime(
        chatId: Long,
        seconds: Int,
    ): Outcome<Unit>
}
