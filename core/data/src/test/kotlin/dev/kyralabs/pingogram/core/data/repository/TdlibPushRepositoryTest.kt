package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ActiveNotifications
import dev.kyralabs.pingogram.core.domain.repository.NotificationRepository
import dev.kyralabs.pingogram.core.domain.repository.PushTokenSource
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.time.Duration.Companion.seconds

class TdlibPushRepositoryTest {
    private val client = FakeTdlibClient()
    private val tokenSource = FakePushTokenSource()
    private val notifications = FakeNotificationRepository()

    private fun TestScope.repository(source: PushTokenSource? = tokenSource) =
        TdlibPushRepository(client, backgroundScope, notifications, Optional.ofNullable(source))

    private fun registered(): List<Pair<String, Boolean>> =
        client.sentOf<TdApi.RegisterDevice>().map { function ->
            val token = function.deviceToken as TdApi.DeviceTokenFirebaseCloudMessaging
            assertEquals(0, function.otherUserIds.size)
            token.token to token.encrypt
        }

    private fun registerAnswers(vararg results: TdResult<TdApi.PushReceiverId>) {
        val queue = ArrayDeque(results.toList())
        client.on(TdApi.RegisterDevice::class) {
            queue.removeFirstOrNull() ?: TdResult.Success(TdApi.PushReceiverId(1))
        }
    }

    @Test
    fun `without a push service nothing is registered or deleted`() =
        runTest {
            registerAnswers()
            val repository = repository(source = null)

            repository.start()
            repository.onNewToken("token")
            client.authState.value = AuthState.Ready
            runCurrent()
            client.authState.value = AuthState.LoggingOut
            runCurrent()

            assertEquals(emptyList<Pair<String, Boolean>>(), registered())
            assertEquals(0, tokenSource.tokenRequests)
        }

    @Test
    fun `registers the push service's token, encrypted, once logged in`() =
        runTest {
            registerAnswers()
            tokenSource.current = "token-1"
            repository().start()
            runCurrent()
            assertEquals(emptyList<Pair<String, Boolean>>(), registered())
            assertEquals(0, tokenSource.tokenRequests)

            client.authState.value = AuthState.Ready
            runCurrent()

            assertEquals(listOf("token-1" to true), registered())
        }

    @Test
    fun `a new token is registered at once while logged in`() =
        runTest {
            registerAnswers()
            tokenSource.current = "token-1"
            val repository = repository()
            repository.start()
            client.authState.value = AuthState.Ready
            runCurrent()

            repository.onNewToken("token-2")
            runCurrent()

            assertEquals(listOf("token-1" to true, "token-2" to true), registered())
        }

    @Test
    fun `a token announced while logged out waits for the login`() =
        runTest {
            registerAnswers()
            tokenSource.current = null
            val repository = repository()
            repository.start()
            client.authState.value = AuthState.WaitPhoneNumber
            repository.onNewToken("token-2")
            runCurrent()
            assertEquals(emptyList<Pair<String, Boolean>>(), registered())

            client.authState.value = AuthState.Ready
            runCurrent()

            assertEquals(listOf("token-2" to true), registered())
        }

    @Test
    fun `logging out deletes the token, and the next login registers the new one`() =
        runTest {
            registerAnswers()
            tokenSource.current = "token-1"
            repository().start()
            client.authState.value = AuthState.Ready
            runCurrent()

            client.authState.value = AuthState.LoggingOut
            runCurrent()
            assertEquals(1, tokenSource.deletions)

            tokenSource.current = "token-2"
            client.authState.value = AuthState.WaitPhoneNumber
            runCurrent()
            client.authState.value = AuthState.Ready
            runCurrent()

            assertEquals(1, tokenSource.deletions)
            assertEquals(listOf("token-1" to true, "token-2" to true), registered())
        }

    @Test
    fun `a start while logged out deletes nothing`() =
        runTest {
            repository().start()
            client.authState.value = AuthState.WaitPhoneNumber
            runCurrent()
            client.authState.value = AuthState.Closed
            runCurrent()

            assertEquals(0, tokenSource.deletions)
        }

    @Test
    fun `starting twice follows the login once`() =
        runTest {
            registerAnswers()
            tokenSource.current = "token-1"
            val repository = repository()
            repository.start()
            repository.start()
            client.authState.value = AuthState.Ready
            runCurrent()

            assertEquals(1, registered().size)
        }

