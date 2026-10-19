package dev.kyralabs.pingogram.core.data.error

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class TdErrorMappingTest {
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("errors")
    fun `maps TDLib errors to domain errors`(
        code: Int,
        message: String,
        expected: DomainError,
    ) {
        assertEquals(expected, TdError(code, message).toDomainError())
    }

    @Test
    fun `toOutcome keeps the value of a success`() {
        assertEquals(Outcome.Success("ok"), TdResult.Success("ok").toOutcome())
    }

    @Test
    fun `toOutcome maps the error of a failure`() {
        assertEquals(
            Outcome.Failure(DomainError.CodeInvalid),
            TdResult.Failure(TdError(400, "PHONE_CODE_INVALID")).toOutcome(),
        )
    }

    companion object {
        @JvmStatic
        fun errors(): List<Arguments> =
            listOf(
                Arguments.of(429, "Too Many Requests: retry after 42", DomainError.FloodWait(42)),
                Arguments.of(420, "FLOOD_WAIT_7", DomainError.FloodWait(7)),
                Arguments.of(400, "PHONE_NUMBER_INVALID", DomainError.PhoneNumberInvalid),
                Arguments.of(400, "PHONE_NUMBER_EMPTY", DomainError.PhoneNumberInvalid),
                Arguments.of(400, "Phone number must be non-empty", DomainError.PhoneNumberInvalid),
                Arguments.of(400, "PHONE_NUMBER_BANNED", DomainError.PhoneNumberBanned),
                Arguments.of(400, "PHONE_NUMBER_FLOOD", DomainError.PhoneNumberFlood),
                Arguments.of(400, "PHONE_CODE_INVALID", DomainError.CodeInvalid),
                Arguments.of(400, "PHONE_CODE_EMPTY", DomainError.CodeInvalid),
                Arguments.of(400, "CODE_INVALID", DomainError.CodeInvalid),
                Arguments.of(400, "EMAIL_CODE_INVALID", DomainError.CodeInvalid),
                Arguments.of(400, "Code must be non-empty", DomainError.CodeInvalid),
                Arguments.of(400, "Invalid recovery code", DomainError.CodeInvalid),
                Arguments.of(400, "PHONE_CODE_EXPIRED", DomainError.CodeExpired),
                Arguments.of(400, "CODE_EXPIRED", DomainError.CodeExpired),
                Arguments.of(400, "EMAIL_CODE_EXPIRED", DomainError.CodeExpired),
                Arguments.of(400, "EMAIL_VERIFY_EXPIRED", DomainError.CodeExpired),
                Arguments.of(400, "PASSWORD_HASH_INVALID", DomainError.PasswordInvalid),
                Arguments.of(400, "FIRSTNAME_INVALID", DomainError.FirstNameInvalid),
                Arguments.of(400, "First name must be non-empty", DomainError.FirstNameInvalid),
                Arguments.of(400, "LASTNAME_INVALID", DomainError.LastNameInvalid),
                Arguments.of(400, "EMAIL_INVALID", DomainError.EmailInvalid),
                Arguments.of(400, "Email address must be non-empty", DomainError.EmailInvalid),
                Arguments.of(400, "EMAIL_NOT_ALLOWED", DomainError.EmailNotAllowed),
                Arguments.of(400, "PASSWORD_RECOVERY_NA", DomainError.PasswordRecoveryUnavailable),
                Arguments.of(400, "PASSWORD_RECOVERY_EXPIRED", DomainError.PasswordRecoveryExpired),
                Arguments.of(400, "TASK_ALREADY_EXISTS", DomainError.AlreadyRequested),
                Arguments.of(400, "UPDATE_APP_TO_LOGIN", DomainError.AppUpdateRequired),
                Arguments.of(400, "Application update is needed to log in", DomainError.AppUpdateRequired),
                Arguments.of(400, "The maximum number of pinned chats exceeded", DomainError.TooManyPinnedChats),
                Arguments.of(403, "PREMIUM_ACCOUNT_REQUIRED", DomainError.PremiumRequired),
                // td_api.tl: the message of a 406 is not processed, even when it names a known error.
                Arguments.of(406, "UPDATE_APP_TO_LOGIN", DomainError.Silent),
                Arguments.of(406, "PHONE_NUMBER_INVALID", DomainError.Silent),
                Arguments.of(406, "PHONE_PASSWORD_FLOOD", DomainError.Silent),
                Arguments.of(406, "AUTH_KEY_DUPLICATED", DomainError.Silent),
                Arguments.of(401, "Unauthorized", DomainError.Unauthorized),
                Arguments.of(401, "SESSION_PASSWORD_NEEDED", DomainError.Unauthorized),
                Arguments.of(404, "Not Found", DomainError.NotFound),
                Arguments.of(400, "User not found", DomainError.NotFound),
                Arguments.of(400, "Chat info not found", DomainError.NotFound),
                Arguments.of(400, "Message not found by the filter", DomainError.NotFound),
                Arguments.of(500, "Network is unreachable", DomainError.Network),
                Arguments.of(500, "Request aborted", DomainError.Unknown(500, "Request aborted")),
                Arguments.of(
                    400,
                    "Call to checkAuthenticationCode unexpected",
                    DomainError.Unknown(400, "Call to checkAuthenticationCode unexpected"),
                ),
                Arguments.of(400, "SOME_NEW_ERROR", DomainError.Unknown(400, "SOME_NEW_ERROR")),
            ) + profileErrors()

        /** Editing the profile: usernames, the bio and photos. */
        private fun profileErrors(): List<Arguments> =
            listOf(
                Arguments.of(400, "USERNAME_OCCUPIED", DomainError.UsernameTaken),
                Arguments.of(400, "USERNAME_INVALID", DomainError.UsernameInvalid),
                Arguments.of(400, "Username is invalid", DomainError.UsernameInvalid),
                Arguments.of(400, "USERNAME_PURCHASE_AVAILABLE", DomainError.UsernameForSale),
                Arguments.of(400, "ABOUT_TOO_LONG", DomainError.BioTooLong),
                Arguments.of(400, "PHOTO_CROP_SIZE_SMALL", DomainError.PhotoInvalid),
                Arguments.of(400, "PHOTO_INVALID_DIMENSIONS", DomainError.PhotoInvalid),
                Arguments.of(400, "PHOTO_EXT_INVALID", DomainError.PhotoInvalid),
                Arguments.of(400, "PHOTO_INVALID", DomainError.PhotoInvalid),
                Arguments.of(400, "IMAGE_PROCESS_FAILED", DomainError.PhotoInvalid),
            )
    }
}
