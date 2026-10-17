package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.AuthCodeType
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.Country
import dev.kyralabs.pingogram.core.domain.model.EmailAddressResetState
import dev.kyralabs.pingogram.core.domain.model.PhoneNumberInfo
import dev.kyralabs.pingogram.core.domain.model.TermsOfService
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import org.drinkless.tdlib.TdApi
import dev.kyralabs.pingogram.core.domain.model.flagEmoji as flagEmojiOf

internal fun AuthState.toAuthStep(): AuthStep =
    when (this) {
        AuthState.Initializing, AuthState.WaitTdlibParameters -> {
            AuthStep.Initializing
        }

        AuthState.WaitPhoneNumber -> {
            AuthStep.WaitPhoneNumber
        }

        is AuthState.WaitCode -> {
            AuthStep.WaitCode(
                phoneNumber = codeInfo.phoneNumber,
                type = codeInfo.type.toAuthCodeType(),
                nextType = codeInfo.nextType?.toAuthCodeType(),
                resendTimeoutSeconds = codeInfo.timeout.coerceAtLeast(0),
            )
        }

        is AuthState.WaitOtherDeviceConfirmation -> {
            AuthStep.WaitOtherDeviceConfirmation(link)
        }

        is AuthState.WaitPassword -> {
            AuthStep.WaitPassword(
                hint = passwordHint,
                hasRecoveryEmailAddress = hasRecoveryEmailAddress,
                recoveryEmailAddressPattern = recoveryEmailAddressPattern,
            )
        }

        is AuthState.WaitRegistration -> {
            AuthStep.WaitRegistration(termsOfService.toTermsOfService())
        }

        // Apple ID and Google ID sign-in would need their SDKs; Pingogram offers the email code only.
        is AuthState.WaitEmailAddress -> {
            AuthStep.WaitEmailAddress
        }

        is AuthState.WaitEmailCode -> {
            AuthStep.WaitEmailCode(
                emailAddressPattern = codeInfo.emailAddressPattern,
                codeLength = codeInfo.length,
                resetState = emailAddressResetState?.toEmailAddressResetState(),
            )
        }

        is AuthState.WaitPremiumPurchase -> {
            AuthStep.WaitPremiumPurchase(supportEmailAddress, supportEmailSubject)
        }

        AuthState.Ready -> {
            AuthStep.Ready
        }

        AuthState.LoggingOut, AuthState.Closing -> {
            AuthStep.LoggingOut
        }

        AuthState.Closed -> {
            AuthStep.Closed
        }
    }

internal fun TdApi.AuthenticationCodeType.toAuthCodeType(): AuthCodeType =
    when (this) {
        is TdApi.AuthenticationCodeTypeTelegramMessage -> AuthCodeType.TelegramMessage(length)

        is TdApi.AuthenticationCodeTypeSms -> AuthCodeType.Sms(length)

        is TdApi.AuthenticationCodeTypeSmsWord -> AuthCodeType.SmsWord(firstLetter.orEmpty())

        is TdApi.AuthenticationCodeTypeSmsPhrase -> AuthCodeType.SmsPhrase(firstWord.orEmpty())

        is TdApi.AuthenticationCodeTypeCall -> AuthCodeType.Call(length)

        is TdApi.AuthenticationCodeTypeFlashCall -> AuthCodeType.FlashCall(pattern.orEmpty())

        is TdApi.AuthenticationCodeTypeMissedCall -> AuthCodeType.MissedCall(phoneNumberPrefix.orEmpty(), length)

        is TdApi.AuthenticationCodeTypeFragment -> AuthCodeType.Fragment(url.orEmpty(), length)

        is TdApi.AuthenticationCodeTypeFirebaseAndroid -> AuthCodeType.Firebase(length)

        is TdApi.AuthenticationCodeTypeFirebaseIos -> AuthCodeType.Firebase(length)

        // A type added by a newer TDLib: a digit code of unknown length is the closest match.
        else -> AuthCodeType.Sms(length = 0)
    }

internal fun TdApi.TermsOfService.toTermsOfService() =
    TermsOfService(
        text = text?.text.orEmpty(),
        minUserAge = minUserAge,
        mustShowPopup = showPopup,
    )

internal fun TdApi.EmailAddressResetState.toEmailAddressResetState(): EmailAddressResetState? =
    when (this) {
        is TdApi.EmailAddressResetStateAvailable -> EmailAddressResetState.Available(waitPeriod.coerceAtLeast(0))
        is TdApi.EmailAddressResetStatePending -> EmailAddressResetState.Pending(resetIn.coerceAtLeast(0))
        else -> null
    }

internal fun TdApi.CountryInfo.toCountry() =
    Country(
        isoCode = countryCode,
        name = name.orEmpty().ifBlank { englishName.orEmpty() },
        englishName = englishName.orEmpty(),
        flagEmoji = flagEmoji.orEmpty().ifBlank { flagEmojiOf(countryCode) },
        callingCodes = callingCodes.orEmpty().toList(),
        isHidden = isHidden,
    )

internal fun TdApi.PhoneNumberInfo.toPhoneNumberInfo() =
    PhoneNumberInfo(
        country = country?.toCountry(),
        callingCode = countryCallingCode.orEmpty(),
        formattedNumber = formattedPhoneNumber.orEmpty(),
        isAnonymous = isAnonymous,
    )

internal fun ConnectionState.toConnectionStatus(): ConnectionStatus =
    when (this) {
        ConnectionState.WaitingForNetwork -> ConnectionStatus.WaitingForNetwork
        ConnectionState.ConnectingToProxy -> ConnectionStatus.ConnectingToProxy
        ConnectionState.Connecting -> ConnectionStatus.Connecting
        ConnectionState.Updating -> ConnectionStatus.Updating
        ConnectionState.Ready -> ConnectionStatus.Ready
    }
