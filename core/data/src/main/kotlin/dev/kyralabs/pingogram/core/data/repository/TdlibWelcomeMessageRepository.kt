package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toInputMessageContent
import dev.kyralabs.pingogram.core.data.mapper.toWelcomeMessage
import dev.kyralabs.pingogram.core.domain.model.WelcomeDraft
import dev.kyralabs.pingogram.core.domain.model.WelcomeLimits
import dev.kyralabs.pingogram.core.domain.model.WelcomeMessage
import dev.kyralabs.pingogram.core.domain.repository.WelcomeMessageRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [WelcomeMessageRepository] on TDLib's welcome messages, without a cache: TDLib keeps them and announces them with
 * `updateChatWelcomeMessages`, once `loadChatWelcomeMessages` asked for them and after each change.
 */
@AccountScoped
class TdlibWelcomeMessageRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : WelcomeMessageRepository {
        override fun messages(chatId: Long): Flow<Outcome<ImmutableList<WelcomeMessage>>> =
            channelFlow {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    client.updates
                        .filterIsInstance<TdApi.UpdateChatWelcomeMessages>()
                        .filter { it.chatId == chatId }
                        .collect { update ->
                            send(Outcome.Success(update.messages.map { it.toWelcomeMessage() }.toImmutableList()))
                        }
                }
                val loaded = client.request(TdApi.LoadChatWelcomeMessages(chatId))
                if (loaded is Outcome.Failure) send(loaded)
            }

        override suspend fun countMax(): Int {
            val value = (client.request(TdApi.GetOption(COUNT_MAX_OPTION)) as? Outcome.Success)?.value
            return (value as? TdApi.OptionValueInteger)?.value?.toInt()?.takeIf { it > 0 }
                ?: WelcomeLimits.COUNT_MAX_DEFAULT
        }

        override suspend fun add(
            chatId: Long,
            draft: WelcomeDraft,
        ): Outcome<Unit> {
            if (draft.isBlank()) return Outcome.Failure(DomainError.InputRejected)
            return client.request(TdApi.AddChatWelcomeMessage(chatId, draft.toInputMessageContent())).map {}
        }

        override suspend fun edit(
            chatId: Long,
            id: Int,
            draft: WelcomeDraft,
        ): Outcome<Unit> {
            if (draft.isBlank()) return Outcome.Failure(DomainError.InputRejected)
            return client.request(TdApi.EditChatWelcomeMessage(chatId, id, draft.toInputMessageContent())).map {}
        }

        override suspend fun delete(
            chatId: Long,
            id: Int,
        ): Outcome<Unit> = client.request(TdApi.DeleteChatWelcomeMessage(chatId, id)).map {}

        override suspend fun deleteAll(chatId: Long): Outcome<Unit> =
            client.request(TdApi.DeleteAllChatWelcomeMessages(chatId)).map {}

        private fun WelcomeDraft.isBlank(): Boolean = photo == null && text.text.isBlank()

        private companion object {
            const val COUNT_MAX_OPTION = "welcome_message_count_max"
        }
    }
