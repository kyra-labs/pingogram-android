package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A page of the users and chats the current user blocked (`BlockListRepository`): they cannot write to the user or see
 * their status and photos. Users and chats are looked up by their ids in `UserRepository` and `ChatRepository`.
 *
 * @property totalCount how many are blocked in all, as TDLib counts them.
 * @property senders this page's blocked users and chats, in TDLib's order.
 */
data class BlockedSendersPage(
    val totalCount: Int,
    val senders: ImmutableList<MessageSender>,
)
