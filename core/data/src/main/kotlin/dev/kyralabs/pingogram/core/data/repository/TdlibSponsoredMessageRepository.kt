package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toSponsoredMessages
import dev.kyralabs.pingogram.core.data.mapper.toSponsoredReportResult
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.model.SponsoredMessages
import dev.kyralabs.pingogram.core.domain.model.SponsoredReportResult
import dev.kyralabs.pingogram.core.domain.repository.SponsoredMessageRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [SponsoredMessageRepository] on TDLib's sponsored message functions, one request per call with the usual timeout.
 * TDLib keeps the sponsored messages of a chat itself and answers again from memory, so nothing is cached here; a
 * `FLOOD_WAIT` fails the call and is never retried in a loop (`docs/TDLIB.md`, compliance notes).
 */
@AccountScoped
class TdlibSponsoredMessageRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : SponsoredMessageRepository {
        override suspend fun sponsoredMessages(chatId: Long): Outcome<SponsoredMessages> =
            client.request(TdApi.GetChatSponsoredMessages(chatId)).map { it.toSponsoredMessages() }

        override suspend fun click(
            chatId: Long,
            messageId: Long,
            isMediaClick: Boolean,
            fromFullscreen: Boolean,
        ): Outcome<Unit> =
            client.request(TdApi.ClickChatSponsoredMessage(chatId, messageId, isMediaClick, fromFullscreen)).map {}

        override suspend fun report(
            chatId: Long,
            messageId: Long,
            option: ReportOptionId,
        ): Outcome<SponsoredReportResult> =
            client
                .request(TdApi.ReportChatSponsoredMessage(chatId, messageId, option.bytes))
                .map { it.toSponsoredReportResult() }
    }
