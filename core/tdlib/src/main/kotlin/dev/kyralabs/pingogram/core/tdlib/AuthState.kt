package dev.kyralabs.pingogram.core.tdlib

import org.drinkless.tdlib.TdApi

/**
 * Typed mirror of TDLib's `AuthorizationState` at the pinned TDLib version (`docs/TDLIB.md`, Authorization state
 * machine), plus [Initializing] until TDLib reports its first state. Some states carry TdApi payloads; `core:data`
 * maps them to domain models, so feature code never sees them.
 *
 * `toString()` leaves payloads out: they hold phone numbers, login tokens, password hints and email patterns that
 * must not reach logs, and TdApi's own `toString()` is native (it throws in JVM tests).
 */
sealed interface AuthState {
    /** The client is starting; TDLib has not reported a state yet. */
    data object Initializing : AuthState

    /** TDLib needs `SetTdlibParameters`; [TdlibClient] sends them by itself. */
    data object WaitTdlibParameters : AuthState

    data object WaitPhoneNumber : AuthState

    /** The account must buy Telegram Premium in the store before it can log in (not offered by Pingogram). */
    data class WaitPremiumPurchase(
        val storeProductId: String,
        val premiumDayCount: Int,
        val supportEmailAddress: String,
        val supportEmailSubject: String,
    ) : AuthState

    data class WaitEmailAddress(
        val allowAppleId: Boolean,
        val allowGoogleId: Boolean,
    ) : AuthState

    data class WaitEmailCode(
        val allowAppleId: Boolean,
        val allowGoogleId: Boolean,
        val codeInfo: TdApi.EmailAddressAuthenticationCodeInfo,
        val emailAddressResetState: TdApi.EmailAddressResetState?,
    ) : AuthState {
        override fun toString() = "WaitEmailCode(allowAppleId=$allowAppleId, allowGoogleId=$allowGoogleId, …)"
    }

    data class WaitCode(
        val codeInfo: TdApi.AuthenticationCodeInfo,
    ) : AuthState {
        override fun toString() = "WaitCode(…)"
    }

    /** QR code login: [link] is shown as a QR code for another logged-in device to confirm. */
    data class WaitOtherDeviceConfirmation(
        val link: String,
    ) : AuthState {
        override fun toString() = "WaitOtherDeviceConfirmation(…)"
    }

    data class WaitRegistration(
        val termsOfService: TdApi.TermsOfService,
    ) : AuthState {
        override fun toString() = "WaitRegistration(…)"
    }

    data class WaitPassword(
        val passwordHint: String,
        val hasRecoveryEmailAddress: Boolean,
        val hasPassportData: Boolean,
        val recoveryEmailAddressPattern: String,
    ) : AuthState {
        override fun toString() =
            "WaitPassword(hasRecoveryEmailAddress=$hasRecoveryEmailAddress, hasPassportData=$hasPassportData, …)"
    }

    data object Ready : AuthState

    data object LoggingOut : AuthState

    data object Closing : AuthState

    /**
     * TDLib closed this instance (after logging out, or on a fatal error). [TdlibClient] starts a new one right away,
     * which reports [WaitTdlibParameters] next; every in-memory cache must be dropped.
     */
    data object Closed : AuthState

    companion object {
        /** Null only for a state this TDLib version does not define (a TDLib bump must add it here). */
        fun from(state: TdApi.AuthorizationState): AuthState? =
            when (state) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    WaitTdlibParameters
                }

                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                    WaitPhoneNumber
                }

                is TdApi.AuthorizationStateWaitPremiumPurchase -> {
                    WaitPremiumPurchase(
                        storeProductId = state.storeProductId,
                        premiumDayCount = state.premiumDayCount,
                        supportEmailAddress = state.supportEmailAddress,
                        supportEmailSubject = state.supportEmailSubject,
                    )
                }

                is TdApi.AuthorizationStateWaitEmailAddress -> {
                    WaitEmailAddress(allowAppleId = state.allowAppleId, allowGoogleId = state.allowGoogleId)
                }

                is TdApi.AuthorizationStateWaitEmailCode -> {
                    WaitEmailCode(
                        allowAppleId = state.allowAppleId,
                        allowGoogleId = state.allowGoogleId,
                        codeInfo = state.codeInfo,
                        emailAddressResetState = state.emailAddressResetState,
                    )
                }

                is TdApi.AuthorizationStateWaitCode -> {
                    WaitCode(state.codeInfo)
                }

                is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                    WaitOtherDeviceConfirmation(state.link)
                }

                is TdApi.AuthorizationStateWaitRegistration -> {
                    WaitRegistration(state.termsOfService)
                }

                is TdApi.AuthorizationStateWaitPassword -> {
                    WaitPassword(
                        passwordHint = state.passwordHint,
                        hasRecoveryEmailAddress = state.hasRecoveryEmailAddress,
                        hasPassportData = state.hasPassportData,
                        recoveryEmailAddressPattern = state.recoveryEmailAddressPattern,
                    )
                }

                is TdApi.AuthorizationStateReady -> {
                    Ready
                }

                is TdApi.AuthorizationStateLoggingOut -> {
                    LoggingOut
                }

                is TdApi.AuthorizationStateClosing -> {
                    Closing
                }

                is TdApi.AuthorizationStateClosed -> {
                    Closed
                }

                else -> {
                    null
                }
            }
    }
}
