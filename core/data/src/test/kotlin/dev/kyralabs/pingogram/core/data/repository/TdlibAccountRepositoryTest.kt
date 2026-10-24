package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TdlibAccountRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibAccountRepository(client)

    @Test
    fun `reads how many days the account may stay unused`() =
        runTest {
            client.on(TdApi.GetAccountTtl::class, TdResult.Success(TdApi.AccountTtl(180)))

            assertEquals(Outcome.Success(180), repository.accountTtl())
        }

    @Test
    fun `sets how many days the account may stay unused`() =
        runTest {
            client.on(TdApi.SetAccountTtl::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.setAccountTtl(365))
            assertEquals(
                365,
                client
                    .sentOf<TdApi.SetAccountTtl>()
                    .single()
                    .ttl.days,
            )
        }

    @Test
    fun `deletes the account with the reason and the password`() =
        runTest {
            client.on(TdApi.DeleteAccount::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.deleteAccount(reason = "Moving on", password = "secret"))
            val sent = client.sentOf<TdApi.DeleteAccount>().single()
            assertEquals("Moving on" to "secret", sent.reason to sent.password)
        }

    @Test
    fun `reads and sets the self-destruct timer of new chats`() =
        runTest {
            client.on(
                TdApi.GetDefaultMessageAutoDeleteTime::class,
                TdResult.Success(TdApi.MessageAutoDeleteTime(86_400)),
            )
            client.on(TdApi.SetDefaultMessageAutoDeleteTime::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(86_400), repository.defaultMessageAutoDeleteTime())
            assertEquals(Outcome.Success(Unit), repository.setDefaultMessageAutoDeleteTime(604_800))
            assertEquals(
                604_800,
                client
                    .sentOf<TdApi.SetDefaultMessageAutoDeleteTime>()
                    .single()
                    .messageAutoDeleteTime.time,
            )
        }

    @Test
    fun `a wrong password fails as such`() =
        runTest {
            client.on(TdApi.DeleteAccount::class, TdResult.Failure(TdError(400, "PASSWORD_HASH_INVALID")))

            assertEquals(Outcome.Failure(DomainError.PasswordInvalid), repository.deleteAccount("", "wrong"))
        }

    @Test
    fun `a flood wait fails with its seconds`() =
        runTest {
            client.on(TdApi.DeleteAccount::class, TdResult.Failure(TdError(429, "FLOOD_WAIT_30")))

            assertEquals(Outcome.Failure(DomainError.FloodWait(30)), repository.deleteAccount("", ""))
        }
}
