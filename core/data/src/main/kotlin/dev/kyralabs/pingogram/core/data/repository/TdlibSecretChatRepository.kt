package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toSecretChat
import dev.kyralabs.pingogram.core.domain.model.SecretChat
import dev.kyralabs.pingogram.core.domain.repository.SecretChatRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [SecretChatRepository] on TDLib's secret chats. TDLib announces each with `updateSecretChat` before its chat reaches
 * the app, and sends the whole secret chat again whenever it changes (its state, its key hash once agreed, the other
 * side's layer), so every secret chat is kept for the session: as many as the secret chats on this device.
 *
 * TDLib accepts the secret chats other users start by itself (td_api.tl has no function for it; a session refuses
 * them only if `toggleSessionCanAcceptSecretChats` turned that off): such a chat arrives pending, not outbound, and
 * turns ready once TDLib agreed on the key. The self-destruct timer is the chat's `message_auto_delete_time`, which
 * [TdlibChatRepository] keeps from `updateChatMessageAutoDeleteTime`; it is set here with
 * `setChatMessageAutoDeleteTime`, which takes any number of seconds in a secret chat (other chats only whole days).
 * Deleting a secret chat closes it first (`ChatDeletion`).
 */
@AccountScoped
class TdlibSecretChatRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : SecretChatRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, persistentMapOf(), ::reduce)

        override val secretChats: Flow<ImmutableMap<Int, SecretChat>> =
            store.state.distinctUntilChanged { old, new -> old === new }

        override fun secretChat(id: Int): Flow<SecretChat?> = store.state.map { it[id] }.distinctUntilChanged()

        override suspend fun createSecretChat(userId: Long): Outcome<Long> =
            client.request(TdApi.CreateNewSecretChat(userId)).map { it.id }

        override suspend fun closeSecretChat(secretChatId: Int): Outcome<Unit> =
            client.request(TdApi.CloseSecretChat(secretChatId)).map {}

        override suspend fun setSelfDestructTime(
            chatId: Long,
            seconds: Int,
        ): Outcome<Unit> = client.request(TdApi.SetChatMessageAutoDeleteTime(chatId, seconds)).map {}
    }

/** The secret chats with [update] applied; the same map for other updates and for a secret chat that is unchanged. */
private fun reduce(
    secretChats: PersistentMap<Int, SecretChat>,
    update: TdApi.Update,
): PersistentMap<Int, SecretChat> {
    if (update !is TdApi.UpdateSecretChat) return secretChats
    val secretChat = update.secretChat.toSecretChat()
    return if (secretChats[secretChat.id] == secretChat) secretChats else secretChats.putting(secretChat.id, secretChat)
}
