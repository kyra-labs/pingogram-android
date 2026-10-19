package dev.kyralabs.pingogram.feature.auth.ui.preview

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AuthCodeType
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.codeLength
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.feature.auth.ui.AuthAction
import dev.kyralabs.pingogram.feature.auth.ui.AuthScreen
import dev.kyralabs.pingogram.feature.auth.ui.AuthStepUiState
import dev.kyralabs.pingogram.feature.auth.ui.AuthUiState
import dev.kyralabs.pingogram.feature.auth.ui.CountryPickerUiState
import dev.kyralabs.pingogram.feature.auth.ui.CountryUiModel
import dev.kyralabs.pingogram.feature.auth.ui.EmailResetUiState
import kotlinx.collections.immutable.persistentListOf

/**
 * Sample states of every login screen, shared by the previews and the screenshot tests ([all]): each state here has a
 * preview next to its screen and goldens in light, dark and dynamic color.
 */
internal object AuthPreviewData {
    val unitedKingdom = CountryUiModel(isoCode = "GB", name = "United Kingdom", flagEmoji = "🇬🇧", callingCode = "44")

    val countries =
        persistentListOf(
            CountryUiModel("CA", "Canada", "🇨🇦", "1"),
            CountryUiModel("DE", "Germany", "🇩🇪", "49"),
            unitedKingdom,
            CountryUiModel("US", "United States", "🇺🇸", "1"),
        )

    const val CODE_LENGTH = 5
    private const val RESEND_SECONDS = 42L
    private const val FLOOD_WAIT_SECONDS = 299L
    private const val EMAIL_CODE_LENGTH = 6
    private const val RESET_WAIT_SECONDS = 604_800L
    private const val RESET_PENDING_SECONDS = 3_600L
    private const val MIN_USER_AGE = 16

    fun state(
        step: AuthStepUiState,
        connection: ConnectionStatus = ConnectionStatus.Ready,
        floodWaitSeconds: Long? = null,
        pendingAction: AuthAction? = null,
        canGoBack: Boolean = true,
    ) = AuthUiState(step, connection, floodWaitSeconds, pendingAction, canGoBack)

    fun phoneNumber(
        number: String = "7911123456",
        error: DomainError? = null,
        isChangingNumber: Boolean = false,
        pickerOpen: Boolean = false,
    ) = AuthStepUiState.PhoneNumber(
        callingCode = "44",
        number = number,
        numberFormat = "---- ------",
        country = unitedKingdom,
        error = error,
        isChangingNumber = isChangingNumber,
        countryPicker = if (pickerOpen) CountryPickerUiState("", countries) else null,
    )

    fun code(
        type: AuthCodeType = AuthCodeType.Sms(length = CODE_LENGTH),
        nextType: AuthCodeType? = AuthCodeType.Call(length = CODE_LENGTH),
        code: String = "",
        resendInSeconds: Long = RESEND_SECONDS,
        error: DomainError? = null,
    ) = AuthStepUiState.Code(
        phoneNumber = "+44 7911 123456",
        type = type,
        nextType = nextType,
        code = code,
        codeLength = type.codeLength,
        resendInSeconds = resendInSeconds,
        error = error,
    )

    fun registration(
        firstName: String = "Ada",
        firstNameError: DomainError? = null,
        termsOpen: Boolean = false,
    ) = AuthStepUiState.Registration(
        firstName = firstName,
        lastName = "",
        firstNameError = firstNameError,
        lastNameError = null,
        termsOfServiceText =
            "By signing up for Telegram, you accept our Privacy Policy and agree not to use Telegram to spam, " +
                "promote violence or post illegal content.",
        minUserAge = MIN_USER_AGE,
        termsAccepted = !termsOpen,
        isTermsOfServiceOpen = termsOpen,
    )

    val loading = state(AuthStepUiState.Loading, connection = ConnectionStatus.Connecting, canGoBack = false)
    val loggingOut = state(AuthStepUiState.LoggingOut, canGoBack = false)
    val welcome = state(AuthStepUiState.Welcome, canGoBack = false)

    val phoneNumber = state(phoneNumber())
    val phoneNumberEmpty =
        state(AuthStepUiState.PhoneNumber("", "", null, null, null, isChangingNumber = false, countryPicker = null))
    val phoneNumberError = state(phoneNumber(number = "791", error = DomainError.PhoneNumberInvalid))
    val phoneNumberFloodWait = state(phoneNumber(), floodWaitSeconds = FLOOD_WAIT_SECONDS)
    val phoneNumberSubmittingOffline =
        state(
            phoneNumber(isChangingNumber = true),
            connection = ConnectionStatus.WaitingForNetwork,
            pendingAction = AuthAction.SubmitPhoneNumber,
        )
    val countryPicker = state(phoneNumber(pickerOpen = true))

