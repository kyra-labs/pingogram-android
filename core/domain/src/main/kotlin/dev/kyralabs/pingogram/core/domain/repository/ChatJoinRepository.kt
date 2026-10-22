package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatInvite
import dev.kyralabs.pingogram.core.domain.model.JoinResult

/**
 * Joining groups and channels (`REQUIREMENTS.md` FR-P5): what an invite link leads to, and joining through it or a
 * public chat. Only ever on the user's tap; nothing joins by itself (Telegram API terms: no automation).
 *
 * Requests fail with a `DomainError`: `InviteLinkInvalid` for a link that expired, was revoked or is no invite link,
 * `ChatFull`, `TooManyChats`, `FloodWait`, `Network` for a request that gets no answer in time, …
 */
interface ChatJoinRepository {
    /** What [inviteLink] leads to (`checkChatInviteLink`); it only reads, and joins nothing. */
    suspend fun checkInvite(inviteLink: String): Outcome<ChatInvite>

    /** Joins the chat of [inviteLink], or sends a request to join it where the link asks for one. */
    suspend fun joinByInvite(inviteLink: String): Outcome<JoinResult>

    /** Joins public group or channel [chatId] (`joinChat`), or sends a request to join it where it asks for one. */
    suspend fun join(chatId: Long): Outcome<JoinResult>
}
