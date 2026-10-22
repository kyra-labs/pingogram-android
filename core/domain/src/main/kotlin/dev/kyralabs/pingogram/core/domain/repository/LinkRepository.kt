package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.LinkTarget

/**
 * Telegram links (`tg://…`, `https://t.me/…`; `docs/adr/0013-deep-links.md`): what a link leads to, from TDLib's
 * `getInternalLinkType`, and what the app needs to open it. The one place the app reads links, whether they come from
 * another app (an intent), a tap in a message or bio, and later a scanned QR code or a shared text. A request that gets
 * no answer in time fails with `DomainError.Network`.
 */
interface LinkRepository {
    /**
     * Where [link] leads. Resolving only reads: it looks up usernames, phone numbers, contact tokens and messages
     * (`searchPublicChat`, `searchUserByPhoneNumber`, `searchUserByToken`, `getMessageLinkInfo`), which Telegram may
     * answer with a `FLOOD_WAIT` after many lookups; it never joins, logs in, sends or changes anything. A link nobody
     * or nothing answers to is [LinkTarget.NotFound]; one Pingogram does not open, or that is no Telegram link at all,
     * [LinkTarget.Unsupported].
     */
    suspend fun resolve(link: String): Outcome<LinkTarget>

    /**
     * Puts [text], the text a link suggests ([LinkTarget.Chat.draftText]), into chat [chatId]'s draft, unless the chat
     * has a draft already, which it keeps: the composer then shows it. Nothing is sent.
     */
    suspend fun prefillDraft(
        chatId: Long,
        text: String,
    ): Outcome<Unit>
}