    val qrCode = state(AuthStepUiState.QrCode("tg://login?token=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA"))

    val code = state(code(code = "123"))
    val codeResend =
        state(code(type = AuthCodeType.TelegramMessage(length = CODE_LENGTH), resendInSeconds = 0))
    val codeError = state(code(code = "12345", error = DomainError.CodeInvalid))
    val codeFragment = state(code(type = AuthCodeType.Fragment("https://fragment.com", length = CODE_LENGTH)))
    val codePhrase = state(code(type = AuthCodeType.SmsPhrase(firstWord = "apple"), nextType = null))

    val password = state(AuthStepUiState.Password("my pet", "secret", null, showNoRecoveryEmailInfo = false))
    val passwordError =
        state(AuthStepUiState.Password("", "wrong", DomainError.PasswordInvalid, showNoRecoveryEmailInfo = false))
    val passwordNoRecoveryEmail =
        state(AuthStepUiState.Password("my pet", "", null, showNoRecoveryEmailInfo = true))
    val passwordRecoveryCode = state(AuthStepUiState.PasswordRecoveryCode("a***@example.org", "123", null))
    val newPassword = state(AuthStepUiState.NewPassword("new secret", "new secre", ""))

    val registration = state(registration())
    val registrationError = state(registration(firstName = "", firstNameError = DomainError.FirstNameInvalid))
    val registrationTerms = state(registration(termsOpen = true))

    val emailAddress = state(AuthStepUiState.EmailAddress("ada@example", DomainError.EmailInvalid, false))
    val emailCode =
        state(
            AuthStepUiState.EmailCode(
                emailAddressPattern = "a***@example.org",
                code = "12",
                codeLength = EMAIL_CODE_LENGTH,
                error = null,
                reset = EmailResetUiState.Available(waitSeconds = RESET_WAIT_SECONDS),
            ),
        )

    // An address entered during this login: its code can be sent again.
    val emailCodeNewAddress =
        state(
            AuthStepUiState.EmailCode(
                emailAddressPattern = "a***@example.org",
                code = "",
                codeLength = EMAIL_CODE_LENGTH,
                error = null,
                reset = null,
                canResendCode = true,
            ),
        )
    val emailCodeResetPending =
        state(
            AuthStepUiState.EmailCode(
                emailAddressPattern = "a***@example.org",
                code = "",
                codeLength = EMAIL_CODE_LENGTH,
                error = DomainError.CodeInvalid,
                reset = EmailResetUiState.Pending(resetInSeconds = RESET_PENDING_SECONDS),
            ),
        )

    val premiumRequired = state(AuthStepUiState.PremiumRequired("support@example.org"))

    /** Every state above by name, for the screenshot tests. */
    val all: Map<String, AuthUiState> =
        linkedMapOf(
            "loading" to loading,
            "loggingOut" to loggingOut,
            "welcome" to welcome,
            "phoneNumber" to phoneNumber,
            "phoneNumberEmpty" to phoneNumberEmpty,
            "phoneNumberError" to phoneNumberError,
            "phoneNumberFloodWait" to phoneNumberFloodWait,
            "phoneNumberSubmittingOffline" to phoneNumberSubmittingOffline,
            "countryPicker" to countryPicker,
            "qrCode" to qrCode,
            "code" to code,
            "codeResend" to codeResend,
            "codeError" to codeError,
            "codeFragment" to codeFragment,
            "codePhrase" to codePhrase,
            "password" to password,
            "passwordError" to passwordError,
            "passwordNoRecoveryEmail" to passwordNoRecoveryEmail,
            "passwordRecoveryCode" to passwordRecoveryCode,
            "newPassword" to newPassword,
            "registration" to registration,
            "registrationError" to registrationError,
            "registrationTerms" to registrationTerms,
            "emailAddress" to emailAddress,
            "emailCode" to emailCode,
            "emailCodeNewAddress" to emailCodeNewAddress,
            "emailCodeResetPending" to emailCodeResetPending,
            "premiumRequired" to premiumRequired,
        )
}

/** A phone in landscape, for `@Preview(device = …)`: medium width, compact height. */
internal const val LANDSCAPE_PHONE = "spec:width=891dp,height=411dp,orientation=landscape"

/** An [AuthScreen] in the preview theme. */
@Composable
internal fun AuthScreenPreview(state: AuthUiState) {
    PingogramPreview {
        AuthScreen(state = state, onEvent = {})
    }
}
