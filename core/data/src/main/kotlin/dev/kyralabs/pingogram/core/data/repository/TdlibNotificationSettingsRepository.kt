package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toChatMute
import dev.kyralabs.pingogram.core.data.mapper.toChatType
import dev.kyralabs.pingogram.core.data.mapper.toNotificationScope
import dev.kyralabs.pingogram.core.data.mapper.toProfilePhoto
import dev.kyralabs.pingogram.core.data.mapper.toScopeNotificationSettings
import dev.kyralabs.pingogram.core.data.mapper.toTdScope
import dev.kyralabs.pingogram.core.data.mapper.with
import dev.kyralabs.pingogram.core.domain.model.ChatMuteChange
import dev.kyralabs.pingogram.core.domain.model.MuteChange
import dev.kyralabs.pingogram.core.domain.model.NotificationException
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettings
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettingsChange
import dev.kyralabs.pingogram.core.domain.repository.NotificationSettingsRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [NotificationSettingsRepository] on TDLib's `getScopeNotificationSettings`, `setScopeNotificationSettings`,
 * `getChatNotificationSettingsExceptions`, `setChatNotificationSettings` and `resetAllNotificationSettings`, and the
 * updates `updateScopeNotificationSettings` and `updateChatNotificationSettings`. TDLib keeps every setting in memory
 * and in its database, so nothing is cached here and this is no `TdlibUpdateCache`.
 *
 * Changing settings reads them first and sends them back with only what Pingogram changes replaced, as TDLib's
 * setters replace every field. A chat's limited mute is reported as the time left (`mute_for`), turned into the time
 * it ends with [clock].
 */
@AccountScoped
class TdlibNotificationSettingsRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        private val clock: WallClock,
    ) : NotificationSettingsRepository {
        override suspend fun scopeSettings(scope: NotificationScope): Outcome<ScopeNotificationSettings> =
            client
                .request(
                    TdApi.GetScopeNotificationSettings(scope.toTdScope()),
                ).map { it.toScopeNotificationSettings() }

        override val scopeChanges: Flow<ScopeNotificationSettingsChange> =
            client.updates
                .filterIsInstance<TdApi.UpdateScopeNotificationSettings>()
                .mapNotNull { update ->
                    update.scope.toNotificationScope()?.let { scope ->
                        ScopeNotificationSettingsChange(
                            scope,
                            update.notificationSettings.toScopeNotificationSettings(),
                        )
                    }
                }

        override suspend fun setScopeSettings(
            scope: NotificationScope,
            settings: ScopeNotificationSettings,
        ): Outcome<Unit> {
            val current =
                when (val read = client.request(TdApi.GetScopeNotificationSettings(scope.toTdScope()))) {
                    is Outcome.Success -> read.value
                    is Outcome.Failure -> return read
                }
            return client.request(TdApi.SetScopeNotificationSettings(scope.toTdScope(), current.with(settings))).map {}
        }

        /**
         * The chats TDLib lists, each read with `getChat` (TDLib answers from memory), in TDLib's order. Sounds are not
         * compared: Pingogram sets no chat's sound. A chat TDLib no longer knows, or of a kind Pingogram does not know,
         * is left out.
         */
        override suspend fun exceptions(scope: NotificationScope): Outcome<List<NotificationException>> {
            val chatIds =
                when (
                    val listed =
                        client.request(TdApi.GetChatNotificationSettingsExceptions(scope.toTdScope(), false))
                ) {
                    is Outcome.Success -> listed.value.chatIds
                    is Outcome.Failure -> return listed
                }
            return chatExceptions(chatIds)
        }

        /** Chats [chatIds] as exceptions, read at once; any failure but `NotFound` fails them all. */
        private suspend fun chatExceptions(chatIds: LongArray): Outcome<List<NotificationException>> {
            val chats = coroutineScope { chatIds.map { async { client.request(TdApi.GetChat(it)) } }.awaitAll() }
            val error =
                chats.firstNotNullOfOrNull { chat ->
                    (chat as? Outcome.Failure)?.error?.takeUnless { it == DomainError.NotFound }
                }
            val now = clock.now().epochSecond
            return if (error != null) {
                Outcome.Failure(error)
            } else {
                Outcome.Success(chats.mapNotNull { (it as? Outcome.Success)?.value?.toNotificationException(now) })
            }
        }

        override val chatMuteChanges: Flow<ChatMuteChange> =
            client.updates
                .filterIsInstance<TdApi.UpdateChatNotificationSettings>()
                .map { ChatMuteChange(it.chatId, it.notificationSettings.toChatMute(clock.now().epochSecond)) }

        override suspend fun setChatMute(
            chatId: Long,
            change: MuteChange,
        ): Outcome<Unit> {
            val chat =
                when (val read = client.request(TdApi.GetChat(chatId))) {
                    is Outcome.Success -> read.value
                    is Outcome.Failure -> return read
                }
            val settings = chat.notificationSettings.with(change)
            return client.request(TdApi.SetChatNotificationSettings(chatId, settings)).map {}
        }

        override suspend fun resetAll(): Outcome<Unit> = client.request(TdApi.ResetAllNotificationSettings()).map {}

        private fun TdApi.Chat.toNotificationException(nowEpochSeconds: Long): NotificationException? {
            val type = type.toChatType() ?: return null
            return NotificationException(
                chatId = id,
                type = type,
                title = title,
                photo = photo?.toProfilePhoto(),
                mute = notificationSettings.toChatMute(nowEpochSeconds),
            )
        }
    }
