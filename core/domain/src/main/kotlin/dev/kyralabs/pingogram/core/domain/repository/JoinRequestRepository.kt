package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.JoinRequest
import dev.kyralabs.pingogram.core.domain.model.JoinRequestsPage

/**
 * The requests to join a group or channel that wait for an administrator (`REQUIREMENTS.md` FR-P3), which those who
 * may invite users approve or dismiss. How many wait is `GroupInfo.pendingJoinRequests`.
 */
interface JoinRequestRepository {
    /** The next [limit] requests to chat [chatId] after [after] (null: the newest), matching [query] if not empty. */
    suspend fun requests(
        chatId: Long,
        query: String = "",
        after: JoinRequest? = null,
        limit: Int = PAGE_SIZE,
    ): Outcome<JoinRequestsPage>

    /** Lets user [userId] join chat [chatId] ([approve]), or dismisses their request. */
    suspend fun process(
        chatId: Long,
        userId: Long,
        approve: Boolean,
    ): Outcome<Unit>

    /** Approves or dismisses every request to chat [chatId]. */
    suspend fun processAll(
        chatId: Long,
        approve: Boolean,
    ): Outcome<Unit>

    companion object {
        const val PAGE_SIZE = 50
    }
}
