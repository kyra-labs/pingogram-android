package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * Someone who asked to join a group or channel through a link that needs an administrator's approval
 * (td_api.tl `chatJoinRequest`): user [userId], when ([date], Unix seconds), and the [bio] they had then.
 *
 * `toString()` leaves the bio out, so a request can be logged.
 */
data class JoinRequest(
    val userId: Long,
    val date: Long,
    val bio: String,
) {
    override fun toString() = "JoinRequest(userId=$userId, date=$date, …)"
}

/** A page of join requests, newest first, of [totalCount] in all. */
data class JoinRequestsPage(
    val requests: ImmutableList<JoinRequest>,
    val totalCount: Int,
)
