package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.model.SponsoredMessages
import dev.kyralabs.pingogram.core.domain.model.SponsoredReportResult
import dev.kyralabs.pingogram.core.domain.repository.SponsoredMessageRepository

/**
 * The sponsored messages' repository: it answers loads with [onLoad] (none by default) and reports with [onReport]
 * (reported by default), and records every load, click and report.
 */
class FakeSponsoredMessageRepository : SponsoredMessageRepository {
    /** The chat id of every [sponsoredMessages] call. */
    val loads = mutableListOf<Long>()

    var onLoad: suspend (Long) -> Outcome<SponsoredMessages> = { Outcome.Success(SponsoredMessages.None) }

    val clicks = mutableListOf<Click>()
    var onClick: suspend (Click) -> Outcome<Unit> = { Outcome.Success(Unit) }

    val reports = mutableListOf<Pair<Long, ReportOptionId>>()
    var onReport: suspend (Long, ReportOptionId) -> Outcome<SponsoredReportResult> = { _, _ ->
        Outcome.Success(SponsoredReportResult.Reported)
    }

    override suspend fun sponsoredMessages(chatId: Long): Outcome<SponsoredMessages> {
        loads += chatId
        return onLoad(chatId)
    }

    override suspend fun click(
        chatId: Long,
        messageId: Long,
        isMediaClick: Boolean,
        fromFullscreen: Boolean,
    ): Outcome<Unit> {
        val click = Click(chatId, messageId, isMediaClick)
        clicks += click
        return onClick(click)
    }

    override suspend fun report(
        chatId: Long,
        messageId: Long,
        option: ReportOptionId,
    ): Outcome<SponsoredReportResult> {
        reports += messageId to option
        return onReport(messageId, option)
    }

    /** One [click] call. */
    data class Click(
        val chatId: Long,
        val messageId: Long,
        val isMediaClick: Boolean,
    )
}
