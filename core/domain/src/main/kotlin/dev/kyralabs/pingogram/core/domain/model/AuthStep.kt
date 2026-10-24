package dev.kyralabs.pingogram.core.domain.model

import kotlin.time.ComparableTimeMark

/**
 * Where the login is, as TDLib reports it (`docs/TDLIB.md`, Authorization state machine): one step per screen of
 * `feature:auth`. Plain data, mapped from TDLib's authorization states by `core:data`.
 *
 * `toString()` of the steps with personal data (phone numbers, login links, password hints, email patterns) leaves
 * that data out, so a step can be logged.
 */
sealed interface AuthStep {
    /** TDLib is starting, or opening its database; nothing to show yet. */
    data object Initializing : AuthStep

    data object WaitPhoneNumber : AuthStep

    /**
     * A login code was sent. [nextType] is how a new code would be sent, if there is another way; it can be
     * requested once [resendAvailableAt] has passed (`resendAvailableAt.hasPassedNow()`). The mark is fixed when the
     * step is first seen, so a screen created later counts down from the same moment.
     */
    data class WaitCode(
        val phoneNumber: String,
        val type: AuthCodeType,
        val nextType: AuthCodeType?,
        val resendAvailableAt: ComparableTimeMark,
    ) : AuthStep {
        override fun toString() = "WaitCode(type=$type, nextType=$nextType)"
    }

    /** QR code login: another logged-in device scans [link]. TDLib replaces the link every few seconds. */
    data class WaitOtherDeviceConfirmation(
        val link: String,
    ) : AuthStep {
        override fun toString() = "WaitOtherDeviceConfirmation(…)"
    }

    /**
     * The account has a 2-step verification password. [recoveryEmailAddressPattern] (e.g. `a***@example.org`) is
     * empty until a recovery code was requested.
     */
    data class WaitPassword(
        val hint: String,
        val hasRecoveryEmailAddress: Boolean,
        val recoveryEmailAddressPattern: String,
    ) : AuthStep {
        override fun toString() = "WaitPassword(hasRecoveryEmailAddress=$hasRecoveryEmailAddress, …)"
    }

    /** A new account: the user enters a name and accepts [termsOfService]. */
    data class WaitRegistration(
        val termsOfService: TermsOfService,
    ) : AuthStep

    /** Telegram requires an email address for login on this account. */
    data object WaitEmailAddress : AuthStep

    data class WaitEmailCode(
        val emailAddressPattern: String,
        val codeLength: Int,
        /** Null if the email address cannot be reset. */
        val resetState: EmailAddressResetState?,
    ) : AuthStep {
        override fun toString() = "WaitEmailCode(codeLength=$codeLength, resetState=$resetState, …)"
    }

    /**
     * Telegram allows this account to log in only after buying Telegram Premium in an app store. Pingogram offers no
     * purchases (`REQUIREMENTS.md` §1.2), so it explains this and offers the support address.
     */
    data class WaitPremiumPurchase(
        val supportEmailAddress: String,
        val supportEmailSubject: String,
    ) : AuthStep

    data object Ready : AuthStep

    data object LoggingOut : AuthStep

    /** TDLib closed (after logging out, or a fatal error) and starts over; every local state must be dropped. */
    data object Closed : AuthStep
}

/** How a login code is delivered (TDLib's `AuthenticationCodeType`). */
sealed interface AuthCodeType {
    /** A message to the account's Telegram apps on other devices. */
    data class TelegramMessage(
        val length: Int,
    ) : AuthCodeType

    data class Sms(
        val length: Int,
    ) : AuthCodeType

    /** The code is a word sent by SMS. */
    data class SmsWord(
        val firstLetter: String,
    ) : AuthCodeType

    /** The code is a phrase of several words sent by SMS. */
    data class SmsPhrase(
        val firstWord: String,
    ) : AuthCodeType

    /** A phone call reads out the code. */
    data class Call(
        val length: Int,
    ) : AuthCodeType

    /** The code is the whole number of an incoming call matching [pattern]. Pingogram never asks for it. */
    data class FlashCall(
        val pattern: String,
    ) : AuthCodeType

    /** The code is the last [length] digits of a missed call from a number starting with [phoneNumberPrefix]. */
    data class MissedCall(
        val phoneNumberPrefix: String,
        val length: Int,
    ) : AuthCodeType

    /** The code is shown on fragment.com, for numbers bought there. */
    data class Fragment(
        val url: String,
        val length: Int,
    ) : AuthCodeType {
        // The URL can contain the phone number.
        override fun toString() = "Fragment(length=$length, …)"
    }

    /** Delivered through Firebase to the official apps only; Pingogram never asks for it. */
    data class Firebase(
        val length: Int,
    ) : AuthCodeType
}

/** How many characters the code has, when that is known; words and phrases have no fixed length. */
val AuthCodeType.codeLength: Int?
    get() =
        when (this) {
            is AuthCodeType.TelegramMessage -> length
            is AuthCodeType.Sms -> length
            is AuthCodeType.Call -> length
            is AuthCodeType.MissedCall -> length
            is AuthCodeType.Fragment -> length
            is AuthCodeType.Firebase -> length
            is AuthCodeType.SmsWord, is AuthCodeType.SmsPhrase, is AuthCodeType.FlashCall -> null
        }?.takeIf { it > 0 }

/** Whether the code consists of digits only. */
val AuthCodeType.isNumeric: Boolean
    get() = this !is AuthCodeType.SmsWord && this !is AuthCodeType.SmsPhrase

/** Telegram's terms of service, accepted when registering a new account. */
data class TermsOfService(
    val text: String,
    /** The minimum age to accept the terms; 0 if there is none. */
    val minUserAge: Int,
    /** Whether the terms must be shown, and accepted, before the user can continue. */
    val mustShowPopup: Boolean,
)

/** Whether the login email address can be reset, so that the login continues with a code sent to the phone. */
sealed interface EmailAddressResetState {
    /** A reset can be requested; it happens after [waitPeriodSeconds] (0 for Telegram Premium users). */
    data class Available(
        val waitPeriodSeconds: Int,
    ) : EmailAddressResetState

    /** A reset was requested and happens at [resetAt], fixed when TDLib's state was first seen. */
    data class Pending(
        val resetAt: ComparableTimeMark,
    ) : EmailAddressResetState
}

/**
 * Where Telegram sent a code outside of the login (changing the phone number): to [phoneNumber] (formatted by
 * Telegram), as [type]; [nextType] is how a new one can be asked for, after [timeoutSeconds] (0: at once), or null when
 * there is none.
 */
data class LoginCodeInfo(
    val phoneNumber: String,
    val type: AuthCodeType,
    val nextType: AuthCodeType?,
    val timeoutSeconds: Int,
) {
    override fun toString() = "LoginCodeInfo(type=$type, nextType=$nextType, timeoutSeconds=$timeoutSeconds)"
}
