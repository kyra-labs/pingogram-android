package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.GiveawayInfo
import dev.kyralabs.pingogram.core.domain.model.GiveawayParticipantStatus
import dev.kyralabs.pingogram.core.domain.repository.GiveawayRepository

/** Giveaways: answers what TDLib knows as tests set, and records every request. */
class FakeGiveawayRepository : GiveawayRepository {
    var onInfo: suspend (messageId: Long) -> Outcome<GiveawayInfo> = {
        Outcome.Success(GiveawayInfo.Ongoing(1_795_975_200L, GiveawayParticipantStatus.Eligible, isEnded = false))
    }

    /** The chat and message of every [info] request. */
    val requests = mutableListOf<Pair<Long, Long>>()

    override suspend fun info(
        chatId: Long,
        messageId: Long,
    ): Outcome<GiveawayInfo> {
        requests += chatId to messageId
        return onInfo(messageId)
    }
}