    @Test
    fun `a flood wait is waited out, then the token is registered again`() =
        runTest {
            registerAnswers(TdResult.Failure(TdError(429, "Too Many Requests: retry after 30")))
            tokenSource.current = "token-1"
            repository().start()
            client.authState.value = AuthState.Ready
            runCurrent()
            assertEquals(1, registered().size)

            advanceTimeBy(29.seconds)
            runCurrent()
            assertEquals(1, registered().size)
            advanceTimeBy(2.seconds)
            runCurrent()

            assertEquals(2, registered().size)
        }

    @Test
    fun `a logout stops waiting out a flood wait`() =
        runTest {
            registerAnswers(TdResult.Failure(TdError(429, "Too Many Requests: retry after 30")))
            tokenSource.current = "token-1"
            repository().start()
            client.authState.value = AuthState.Ready
            runCurrent()

            client.authState.value = AuthState.LoggingOut
            advanceTimeBy(60.seconds)
            runCurrent()

            assertEquals(1, registered().size)
        }

    @Test
    fun `other failures are not retried`() =
        runTest {
            registerAnswers(TdResult.Failure(TdError(400, "DEVICE_TOKEN_INVALID")))
            tokenSource.current = "token-1"
            repository().start()
            client.authState.value = AuthState.Ready
            runCurrent()
            advanceTimeBy(3600.seconds)

            assertEquals(1, registered().size)
        }

    @Test
    fun `a push goes to TDLib and waits for held-back notifications`() =
        runTest {
            client.on(TdApi.ProcessPushNotification::class, TdResult.Success(TdApi.Ok()))
            notifications.pending.value = true
            val repository = repository()

            val result = async { repository.processPush("""{"p":"abc"}""") }
            runCurrent()
            assertEquals(listOf("""{"p":"abc"}"""), client.sentOf<TdApi.ProcessPushNotification>().map { it.payload })
            assertFalse(result.isCompleted)

            notifications.pending.value = false
            runCurrent()

            assertEquals(Outcome.Success(Unit), result.await())
        }

    @Test
    fun `a push TDLib cannot read without connecting waits for the connection`() =
        runTest {
            client.on(TdApi.ProcessPushNotification::class, TdResult.Failure(TdError(406, "")))
            val repository = repository()

            val result = async { repository.processPush("{}") }
            runCurrent()
            assertFalse(result.isCompleted)

            client.connectionState.value = ConnectionState.Ready
            runCurrent()

            assertEquals(Outcome.Failure(DomainError.Silent), result.await())
        }

    @Test
    fun `a push returns within the time a push service gives, whatever TDLib does`() =
        runTest {
            val never = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.ProcessPushNotification::class) { never.await() }
            val repository = repository()

            val result = async { repository.processPush("{}") }
            advanceTimeBy(TdlibPushRepository.PUSH_TIMEOUT + 1.seconds)

            assertTrue(result.isCompleted)
            assertEquals(Outcome.Failure(DomainError.Network), result.await())
        }

    @Test
    fun `waiting after a push is bounded too`() =
        runTest {
            client.on(TdApi.ProcessPushNotification::class, TdResult.Success(TdApi.Ok()))
            notifications.pending.value = true
            val repository = repository()

            val result = async { repository.processPush("{}") }
            advanceTimeBy(TdlibPushRepository.SETTLE_TIMEOUT + 1.seconds)

            assertTrue(result.isCompleted)
            assertEquals(Outcome.Success(Unit), result.await())
        }

    private class FakePushTokenSource : PushTokenSource {
        var current: String? = null
        var tokenRequests = 0
        var deletions = 0

        override suspend fun token(): String? {
            tokenRequests++
            return current
        }

        override suspend fun deleteToken() {
            deletions++
        }
    }

    private class FakeNotificationRepository : NotificationRepository {
        val pending = MutableStateFlow(false)

        override val notifications: Flow<ActiveNotifications> = emptyFlow()
        override val hasPendingNotifications: Flow<Boolean> = pending

        override suspend fun reply(
            chatId: Long,
            text: String,
            replyToMessageId: Long?,
        ): Outcome<Unit> = error("not used")

        override suspend fun markAsRead(
            chatId: Long,
            messageIds: List<Long>,
        ): Outcome<Unit> = error("not used")

        override suspend fun dismiss(
            groupId: Int,
            maxNotificationId: Int,
        ): Outcome<Unit> = error("not used")
    }
}
