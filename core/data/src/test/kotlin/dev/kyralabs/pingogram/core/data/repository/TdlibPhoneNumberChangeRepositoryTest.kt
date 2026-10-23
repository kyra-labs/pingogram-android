package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AuthCodeType
import dev.kyralabs.pingogram.core.domain.model.LoginCodeInfo
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TdlibPhoneNumberChangeRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibPhoneNumberChangeRepository(client)

    private val sms =
        TdApi.AuthenticationCodeInfo(
            "+44 7911 123456",
            TdApi.AuthenticationCodeTypeSms(5),
            TdApi.AuthenticationCodeTypeCall(5),
            60,
        )

    @Test
    fun `sends a code to the new number for a change, without flash or missed calls`() =
        runTest {
            client.on(TdApi.SendPhoneNumberCode::class, TdResult.Success(sms))

            val sent = repository.sendCode("+447911123456")

            assertEquals(
                Outcome.Success(LoginCodeInfo("+44 7911 123456", AuthCodeType.Sms(5), AuthCodeType.Call(5), 60)),
                sent,
            )
            val request = client.sentOf<TdApi.SendPhoneNumberCode>().single()
            assertEquals("+447911123456", request.phoneNumber)
            assertTrue(request.type is TdApi.PhoneNumberCodeTypeChange)
            assertFalse(request.settings.allowFlashCall || request.settings.allowMissedCall)
        }

    @Test
    fun `a number another account has fails as occupied`() =
        runTest {
            client.on(TdApi.SendPhoneNumberCode::class, TdResult.Failure(TdError(400, "PHONE_NUMBER_OCCUPIED")))

            assertEquals(Outcome.Failure(DomainError.PhoneNumberOccupied), repository.sendCode("+447911123456"))
        }

    @Test
    fun `asks for the code again at the user's request`() =
        runTest {
            client.on(TdApi.ResendPhoneNumberCode::class, TdResult.Success(sms))

            val resent = repository.resendCode()

            assertEquals(AuthCodeType.Sms(5), (resent as Outcome.Success).value.type)
            assertTrue(
                client.sentOf<TdApi.ResendPhoneNumberCode>().single().reason is TdApi.ResendCodeReasonUserRequest,
            )
        }

    @Test
    fun `checks the code, a wrong one failing as such`() =
        runTest {
            client.on(TdApi.CheckPhoneNumberCode::class) { request ->
                if (request.code == "12345") {
                    TdResult.Success(TdApi.Ok())
                } else {
                    TdResult.Failure(TdError(400, "PHONE_CODE_INVALID"))
                }
            }

            assertEquals(Outcome.Success(Unit), repository.checkCode("12345"))
            assertEquals(Outcome.Failure(DomainError.CodeInvalid), repository.checkCode("00000"))
        }
}
