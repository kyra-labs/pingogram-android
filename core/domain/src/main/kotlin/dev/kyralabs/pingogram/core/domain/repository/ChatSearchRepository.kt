package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatSearchCursor
import dev.kyralabs.pingogram.core.domain.model.ChatSearchPage
import dev.kyralabs.pingogram.core.domain.model.ChatSearchQuery
import java.time.Instant

/**
 * Search in one chat (`REQUIREMENTS.md` §5.9, in-chat search): the messages that match what the user typed or the
 * sender they picked, and the message to show for a day they picked. Every request is one the user started, nothing
 * is kept, and cancelling a call drops its answer (Telegram's API terms: no scraping).
 *
 * Failures are `DomainError.Network` (no answer in time), `DomainError.FloodWait` and others as TDLib reports them.
 */
interface ChatSearchRepository {
    /**
     * A page of the messages of chat [chatId] that [query] finds, newest first: the first page for a null [cursor],
     * then the page after the one whose [ChatSearchPage.next] is passed. An empty query ([ChatSearchQuery.isEmpty])
     * finds nothing, without asking TDLib; so does one Telegram finds too short to search.
     */
    suspend fun search(
        chatId: Long,
        query: ChatSearchQuery,
        cursor: ChatSearchCursor? = null,
    ): Outcome<ChatSearchPage>

    /**
     * The id of the message of chat [chatId] to show for a day that starts at [start]: the first one sent then or
     * later; the last one before it when none was; `DomainError.NotFound` when the chat has no messages up to [end],
     * the day's end.
     */
    suspend fun messageForDay(
        chatId: Long,
        start: Instant,
        end: Instant,
    ): Outcome<Long>

    companion object {
        /** Messages a page holds; TDLib allows up to 100, and may return fewer. */
        const val PAGE_SIZE = 50
    }
}
