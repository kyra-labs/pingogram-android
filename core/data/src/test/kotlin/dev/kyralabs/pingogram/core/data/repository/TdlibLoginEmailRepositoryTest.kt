package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.EmailCodeInfo
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TdlibLoginEmailRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibLoginEmailRepository(client)

    private fun passwordState(loginEmailPattern: String) =
        TdResult.Success(TdApi.PasswordState(false, "", false, false, null, loginEmailPattern, 0))

    @Test
    fun `reads the login email's pattern, none when empty`() =
        runTest {
            client.on(TdApi.GetPasswordState::class, passwordState("a****@g****.com"))
            assertEquals(Outcome.Success("a****@g****.com"), repository.address())

            client.on(TdApi.GetPasswordState::class, passwordState(""))
            assertEquals(Outcome.Success(null), repository.address())
        }

    @Test
    fun `sets a new address, which Telegram sends a code to`() =
        runTest {
            client.on(
                TdApi.SetLoginEmailAddress::class,
                TdResult.Success(TdApi.EmailAddressAuthenticationCodeInfo("a****@e****.org", 6)),
            )

            assertEquals(Outcome.Success(EmailCodeInfo("a****@e****.org", 6)), repository.setAddress("ada@example.org"))
            assertEquals("ada@example.org", client.sentOf<TdApi.SetLoginEmailAddress>().single().newLoginEmailAddress)
        }

    @Test
    fun `checks the code, a wrong one failing as such`() =
        runTest {
            client.on(TdApi.CheckLoginEmailAddressCode::class) { request ->
                val code = (request.code as TdApi.EmailAddressAuthenticationCode).code
                if (code == "123456") TdResult.Success(TdApi.Ok()) else TdResult.Failure(TdError(400, "CODE_INVALID"))
            }

            assertEquals(Outcome.Success(Unit), repository.checkCode("123456"))
            assertEquals(Outcome.Failure(DomainError.CodeInvalid), repository.checkCode("000000"))
        }

    @Test
    fun `an address Telegram does not take fails as such`() =
        runTest {
            client.on(TdApi.SetLoginEmailAddress::class, TdResult.Failure(TdError(400, "EMAIL_NOT_ALLOWED")))

            assertEquals(Outcome.Failure(DomainError.EmailNotAllowed), repository.setAddress("ada@example.org"))
        }
}
