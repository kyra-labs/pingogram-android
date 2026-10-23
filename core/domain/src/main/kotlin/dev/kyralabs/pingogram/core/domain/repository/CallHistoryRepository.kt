package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.CallHistoryChange
import dev.kyralabs.pingogram.core.domain.model.CallHistoryEntry
import dev.kyralabs.pingogram.core.domain.model.CallHistoryPage
import kotlinx.coroutines.flow.Flow

/**
 * The user's one-to-one calls, newest first (Recent calls, `docs/ARCHITECTURE.md`, Calls): the call service messages
 * of every private chat, which TDLib searches with `searchCallMessages`. Nothing is cached; [changes] says what arrives
 * or goes while someone collects it.
 */
interface CallHistoryRepository {
    /** The page of calls that starts at [offset] (empty: the newest), only the missed ones with [onlyMissed]. */
    suspend fun page(
        offset: String = "",
        limit: Int = PAGE_SIZE,
        onlyMissed: Boolean = false,
    ): Outcome<CallHistoryPage>

    /** Calls that end and call messages that are deleted, as they happen; hot, without replay. */
    val changes: Flow<CallHistoryChange>

    /**
     * Deletes [entry] from the history: its service message, for the user only, as Telegram's apps do; the other
     * person keeps theirs.
     */
    suspend fun delete(entry: CallHistoryEntry): Outcome<Unit>

    companion object {
        const val PAGE_SIZE = 50
    }
}
