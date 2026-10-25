package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toMessageSender
import dev.kyralabs.pingogram.core.data.mapper.toTdMessageSender
import dev.kyralabs.pingogram.core.domain.model.BlockedSendersPage
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.BlockListRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [BlockListRepository] on TDLib's main block list (`blockListMain`): `getBlockedMessageSenders` and
 * `setMessageSenderBlockList`. TDLib announces the users and chats of a page (`updateUser`, `updateNewChat`) before it
 * answers, so `UserRepository` and `ChatRepository` know them.
 */
@AccountScoped
class TdlibBlockListRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : BlockListRepository {
        override suspend fun blockedSenders(
            offset: Int,
            limit: Int,
        ): Outcome<BlockedSendersPage> =
            client.request(TdApi.GetBlockedMessageSenders(TdApi.BlockListMain(), offset, limit)).map { senders ->
                BlockedSendersPage(
                    totalCount = senders.totalCount.coerceAtLeast(0),
                    senders = senders.senders.mapNotNull { it.toMessageSender() }.toImmutableList(),
                )
            }

        // td_api.tl: a null block list unblocks.
        override suspend fun unblock(sender: MessageSender): Outcome<Unit> =
            client.request(TdApi.SetMessageSenderBlockList(sender.toTdMessageSender(), null)).map {}

        override suspend fun block(sender: MessageSender): Outcome<Unit> =
            client.request(TdApi.SetMessageSenderBlockList(sender.toTdMessageSender(), TdApi.BlockListMain())).map {}
    }
