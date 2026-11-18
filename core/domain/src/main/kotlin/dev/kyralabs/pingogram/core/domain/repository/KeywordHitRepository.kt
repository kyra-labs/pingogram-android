package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.KeywordAlert
import dev.kyralabs.pingogram.core.domain.model.KeywordHit
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The account's keyword matches (board 30): the new messages of other people that have one of the user's words
 * ([KeywordAlertsRepository]) in the chats the settings look at, matched on this phone as TDLib announces them while
 * Pingogram is connected. Kept on this phone only, for a week; deleted at logout.
 */
interface KeywordHitRepository {
    /** The matches of the last week, the newest first, then every change. */
    val recent: Flow<ImmutableList<KeywordHit>>

    /** Each new match as it arrives, for its notification; nothing is replayed. */
    val alerts: Flow<KeywordAlert>

    /** Forgets the matches of chat [chatId], as once it is read. */
    suspend fun clearChat(chatId: Long)

    /**
     * Adds the matches of a backup (`docs/adr/0039-local-backup-and-restore.md`) that the account lacks; those older
     * than a week are dropped, as any are.
     */
    suspend fun restore(hits: List<KeywordHit>)
}
