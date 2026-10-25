package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.ChatBoostStatus
import dev.kyralabs.pingogram.core.domain.repository.ChatBoostRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [ChatBoostRepository] on TDLib's `getChatBoostStatus` and `getAvailableChatBoostSlots`, read only: nothing here
 * boosts (`boostChat` is never sent, `REQUIREMENTS.md` FR-P5). The user's boost slots are only counted; their failure
 * (an account without Telegram Premium may have none to list) leaves the count at 0 rather than failing the status.
 */
@AccountScoped
class TdlibChatBoostRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ChatBoostRepository {
        override suspend fun status(chatId: Long): Outcome<ChatBoostStatus> {
            val status =
                when (val found = client.request(TdApi.GetChatBoostStatus(chatId))) {
                    is Outcome.Success -> found.value
                    is Outcome.Failure -> return found
                }
            val slots = (client.request(TdApi.GetAvailableChatBoostSlots()) as? Outcome.Success)?.value?.slots
            return Outcome.Success(
                ChatBoostStatus(
                    level = status.level,
                    boostCount = status.boostCount,
                    currentLevelBoostCount = status.currentLevelBoostCount,
                    nextLevelBoostCount = status.nextLevelBoostCount,
                    premiumMemberCount = status.premiumMemberCount,
                    premiumMemberPercentage = status.premiumMemberPercentage,
                    boostedByUser = status.appliedSlotIds?.isNotEmpty() == true,
                    // td_api.tl: 0 when the slot boosts no chat.
                    unusedBoosts = slots.orEmpty().count { it.currentlyBoostedChatId == 0L },
                ),
            )
        }
    }
