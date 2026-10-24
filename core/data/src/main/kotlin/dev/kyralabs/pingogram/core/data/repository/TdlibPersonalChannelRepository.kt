package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.repository.PersonalChannelRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/** [PersonalChannelRepository] on TDLib's `getSuitablePersonalChats` and `setPersonalChat`; no cache. */
@Singleton
class TdlibPersonalChannelRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : PersonalChannelRepository {
        override suspend fun candidates(): Outcome<ImmutableList<Long>> =
            client.request(TdApi.GetSuitablePersonalChats()).map { chats ->
                chats.chatIds
                    ?.toList()
                    .orEmpty()
                    .toImmutableList()
            }

        override suspend fun setPersonalChannel(chatId: Long): Outcome<Unit> =
            client.request(TdApi.SetPersonalChat(chatId)).map {}
    }
