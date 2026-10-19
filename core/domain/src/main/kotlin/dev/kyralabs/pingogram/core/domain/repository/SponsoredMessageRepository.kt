package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.model.SponsoredMessages
import dev.kyralabs.pingogram.core.domain.model.SponsoredReportResult

/**
 * The sponsored messages of channels and chats with bots (`REQUIREMENTS.md` §2; Telegram's API terms): what TDLib
 * returns for a chat, the user opening one, and reporting one. Nothing is kept here, TDLib caches them itself; viewing
 * one goes through `ChatRepository.viewMessages`, like any message.
 *
 * Every call fails with a `DomainError`: `FloodWait`, `Network` for a request that gets no answer in time, or another
 * one when TDLib refuses it.
 */
interface SponsoredMessageRepository {
    /** The sponsored messages to show in chat [chatId]; [SponsoredMessages.None] for a chat without any. */
    suspend fun sponsoredMessages(chatId: Long): Outcome<SponsoredMessages>

    /**
     * Tells TDLib the user opened sponsored message [messageId] of chat [chatId]: its button, a link in its text, or
     * its media ([isMediaClick]), from the media viewer if [fromFullscreen].
     */
    suspend fun click(
        chatId: Long,
        messageId: Long,
        isMediaClick: Boolean,
        fromFullscreen: Boolean = false,
    ): Outcome<Unit>

    /**
     * Reports sponsored message [messageId] of chat [chatId]: first with [ReportOptionId.Initial], then with the option
     * the user chose among those TDLib asked for ([SponsoredReportResult.OptionRequired]).
     */
    suspend fun report(
        chatId: Long,
        messageId: Long,
        option: ReportOptionId = ReportOptionId.Initial,
    ): Outcome<SponsoredReportResult>
}
