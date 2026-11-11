package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.repository.ChatActionBarRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [ChatActionBarRepository] on `removeChatActionBar` and `sharePhoneNumber`, without a cache: TDLib reports the bar
 * gone with `updateChatActionBar`, which [TdlibChatRepository] applies to the chat.
 */
@AccountScoped
class TdlibChatActionBarRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ChatActionBarRepository {
        override suspend fun dismiss(chatId: Long): Outcome<Unit> =
            client.request(TdApi.RemoveChatActionBar(chatId)).map {}

        override suspend fun sharePhoneNumber(userId: Long): Outcome<Unit> =
            client.request(TdApi.SharePhoneNumber(userId)).map {}
    }
