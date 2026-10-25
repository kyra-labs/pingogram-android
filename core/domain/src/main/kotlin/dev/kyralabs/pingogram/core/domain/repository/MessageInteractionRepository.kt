package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AddedReactionsPage
import dev.kyralabs.pingogram.core.domain.model.MessageViewer
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import kotlinx.collections.immutable.ImmutableList

/**
 * Who saw a group message and who reacted to it, as Telegram shows them to the group's members ("Seen by" and
 * "Reactions" in a message's actions, `docs/DESIGN_SYSTEM.md` §6). Nothing is cached, and nothing is asked before the
 * user opens the list: each call is one request to TDLib. The users and chats of an answer are announced by TDLib
 * before it answers, so `UserRepository` and `ChatRepository` know them.
 *
 * Every call fails with a `DomainError`: `FloodWait`, `Network` for a request that gets no answer in time,
 * `MessageViewersUnavailable` where Telegram no longer keeps who saw the message (too old, a group too large), or
 * another one when TDLib refuses it.
 */
interface MessageInteractionRepository {
    /**
     * The members who saw message [messageId] of chat [chatId], the latest view first; only where TDLib allows it
     * (`MessageProperties.canGetViewers`). Telegram gives them all at once.
     */
    suspend fun viewers(
        chatId: Long,
        messageId: Long,
    ): Outcome<ImmutableList<MessageViewer>>

    /**
     * Up to [limit] (at most 100, TDLib's limit) reactions added to message [messageId] of chat [chatId], of [type]
     * only if it is not null, from [offset] (empty for the first page, then the page's `nextOffset`); only where TDLib
     * allows it (`InteractionInfo.canGetAddedReactions`).
     */
    suspend fun addedReactions(
        chatId: Long,
        messageId: Long,
        type: ReactionType?,
        offset: String = "",
        limit: Int = PAGE_SIZE,
    ): Outcome<AddedReactionsPage>

    companion object {
        /** The reactions a page holds, as many as a page of blocked users. */
        const val PAGE_SIZE = 50
    }
}
