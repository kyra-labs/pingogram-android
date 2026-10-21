package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.EncryptionKeyHash
import dev.kyralabs.pingogram.core.domain.model.SecretChat
import dev.kyralabs.pingogram.core.domain.model.SecretChatState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Secret chats: the cache of `updateSecretChat`, starting and ending one, and the self-destruct timer. */
class TdlibSecretChatRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibSecretChatRepository(client, backgroundScope)

    @Test
    fun `knows no secret chat before TDLib announces one`() =
        runTest {
            val repository = repository()

            assertNull(repository.secretChat(7).first())
            assertTrue(repository.secretChats.first().isEmpty())
        }

    @Test
    fun `maps an announced secret chat with its key hash`() =
        runTest {
            val repository = repository()

            client.emit(updateSecretChat(secretChat(id = 7, userId = 42, isOutbound = false, layer = 144)))

            assertEquals(
                SecretChat(
                    id = 7,
                    userId = 42,
                    state = SecretChatState.Ready,
                    isOutbound = false,
                    keyHash = EncryptionKeyHash(ByteArray(36) { it.toByte() }),
                    layer = 144,
                ),
                repository.secretChat(7).first(),
            )
        }

    @Test
    fun `maps every state, and an empty key hash before the key is agreed`() =
        runTest {
            val repository = repository()

            client.emit(
                updateSecretChat(secretChat(id = 1, state = TdApi.SecretChatStatePending(), keyHash = ByteArray(0))),
            )
            client.emit(updateSecretChat(secretChat(id = 2, state = TdApi.SecretChatStateReady())))
            client.emit(updateSecretChat(secretChat(id = 3, state = TdApi.SecretChatStateClosed())))

            val secretChats = repository.secretChats.first()
            assertEquals(
                listOf(SecretChatState.Pending, SecretChatState.Ready, SecretChatState.Closed),
                listOf(1, 2, 3).map { secretChats.getValue(it).state },
            )
            assertTrue(secretChats.getValue(1).keyHash.isEmpty)
        }

    @Test
    fun `a new update replaces the secret chat, an unchanged one keeps the map`() =
        runTest {
            val repository = repository()
            client.emit(updateSecretChat(secretChat(state = TdApi.SecretChatStatePending())))
            val before = repository.secretChats.first()

            client.emit(updateSecretChat(secretChat(state = TdApi.SecretChatStatePending())))
            assertSame(before, repository.secretChats.first())

            client.emit(updateSecretChat(secretChat(state = TdApi.SecretChatStateReady())))
            assertEquals(SecretChatState.Ready, repository.secretChat(7).first()?.state)
        }

    @Test
    fun `logging out forgets every secret chat`() =
        runTest {
            val repository = repository()
            client.emit(updateSecretChat())

            client.emit(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.closed()))

            assertNull(repository.secretChat(7).first())
        }

    @Test
    fun `starts a secret chat with a user and returns its chat`() =
        runTest {
            client.on(
                TdApi.CreateNewSecretChat::class,
                TdResult.Success(chat(id = 900, type = TdApi.ChatTypeSecret(7, 42))),
            )

            assertEquals(Outcome.Success(900L), repository().createSecretChat(42))
            assertEquals(42L, client.sentOf<TdApi.CreateNewSecretChat>().single().userId)
        }

    @Test
    fun `a failure to start a secret chat is a domain error`() =
        runTest {
            client.on(
                TdApi.CreateNewSecretChat::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 8")),
            )

            assertEquals(Outcome.Failure(DomainError.FloodWait(8)), repository().createSecretChat(42))
        }

    @Test
    fun `ends a secret chat`() =
        runTest {
            client.on(TdApi.CloseSecretChat::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository().closeSecretChat(7))
            assertEquals(7, client.sentOf<TdApi.CloseSecretChat>().single().secretChatId)
        }

    @Test
    fun `sets the self-destruct timer as the chat's auto-delete time`() =
        runTest {
            client.on(TdApi.SetChatMessageAutoDeleteTime::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository().setSelfDestructTime(chatId = 900, seconds = 30))
            val sent = client.sentOf<TdApi.SetChatMessageAutoDeleteTime>().single()
            assertEquals(900L to 30, sent.chatId to sent.messageAutoDeleteTime)
        }

    @Test
    fun `a timer TDLib refuses is a domain error`() =
        runTest {
            client.on(
                TdApi.SetChatMessageAutoDeleteTime::class,
                TdResult.Failure(TdError(400, "Invalid TTL specified")),
            )

            assertEquals(
                Outcome.Failure(DomainError.Unknown(400, "Invalid TTL specified")),
                repository().setSelfDestructTime(chatId = 900, seconds = 30),
            )
        }
}

/** Secret chat 7 with user 42 that the current user started, ready, with a 36-byte key hash of 0, 1, 2, …. */
private fun secretChat(
    id: Int = 7,
    userId: Long = 42,
    state: TdApi.SecretChatState = TdApi.SecretChatStateReady(),
    isOutbound: Boolean = true,
    keyHash: ByteArray = ByteArray(36) { it.toByte() },
    layer: Int = 144,
) = TdApi.SecretChat(id, userId, state, isOutbound, keyHash, layer)

private fun updateSecretChat(secretChat: TdApi.SecretChat = secretChat()) = TdApi.UpdateSecretChat(secretChat)
