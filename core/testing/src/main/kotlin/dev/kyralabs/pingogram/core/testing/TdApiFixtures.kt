package dev.kyralabs.pingogram.core.testing

import org.drinkless.tdlib.TdApi

/**
 * Builders for TdApi objects in tests (`docs/TESTING.md`), with harmless defaults: test-DC phone numbers
 * (`+99966…`) and `example.org` addresses. TdApi's `toString()` is native, so never print these in a JVM test.
 */
object TdApiFixtures {
    const val PHONE_NUMBER = "+99966 2 1234"

    fun waitTdlibParameters() = TdApi.AuthorizationStateWaitTdlibParameters()

    fun waitPhoneNumber() = TdApi.AuthorizationStateWaitPhoneNumber()

    fun waitPremiumPurchase(
        storeProductId: String = "premium.monthly",
        premiumDayCount: Int = 30,
        supportEmailAddress: String = "support@example.org",
        supportEmailSubject: String = "Login",
    ) = TdApi.AuthorizationStateWaitPremiumPurchase(
        storeProductId,
        premiumDayCount,
        supportEmailAddress,
        supportEmailSubject,
    )

    fun waitEmailAddress(
        allowAppleId: Boolean = false,
        allowGoogleId: Boolean = false,
    ) = TdApi.AuthorizationStateWaitEmailAddress(allowAppleId, allowGoogleId)

    fun waitEmailCode(
        emailAddressPattern: String = "a***@example.org",
        codeLength: Int = 6,
        allowAppleId: Boolean = false,
        allowGoogleId: Boolean = false,
        emailAddressResetState: TdApi.EmailAddressResetState? = null,
    ) = TdApi.AuthorizationStateWaitEmailCode(
        allowAppleId,
        allowGoogleId,
        TdApi.EmailAddressAuthenticationCodeInfo(emailAddressPattern, codeLength),
        emailAddressResetState,
    )

    fun waitCode(codeInfo: TdApi.AuthenticationCodeInfo = authenticationCodeInfo()) =
        TdApi.AuthorizationStateWaitCode(codeInfo)

    fun authenticationCodeInfo(
        phoneNumber: String = PHONE_NUMBER,
        type: TdApi.AuthenticationCodeType = TdApi.AuthenticationCodeTypeSms(5),
        nextType: TdApi.AuthenticationCodeType? = null,
        timeoutSeconds: Int = 60,
    ) = TdApi.AuthenticationCodeInfo(phoneNumber, type, nextType, timeoutSeconds)

    fun waitOtherDeviceConfirmation(link: String = "tg://login?token=dGVzdA") =
        TdApi.AuthorizationStateWaitOtherDeviceConfirmation(link)

    fun waitRegistration(
        termsOfService: TdApi.TermsOfService = TdApi.TermsOfService(formattedText("Terms of Service"), 0, true),
    ) = TdApi.AuthorizationStateWaitRegistration(termsOfService)

    fun waitPassword(
        passwordHint: String = "",
        hasRecoveryEmailAddress: Boolean = false,
        hasPassportData: Boolean = false,
        recoveryEmailAddressPattern: String = "",
    ) = TdApi.AuthorizationStateWaitPassword(
        passwordHint,
        hasRecoveryEmailAddress,
        hasPassportData,
        recoveryEmailAddressPattern,
    )

    fun ready() = TdApi.AuthorizationStateReady()

    fun loggingOut() = TdApi.AuthorizationStateLoggingOut()

    fun closing() = TdApi.AuthorizationStateClosing()

    fun closed() = TdApi.AuthorizationStateClosed()

    /** One instance of every `AuthorizationState` of the pinned TDLib version, in td_api.tl order. */
    fun allAuthorizationStates(): List<TdApi.AuthorizationState> =
        listOf(
            waitTdlibParameters(),
            waitPhoneNumber(),
            waitPremiumPurchase(),
            waitEmailAddress(),
            waitEmailCode(),
            waitCode(),
            waitOtherDeviceConfirmation(),
            waitRegistration(),
            waitPassword(),
            ready(),
            loggingOut(),
            closing(),
            closed(),
        )

    /** One instance of every `ConnectionState`, in td_api.tl order. */
    fun allConnectionStates(): List<TdApi.ConnectionState> =
        listOf(
            TdApi.ConnectionStateWaitingForNetwork(),
            TdApi.ConnectionStateConnectingToProxy(),
            TdApi.ConnectionStateConnecting(),
            TdApi.ConnectionStateUpdating(),
            TdApi.ConnectionStateReady(),
        )

    fun authorizationStateUpdate(state: TdApi.AuthorizationState) = TdApi.UpdateAuthorizationState(state)

    fun connectionStateUpdate(state: TdApi.ConnectionState) = TdApi.UpdateConnectionState(state)

    fun formattedText(text: String) = TdApi.FormattedText(text, emptyArray())

    fun error(
        code: Int = 400,
        message: String = "BAD_REQUEST",
    ) = TdApi.Error(code, message)

    /** What TDLib returns for a flood wait: code 429 with the seconds in the message. */
    fun floodWait(seconds: Int) = TdApi.Error(429, "Too Many Requests: retry after $seconds")
}
