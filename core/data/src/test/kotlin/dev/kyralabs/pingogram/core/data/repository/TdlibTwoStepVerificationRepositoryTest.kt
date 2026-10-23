package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PasswordResetResult
import dev.kyralabs.pingogram.core.domain.model.TwoStepVerificationState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Changing 2-step verification; reading its state is covered by [TdlibSessionRepositoryTest]. */
class TdlibTwoStepVerificationRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibTwoStepVerificationRepository(client)

    private fun passwordState(
        hasPassword: Boolean = true,
        hint: String = "",
        hasRecoveryEmail: Boolean = false,
        pendingEmail: String? = null,
    ) = TdApi.PasswordState(
        hasPassword,
        hint,
        hasRecoveryEmail,
        false,
        pendingEmail?.let { TdApi.EmailAddressAuthenticationCodeInfo(it, 6) },
        "",
        0,
    )

    @Test
    fun `checks the current password without changing anything`() =
        runTest {
            client.on(TdApi.GetRecoveryEmailAddress::class, TdResult.Success(TdApi.RecoveryEmailAddress("")))

            assertEquals(Outcome.Success(Unit), repository.checkPassword("secret"))
            assertEquals("secret", client.sentOf<TdApi.GetRecoveryEmailAddress>().single().password)
        }

    @Test
    fun `a wrong current password fails as such`() =
        runTest {
            client.on(TdApi.GetRecoveryEmailAddress::class, TdResult.Failure(TdError(400, "PASSWORD_HASH_INVALID")))

            assertEquals(Outcome.Failure(DomainError.PasswordInvalid), repository.checkPassword("wrong"))
        }

    @Test
    fun `sets a first password with its hint and recovery email`() =
        runTest {
            client.on(
                TdApi.SetPassword::class,
                TdResult.Success(passwordState(hasPassword = false, pendingEmail = "a***@e***.com")),
            )

            val result = repository.setPassword(null, "new", "hint", recoveryEmail = "a@example.com")

            assertEquals(
                Outcome.Success(
                    TwoStepVerificationState(
                        hasPassword = false,
                        hasRecoveryEmail = false,
                        pendingRecoveryEmailPattern = "a***@e***.com",
                    ),
                ),
                result,
            )
            val sent = client.sentOf<TdApi.SetPassword>().single()
            assertEquals("", sent.oldPassword)
            assertEquals("new", sent.newPassword)
            assertEquals("hint", sent.newHint)
            assertTrue(sent.setRecoveryEmailAddress)
            assertEquals("a@example.com", sent.newRecoveryEmailAddress)
        }

    @Test
    fun `changing the password keeps the recovery email`() =
        runTest {
            client.on(TdApi.SetPassword::class, TdResult.Success(passwordState(hint = "pet", hasRecoveryEmail = true)))

            val result = repository.setPassword("old", "new", "pet")

            assertEquals(
                Outcome.Success(TwoStepVerificationState(true, true, passwordHint = "pet")),
                result,
            )
            val sent = client.sentOf<TdApi.SetPassword>().single()
            assertEquals("old", sent.oldPassword)
            assertFalse(sent.setRecoveryEmailAddress)
        }

    @Test
    fun `removing the password clears it and its recovery email`() =
        runTest {
            client.on(TdApi.SetPassword::class, TdResult.Success(passwordState(hasPassword = false)))

            assertEquals(
                Outcome.Success(TwoStepVerificationState(hasPassword = false, hasRecoveryEmail = false)),
                repository.removePassword("old"),
            )
            val sent = client.sentOf<TdApi.SetPassword>().single()
            assertEquals("old", sent.oldPassword)
            assertEquals("", sent.newPassword)
            assertTrue(sent.setRecoveryEmailAddress)
            assertEquals("", sent.newRecoveryEmailAddress)
        }

    @Test
    fun `sets confirms resends and cancels a recovery email`() =
        runTest {
            val pending = passwordState(pendingEmail = "a***@e***.com")
            client.on(TdApi.SetRecoveryEmailAddress::class, TdResult.Success(pending))
            client.on(TdApi.ResendRecoveryEmailAddressCode::class, TdResult.Success(pending))
            client.on(
                TdApi.CheckRecoveryEmailAddressCode::class,
                TdResult.Success(passwordState(hasRecoveryEmail = true)),
            )
            client.on(TdApi.CancelRecoveryEmailAddressVerification::class, TdResult.Success(passwordState()))

            assertEquals(
                "a***@e***.com",
                (
                    repository.setRecoveryEmail(
                        "pw",
                        "a@example.com",
                    ) as Outcome.Success
                ).value.pendingRecoveryEmailPattern,
            )
            assertEquals(
                "a***@e***.com",
                (repository.resendRecoveryEmailCode() as Outcome.Success).value.pendingRecoveryEmailPattern,
            )
            assertTrue((repository.confirmRecoveryEmail("123456") as Outcome.Success).value.hasRecoveryEmail)
            assertEquals(Outcome.Success(TwoStepVerificationState(true, false)), repository.cancelRecoveryEmail())
            assertEquals("pw", client.sentOf<TdApi.SetRecoveryEmailAddress>().single().password)
            assertEquals("123456", client.sentOf<TdApi.CheckRecoveryEmailAddressCode>().single().code)
        }

    @Test
    fun `a wrong confirmation code fails as such`() =
        runTest {
            client.on(TdApi.CheckRecoveryEmailAddressCode::class, TdResult.Failure(TdError(400, "CODE_INVALID")))

            assertEquals(Outcome.Failure(DomainError.CodeInvalid), repository.confirmRecoveryEmail("000000"))
        }

    @Test
    fun `recovers a forgotten password by email`() =
        runTest {
            client.on(
                TdApi.RequestPasswordRecovery::class,
                TdResult.Success(TdApi.EmailAddressAuthenticationCodeInfo("a***@e***.com", 6)),
            )
            client.on(TdApi.CheckPasswordRecoveryCode::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.RecoverPassword::class, TdResult.Success(passwordState(hasRecoveryEmail = true)))

            assertEquals(Outcome.Success("a***@e***.com"), repository.requestRecovery())
            assertEquals(Outcome.Success(Unit), repository.checkRecoveryCode("111111"))
            assertEquals(
                Outcome.Success(TwoStepVerificationState(true, true)),
                repository.recoverPassword("111111", "new", "hint"),
            )
            val sent = client.sentOf<TdApi.RecoverPassword>().single()
            assertEquals(listOf("111111", "new", "hint"), listOf(sent.recoveryCode, sent.newPassword, sent.newHint))
        }

    @Test
    fun `recovery without a recovery email is unavailable`() =
        runTest {
            client.on(TdApi.RequestPasswordRecovery::class, TdResult.Failure(TdError(400, "PASSWORD_RECOVERY_NA")))

            assertEquals(Outcome.Failure(DomainError.PasswordRecoveryUnavailable), repository.requestRecovery())
        }

    @Test
    fun `maps each answer to a reset`() =
        runTest {
            val answers =
                listOf(
                    TdApi.ResetPasswordResultOk() to PasswordResetResult.Done,
                    TdApi.ResetPasswordResultPending(1_700_000_000) to PasswordResetResult.Pending(1_700_000_000),
                    TdApi.ResetPasswordResultDeclined(1_800_000_000) to PasswordResetResult.Declined(1_800_000_000),
                )
            answers.forEach { (answer, expected) ->
                client.on(TdApi.ResetPassword::class, TdResult.Success(answer))
                assertEquals(Outcome.Success(expected), repository.resetPassword())
            }
        }

    @Test
    fun `cancels a waiting reset`() =
        runTest {
            client.on(TdApi.CancelPasswordReset::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.cancelReset())
        }

    @Test
    fun `a password set too recently asks to wait`() =
        runTest {
            client.on(TdApi.SetPassword::class, TdResult.Failure(TdError(400, "PASSWORD_TOO_FRESH_600")))

            assertEquals(Outcome.Failure(DomainError.FloodWait(600)), repository.setPassword("old", "new", ""))
        }
}
