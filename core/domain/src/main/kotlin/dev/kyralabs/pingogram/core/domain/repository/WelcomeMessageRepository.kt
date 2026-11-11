package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.WelcomeDraft
import dev.kyralabs.pingogram.core.domain.model.WelcomeMessage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * A group's welcome messages (td_api.tl `welcomeMessage`), which administrators with the right to send them
 * (`AdministratorRights.canSendWelcomeMessages`) set: TDLib announces them with `updateChatWelcomeMessages` once asked
 * to load them, and every change made since. td_api.tl (a94b83d) has no function to reorder them: new ones come last.
 */
interface WelcomeMessageRepository {
    /**
     * The welcome messages of chat [chatId], in Telegram's order: `loadChatWelcomeMessages`, then each
     * `updateChatWelcomeMessages` while collected. A failure to load comes as one `Outcome.Failure`.
     */
    fun messages(chatId: Long): Flow<Outcome<ImmutableList<WelcomeMessage>>>

    /** How many welcome messages a group may have: TDLib's option `welcome_message_count_max`, or Telegram's 5. */
    suspend fun countMax(): Int

    /** Adds [draft] as chat [chatId]'s last welcome message. */
    suspend fun add(
        chatId: Long,
        draft: WelcomeDraft,
    ): Outcome<Unit>

    /** Replaces welcome message [id] of chat [chatId] with [draft]. */
    suspend fun edit(
        chatId: Long,
        id: Int,
        draft: WelcomeDraft,
    ): Outcome<Unit>

    /** Deletes welcome message [id] of chat [chatId]. */
    suspend fun delete(
        chatId: Long,
        id: Int,
    ): Outcome<Unit>

    /** Deletes every welcome message of chat [chatId]: new members then see none. */
    suspend fun deleteAll(chatId: Long): Outcome<Unit>
}
