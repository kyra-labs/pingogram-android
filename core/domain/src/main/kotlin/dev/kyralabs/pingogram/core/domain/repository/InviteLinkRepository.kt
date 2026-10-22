package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.InviteLink
import dev.kyralabs.pingogram.core.domain.model.InviteLinkSettings
import dev.kyralabs.pingogram.core.domain.model.InviteLinksPage

/**
 * The invite links of a group or channel (`REQUIREMENTS.md` FR-P3) for administrators who may invite: listing,
 * creating, editing, revoking and deleting them, one at a time. TDLib has no updates for links, so callers read a
 * list again after a change, or use what the change returns. The primary link is also in the group's full info
 * (`GroupInfoRepository`).
 *
 * Requests fail with a `DomainError`: `AdminRightsRequired`, `FloodWait`, `Network` for a request that gets no answer
 * in time, …
 */
interface InviteLinkRepository {
    /**
     * Up to [limit] links of chat [chatId] that [creatorUserId] made, active ones or revoked ones ([revoked]), newest
     * first, after [after] (the last link of the page before); the primary link comes first among active ones.
     */
    suspend fun links(
        chatId: Long,
        creatorUserId: Long,
        revoked: Boolean,
        after: InviteLink? = null,
        limit: Int = PAGE_SIZE,
    ): Outcome<InviteLinksPage>

    /** [link] of chat [chatId] as Telegram has it now. */
    suspend fun link(
        chatId: Long,
        link: String,
    ): Outcome<InviteLink>

    /** Creates a link of chat [chatId] with [settings]. */
    suspend fun create(
        chatId: Long,
        settings: InviteLinkSettings,
    ): Outcome<InviteLink>

    /** Changes [link] of chat [chatId] to [settings]; not the primary link, which can only be replaced. */
    suspend fun edit(
        chatId: Long,
        link: String,
        settings: InviteLinkSettings,
    ): Outcome<InviteLink>

    /**
     * Revokes [link] of chat [chatId], so nobody joins through it any more; revoking the primary link replaces it
     * with a new one.
     */
    suspend fun revoke(
        chatId: Long,
        link: String,
    ): Outcome<Unit>

    /** Deletes revoked [link] of chat [chatId]. */
    suspend fun deleteRevoked(
        chatId: Long,
        link: String,
    ): Outcome<Unit>

    /** Deletes every revoked link of chat [chatId] that [creatorUserId] made. */
    suspend fun deleteAllRevoked(
        chatId: Long,
        creatorUserId: Long,
    ): Outcome<Unit>

    companion object {
        /** Links a page holds: the most `getChatInviteLinks` returns at once. */
        const val PAGE_SIZE = 100
    }
}
