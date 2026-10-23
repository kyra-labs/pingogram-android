package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.SecretChat
import dev.kyralabs.pingogram.core.domain.repository.SecretChatRepository
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Secret chats in memory, with what setting a timer and ending a chat answer, and every such call. */
class FakeSecretChatRepository : SecretChatRepository {
    val secretChatMap = MutableStateFlow<ImmutableMap<Int, SecretChat>>(persistentMapOf())

    var onSetSelfDestructTime: suspend (Int) -> Outcome<Unit> = { Outcome.Success(Unit) }
    var onCloseSecretChat: suspend (Int) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** The chat and seconds of every [setSelfDestructTime] call. */
    val timersSet = mutableListOf<Pair<Long, Int>>()

    /** The secret chat of every [closeSecretChat] call. */
    val closed = mutableListOf<Int>()

    fun setSecretChats(vararg secretChats: SecretChat) {
        secretChatMap.value = secretChats.associateBy { it.id }.toImmutableMap()
    }

    override val secretChats: Flow<ImmutableMap<Int, SecretChat>> = secretChatMap

    override fun secretChat(id: Int): Flow<SecretChat?> = secretChatMap.map { it[id] }.distinctUntilChanged()

    override suspend fun createSecretChat(userId: Long): Outcome<Long> = error("not used by the conversation")

    override suspend fun closeSecretChat(secretChatId: Int): Outcome<Unit> {
        closed += secretChatId
        return onCloseSecretChat(secretChatId)
    }

    override suspend fun setSelfDestructTime(
        chatId: Long,
        seconds: Int,
    ): Outcome<Unit> {
        timersSet += chatId to seconds
        return onSetSelfDestructTime(seconds)
    }
}
