package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toGiveawayInfo
import dev.kyralabs.pingogram.core.domain.model.GiveawayInfo
import dev.kyralabs.pingogram.core.domain.repository.GiveawayRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [GiveawayRepository] on `getGiveawayInfo`, without a cache: TDLib asks Telegram each time, and the answer changes
 * as the user joins the chats and as the winners are picked.
 */
@AccountScoped
class TdlibGiveawayRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : GiveawayRepository {
        override suspend fun info(
            chatId: Long,
            messageId: Long,
        ): Outcome<GiveawayInfo> =
            when (val result = client.request(TdApi.GetGiveawayInfo(chatId, messageId))) {
                is Outcome.Success -> {
                    result.value.toGiveawayInfo()?.let { Outcome.Success(it) }
                        ?: Outcome.Failure(DomainError.Unknown(0, "Unknown giveaway info"))
                }

                is Outcome.Failure -> {
                    result
                }
            }
    }
