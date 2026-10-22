package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.domain.model.SharedMediaPage

/**
 * The shared media of a chat (`REQUIREMENTS.md` FR-P2, the profile's tabs): its messages of one [SharedMediaKind],
 * newest first, a page at a time, as TDLib finds them, from its database where it can, else from the server. Each page
 * is one the user scrolled to; nothing is kept, and cancelling a call drops its answer.
 *
 * Failures are `DomainError.Network` and `DomainError.FloodWait` (try again), `DomainError.NotFound` for a chat TDLib
 * does not know, and others as TDLib reports them.
 */
interface SharedMediaRepository {
    /**
     * Up to [limit] (at most 100) messages of [kind] in chat [chatId], newest first: the newest ones for
     * [fromMessageId] 0, else those older than it (the previous page's `nextFromMessageId`).
     */
    suspend fun page(
        chatId: Long,
        kind: SharedMediaKind,
        fromMessageId: Long = 0,
        limit: Int = PAGE_SIZE,
    ): Outcome<SharedMediaPage>

    companion object {
        /** Messages per page: a few screens of the photo grid. */
        const val PAGE_SIZE = 50
    }
}
