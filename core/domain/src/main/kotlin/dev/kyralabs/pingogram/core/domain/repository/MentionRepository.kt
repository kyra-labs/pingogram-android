package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import kotlinx.collections.immutable.ImmutableList

/**
 * The messages that mention the user or reply to the user's messages, across chats (`REQUIREMENTS.md` FR-X1, Mentions
 * & replies). TDLib finds them only chat by chat, so this asks the chats that can have them, nothing more, and keeps
 * nothing: each call reads them again. Every request is one the user started (Telegram's API terms: no scraping).
 *
 * Failures are `DomainError.Network` (no answer in time), `DomainError.FloodWait` and others as TDLib reports them.
 */
interface MentionRepository {
    /**
     * The mentions and replies of the chats with unread ones, and the newest ones of the most recent groups, each with
     * its chat: unread first ([dev.kyralabs.pingogram.core.domain.model.Message.containsUnreadMention]), then newest
     * first. A chat that cannot be searched is left out; it fails only when no chat could be.
     */
    suspend fun mentions(): Outcome<ImmutableList<FoundMessage>>
}
