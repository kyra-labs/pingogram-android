package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toMessageSender
import dev.kyralabs.pingogram.core.data.mapper.toTdMessageSender
import dev.kyralabs.pingogram.core.domain.model.AvailableSender
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.SendAsRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SendAsRepository] on `getChatAvailableMessageSenders` and `setChatMessageSender`, without a cache: the list is
 * short, asked for only when the picker opens, and the current sender comes with the chat (`updateChatMessageSender`,
 * kept by [TdlibChatRepository]). Senders of a kind a newer TDLib adds are left out.
 */
@Singleton
class TdlibSendAsRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : SendAsRepository {
        override suspend fun availableSenders(chatId: Long): Outcome<ImmutableList<AvailableSender>> =
            client.request(TdApi.GetChatAvailableMessageSenders(chatId)).map { answer ->
                answer.senders
                    .mapNotNull { sender ->
                        sender.sender?.toMessageSender()?.let { AvailableSender(it, sender.needsPremium) }
                    }.toImmutableList()
            }

        override suspend fun setSender(
            chatId: Long,
            sender: MessageSender,
        ): Outcome<Unit> = client.request(TdApi.SetChatMessageSender(chatId, sender.toTdMessageSender())).map {}
    }
