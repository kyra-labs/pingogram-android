package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ActiveSession
import dev.kyralabs.pingogram.core.domain.model.ActiveSessions
import dev.kyralabs.pingogram.core.domain.model.SessionDevice
import dev.kyralabs.pingogram.core.domain.model.TwoStepVerificationState
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

/** In TDLib's order, which td_api.tl asks apps to keep. */
internal fun TdApi.Sessions.toActiveSessions() =
    ActiveSessions(
        sessions = sessions.map { it.toActiveSession() }.toImmutableList(),
        inactiveSessionTtlDays = inactiveSessionTtlDays,
    )

internal fun TdApi.Session.toActiveSession() =
    ActiveSession(
        id = id,
        isCurrent = isCurrent,
        isPasswordPending = isPasswordPending,
        isUnconfirmed = isUnconfirmed,
        device = deviceType.toSessionDevice(),
        applicationName = applicationName,
        applicationVersion = applicationVersion,
        isOfficialApplication = isOfficialApplication,
        deviceModel = deviceModel,
        platform = platform,
        systemVersion = systemVersion,
        logInDate = logInDate.toLong(),
        lastActiveDate = lastActiveDate.toLong(),
        location = location,
    )

/** A generic Apple device could be any of them, so it is [SessionDevice.Unknown]; so are types of a newer TDLib. */
private fun TdApi.SessionDeviceType?.toSessionDevice(): SessionDevice =
    when (this) {
        is TdApi.SessionDeviceTypeAndroid, is TdApi.SessionDeviceTypeIphone -> {
            SessionDevice.Phone
        }

        is TdApi.SessionDeviceTypeIpad -> {
            SessionDevice.Tablet
        }

        is TdApi.SessionDeviceTypeWindows, is TdApi.SessionDeviceTypeMac, is TdApi.SessionDeviceTypeLinux,
        is TdApi.SessionDeviceTypeUbuntu,
        -> {
            SessionDevice.Desktop
        }

        is TdApi.SessionDeviceTypeChrome, is TdApi.SessionDeviceTypeFirefox, is TdApi.SessionDeviceTypeEdge,
        is TdApi.SessionDeviceTypeSafari, is TdApi.SessionDeviceTypeOpera, is TdApi.SessionDeviceTypeBrave,
        is TdApi.SessionDeviceTypeVivaldi,
        -> {
            SessionDevice.Browser
        }

        is TdApi.SessionDeviceTypeXbox -> {
            SessionDevice.GameConsole
        }

        else -> {
            SessionDevice.Unknown
        }
    }

/**
 * The recovery email address of [TdApi.PasswordState.recoveryEmailAddressCodeInfo] is one waiting for its confirmation
 * code (td_api.tl `passwordState`); a confirmed one is only [TdApi.PasswordState.hasRecoveryEmailAddress].
 */
internal fun TdApi.PasswordState.toTwoStepVerificationState() =
    TwoStepVerificationState(
        hasPassword = hasPassword,
        hasRecoveryEmail = hasRecoveryEmailAddress,
        pendingRecoveryEmailPattern = recoveryEmailAddressCodeInfo?.emailAddressPattern?.takeIf { it.isNotBlank() },
        pendingResetDate = pendingResetDate.takeIf { it > 0 }?.toLong(),
        passwordHint = passwordHint.orEmpty(),
        loginEmailPattern = loginEmailAddressPattern?.takeIf { it.isNotBlank() },
    )
