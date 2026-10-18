package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.mapper.toUser
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.myIdOption
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateUser
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateUserStatus
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TdlibUserRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibUserRepository(client, backgroundScope)

    @Test
    fun `knows no user before TDLib announces one`() =
        runTest {
            assertNull(repository().user(1).first())
        }

    @Test
    fun `caches a user announced right after it was created, before TDLib could start`() =
        runTest {
            val repository = repository()

            // No suspension in between: an update without a subscriber is lost, as it would be from TDLib.
            client.emit(updateUser(user(id = 1, firstName = "Ada")))

            assertEquals("Ada", repository.user(1).first()?.firstName)
        }

    @Test
    fun `a new updateUser replaces the cached user`() =
        runTest {
            val repository = repository()
            client.emit(updateUser(user(id = 1, firstName = "Ada")))

            client.emit(updateUser(user(id = 1, firstName = "Grace")))

            assertEquals("Grace", repository.user(1).first()?.firstName)
        }

    @Test
    fun `updateUserStatus changes the status of a cached user`() =
        runTest {
            val repository = repository()
            client.emit(updateUser(user(id = 1, status = TdApi.UserStatusRecently(false))))

            client.emit(updateUserStatus(userId = 1, status = TdApiFixtures.onlineStatus(expires = 1_000)))

            assertEquals(UserStatus.Online(expiresAtEpochSeconds = 1_000), repository.user(1).first()?.status)
        }

    @Test
    fun `updateUserStatus of an unknown user is ignored`() =
        runTest {
            val repository = repository()

            client.emit(updateUserStatus(userId = 2, status = TdApiFixtures.onlineStatus(expires = 1_000)))

            assertNull(repository.user(2).first())
        }

    @Test
    fun `user emits only changes of that user`() =
        runTest {
            val repository = repository()
            client.emit(updateUser(user(id = 1)))

            repository.user(1).test {
                awaitItem()

                client.emit(updateUser(user(id = 2)))
                client.emit(updateUserStatus(userId = 2, status = TdApiFixtures.offlineStatus(wasOnline = 900)))
                client.emit(updateUser(user(id = 1)))

                expectNoEvents()
            }
        }

    @Test
    fun `getUser answers from the cache without a request`() =
        runTest {
            val repository = repository()
            val user = user(id = 1)
            client.emit(updateUser(user))

            assertEquals(Outcome.Success(user.toUser()), repository.getUser(1))
            assertEquals(emptyList<TdApi.Function<*>>(), client.sent)
        }

    @Test
    fun `getUser asks TDLib for a user it has not announced`() =
        runTest {
            client.on(TdApi.GetUser::class) { request -> TdResult.Success(user(id = request.userId)) }

            val result = repository().getUser(5)

            assertEquals(5L, client.sentOf<TdApi.GetUser>().single().userId)
            assertEquals(Outcome.Success(user(id = 5).toUser()), result)
        }

    @Test
    fun `getUser maps a user TDLib does not know to NotFound`() =
        runTest {
            client.on(TdApi.GetUser::class, TdResult.Failure(TdError(400, "User not found")))

            assertEquals(Outcome.Failure(DomainError.NotFound), repository().getUser(5))
        }

    @Test
    fun `getUser maps a flood wait`() =
        runTest {
            val floodWait = TdApiFixtures.floodWait(seconds = 30)
            client.on(TdApi.GetUser::class, TdResult.Failure(TdError(floodWait.code, floodWait.message)))

            assertEquals(Outcome.Failure(DomainError.FloodWait(30)), repository().getUser(5))
        }

    @Test
    fun `getUser fails with Network when TDLib does not answer in time`() =
        runTest {
            val never = CompletableDeferred<TdResult<TdApi.User>>()
            client.on(TdApi.GetUser::class) { never.await() }

            // runTest skips the 30 s timeout in virtual time.
            assertEquals(Outcome.Failure(DomainError.Network), repository().getUser(5))
        }

    @Test
    fun `myUserId follows the my_id option`() =
        runTest {
            val repository = repository()

            repository.myUserId.test {
                assertNull(awaitItem())

                client.emit(myIdOption(42))

                assertEquals(42L, awaitItem())
            }
        }

    @Test
    fun `other options leave myUserId alone`() =
        runTest {
            val repository = repository()

            // myUserId is derived in a coroutine of its own, so collect it from the start.
            repository.myUserId.test {
                skipItems(1)
                client.emit(myIdOption(42))
                assertEquals(42L, awaitItem())

                client.emit(TdApi.UpdateOption("version", TdApi.OptionValueString("1.8.68")))

                expectNoEvents()
            }
        }

    @Test
    fun `forgets every user when TDLib closes`() =
        runTest {
            val repository = repository()
            client.emit(updateUser(user(id = 1)))

            client.emitAuthorizationState(TdApiFixtures.closed())

            assertNull(repository.user(1).first())
        }

    @Test
    fun `forgets the logged-in user when TDLib closes`() =
        runTest {
            val repository = repository()

            repository.myUserId.test {
                skipItems(1)
                client.emit(myIdOption(42))
                assertEquals(42L, awaitItem())

                client.emitAuthorizationState(TdApiFixtures.closed())

                assertNull(awaitItem())
            }
        }
}
